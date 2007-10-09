/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
import com.intellij.execution.stacktrace.StackTraceLine;
import com.intellij.execution.junit2.Printer;
import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.segments.ObjectReader;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diff.LineTokenizer;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;

import java.util.Iterator;

public class FaultyState extends ReadableState {
  private String myMessage;
  private String myStackTrace;

  public void initializeFrom(final ObjectReader reader) {
    myMessage = reader.readLimitedString();
    myStackTrace = reader.readLimitedString();
  }

  public void printOn(final Printer printer) {
    printer.print(TestProxy.NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
    printer.mark();
    printExceptionHeader(printer, myMessage);
    printer.print(myStackTrace + TestProxy.NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
  }

  protected void printExceptionHeader(final Printer printer, final String message) {
    printer.print(message, ConsoleViewContentType.ERROR_OUTPUT);
  }

  public boolean isDefect() {
    return true;
  }

  public Navigatable getDescriptor(final Location<?> location) {
    if (location == null) return super.getDescriptor(location);
    final String[] stackTrace = new LineTokenizer(myStackTrace).execute();
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
