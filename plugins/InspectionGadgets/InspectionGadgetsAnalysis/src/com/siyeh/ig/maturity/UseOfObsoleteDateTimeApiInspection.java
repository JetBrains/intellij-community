/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ig.maturity;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.LibraryUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Bas Leijdekkers
 */
public class UseOfObsoleteDateTimeApiInspection extends BaseInspection {

  private static final Set<String> dateTimeNames = new HashSet<>(
    Arrays.asList("java.util.Date", "java.util.Calendar", "java.util.GregorianCalendar", "java.util.TimeZone", "java.util.SimpleTimeZone"));

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("use.of.obsolete.date.time.api.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("use.of.obsolete.date.time.api.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ObsoleteDateTimeApiVisitor();
  }

  private static class ObsoleteDateTimeApiVisitor extends BaseInspectionVisitor {

    private Boolean newDateTimeApiPresent = null;

    @Override
    public void visitTypeElement(PsiTypeElement typeElement) {
      if (!isNewDateTimeApiPresent(typeElement)) {
        return;
      }
      super.visitTypeElement(typeElement);
      final PsiType type = typeElement.getType();
      if (!isObsoleteDateTimeType(type)) {
        return;
      }
      final PsiElement parent = typeElement.getParent();
      if (parent instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)parent;
        if (LibraryUtil.isOverrideOfLibraryMethod(method)) {
          return;
        }
      }
      else if (parent instanceof PsiParameter) {
        final PsiParameter parameter = (PsiParameter)parent;
        if (LibraryUtil.isOverrideOfLibraryMethodParameter(parameter)) {
          return;
        }
      }
      registerError(typeElement);
    }

    @Override
    public void visitClass(PsiClass aClass) {
      if (!isNewDateTimeApiPresent(aClass)) {
        return;
      }
      super.visitClass(aClass);
      final PsiReferenceList extendsList = aClass.getExtendsList();
      if (extendsList == null) {
        return;
      }
      for (PsiJavaCodeReferenceElement referenceElement : extendsList.getReferenceElements()) {
        final PsiElement target = referenceElement.resolve();
        if (!(target instanceof PsiClass)) {
          return;
        }
        final PsiClass extendsClass = (PsiClass)target;
        if (isObsoleteDateTimeClass(extendsClass)) {
          registerError(referenceElement);
        }
      }
    }

    private boolean isNewDateTimeApiPresent(PsiElement context) {
      if (newDateTimeApiPresent == null) {
        newDateTimeApiPresent = ClassUtils.findClass("java.time.Instant", context) != null;
      }
      return newDateTimeApiPresent != Boolean.FALSE;
    }

    private static boolean isObsoleteDateTimeType(PsiType type) {
      if (type == null) {
        return false;
      }
      final PsiType deepComponentType = type.getDeepComponentType();
      if (!(deepComponentType instanceof PsiClassType)) {
        return false;
      }
      final PsiClassType classType = (PsiClassType)deepComponentType;
      final PsiClass aClass = classType.resolve();
      return isObsoleteDateTimeClass(aClass);
    }

    private static boolean isObsoleteDateTimeClass(PsiClass aClass) {
      if (aClass == null) {
        return false;
      }
      return dateTimeNames.contains(aClass.getQualifiedName());
    }
  }
}
