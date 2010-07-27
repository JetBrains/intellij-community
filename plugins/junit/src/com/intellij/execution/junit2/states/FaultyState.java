/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.execution.junit2.states;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.junit2.segments.ObjectReader;
import com.intellij.execution.stacktrace.StackTraceLine;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.CompositePrintable;
import com.intellij.execution.testframework.Printer;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diff.LineTokenizer;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class FaultyState extends ReadableState {
  private List<String> myMessages;
  private List<String> myStackTraces;

  public void initializeFrom(final ObjectReader reader) {
    myMessages = Collections.singletonList(reader.readLimitedString());
    myStackTraces = Collections.singletonList(reader.readLimitedString());
  }

  public void printOn(final Printer printer) {
    printer.print(CompositePrintable.NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
    printer.mark();
    for (int i = 0; i < myMessages.size(); i++) {
      printExceptionHeader(printer, myMessages.get(i));
      printer.print(myStackTraces.get(i) + CompositePrintable.NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
    }
  }

  protected void printExceptionHeader(final Printer printer, final String message) {
    printer.print(message, ConsoleViewContentType.ERROR_OUTPUT);
  }

  public boolean isDefect() {
    return true;
  }

  @Override
  public void merge(@NotNull TestState state) {
    if (state instanceof FaultyState) {
      myMessages = new ArrayList<String>(myMessages);
      myMessages.addAll(0, ((FaultyState)state).myMessages);

      myStackTraces = new ArrayList<String>(myStackTraces);
      myStackTraces.addAll(0, ((FaultyState)state).myStackTraces);
    }
  }

  public Navigatable getDescriptor(final Location<?> location) {
    if (location == null) return super.getDescriptor(location);
    //navigate to the first stack trace
    final String[] stackTrace = new LineTokenizer(myStackTraces.get(0)).execute();
    final PsiLocation<?> psiLocation = location.toPsiLocation();
    final PsiClass containingClass = psiLocation.getParentElement(PsiClass.class);
    if (containingClass == null) return super.getDescriptor(location);
    String containingMethod = null;
    for (Iterator<Location<PsiMethod>> iterator = psiLocation.getAncestors(PsiMethod.class, false); iterator.hasNext();) {
      final PsiMethod psiMethod = iterator.next().getPsiElement();
      if (containingClass.equals(psiMethod.getContainingClass())) containingMethod = psiMethod.getName();
    }
    if (containingMethod == null) return super.getDescriptor(location);
    final String qualifiedName = containingClass.getQualifiedName();
    StackTraceLine lastLine = null;
    for (String aStackTrace : stackTrace) {
      final StackTraceLine line = new StackTraceLine(containingClass.getProject(), aStackTrace);
      if (containingMethod.equals(line.getMethodName()) && qualifiedName.equals(line.getClassName())) {
        lastLine = line;
        break;
      }
    }
    return lastLine != null ?
        lastLine.getOpenFileDescriptor(containingClass.getContainingFile().getVirtualFile()) :
        super.getDescriptor(location);
  }
}
