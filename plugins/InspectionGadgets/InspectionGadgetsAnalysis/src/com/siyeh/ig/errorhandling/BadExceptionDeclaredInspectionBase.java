/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.errorhandling;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.ig.ui.ExternalizableStringSet;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class BadExceptionDeclaredInspectionBase extends BaseInspection {

  @SuppressWarnings("PublicField")
  public String exceptionsString = "";

  @SuppressWarnings("PublicField")
  public final ExternalizableStringSet exceptions =
    new ExternalizableStringSet(
      "java.lang.Throwable",
      "java.lang.Exception",
      "java.lang.Error",
      "java.lang.RuntimeException",
      "java.lang.NullPointerException",
      "java.lang.ClassCastException",
      "java.lang.ArrayIndexOutOfBoundsException"
    );

  @SuppressWarnings("PublicField")
  public boolean ignoreTestCases = false; // keep for compatibility

  @SuppressWarnings("PublicField")
  public boolean ignoreLibraryOverrides = false;

  public BadExceptionDeclaredInspectionBase() {
    if (!exceptionsString.isEmpty()) {
      exceptions.clear();
      final List<String> strings = StringUtil.split(exceptionsString, ",");
      for (String string : strings) {
        exceptions.add(string);
      }
      exceptionsString = "";
    }
  }

  @Override
  @NotNull
  public String getID() {
    return "ProhibitedExceptionDeclared";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("bad.exception.declared.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("bad.exception.declared.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BadExceptionDeclaredVisitor();
  }

  private class BadExceptionDeclaredVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      if (ignoreLibraryOverrides && LibraryUtil.isOverrideOfLibraryMethod(method)) {
        return;
      }
      final PsiReferenceList throwsList = method.getThrowsList();
      final PsiJavaCodeReferenceElement[] references = throwsList.getReferenceElements();
      for (PsiJavaCodeReferenceElement reference : references) {
        final PsiElement element = reference.resolve();
        if (!(element instanceof PsiClass)) {
          continue;
        }
        final PsiClass thrownClass = (PsiClass)element;
        final String qualifiedName = thrownClass.getQualifiedName();
        if (qualifiedName != null && exceptions.contains(qualifiedName)) {
          registerError(reference, reference);
        }
      }
    }
  }
}
