/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ig.bugs;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class EqualsOnSuspiciousObjectInspection extends BaseInspection {
  private final List<String> myClasses =
    Arrays.asList(CommonClassNames.JAVA_LANG_STRING_BUILDER, CommonClassNames.JAVA_LANG_STRING_BUFFER);

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    String typeName = (String)infos[0];
    return InspectionGadgetsBundle.message("equals.called.on.suspicious.object.problem.descriptor", StringUtil.getShortName(typeName));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BaseEqualsVisitor() {
      @Override
      boolean checkTypes(@NotNull PsiReferenceExpression expression, @NotNull PsiType type1, @NotNull PsiType type2) {
        if (checkType(expression, type1)) return true;
        if (checkType(expression, type2)) return true;
        return false;
      }

      private boolean checkType(PsiReferenceExpression expression, PsiType type) {
        PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(type);
        if (psiClass != null) {
          String qualifiedName = psiClass.getQualifiedName();
          if (myClasses.contains(qualifiedName)) {
            PsiElement name = expression.getReferenceNameElement();
            registerError(name == null ? expression : name, qualifiedName);
            return true;
          }
        }
        return false;
      }
    };
  }
}
