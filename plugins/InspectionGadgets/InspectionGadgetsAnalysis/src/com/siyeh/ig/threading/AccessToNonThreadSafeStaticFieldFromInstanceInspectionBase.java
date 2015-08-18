/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.siyeh.ig.threading;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.ui.ExternalizableStringSet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AccessToNonThreadSafeStaticFieldFromInstanceInspectionBase extends BaseInspection {

  public AccessToNonThreadSafeStaticFieldFromInstanceInspectionBase() {
    if (!nonThreadSafeTypes.isEmpty()) {
      nonThreadSafeClasses.clear();
      final List<String> strings = StringUtil.split(nonThreadSafeTypes, ",");
      for (String string : strings) {
        nonThreadSafeClasses.add(string);
      }
      nonThreadSafeTypes = "";
    }
  }

  @SuppressWarnings("PublicField")
  public final ExternalizableStringSet nonThreadSafeClasses =
    new ExternalizableStringSet("java.text.SimpleDateFormat",
                                "java.text.MessageFormat",
                                "java.text.DecimalFormat",
                                "java.text.ChoiceFormat",
                                "java.util.Calendar");

  @NonNls
  @SuppressWarnings({"PublicField"})
  public String nonThreadSafeTypes = "";

  @NotNull
  @Override
  public String getID() {
    return "AccessToNonThreadSafeStaticField";
  }

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("access.to.non.thread.safe.static.field.from.instance.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("access.to.non.thread.safe.static.field.from.instance.field.problem.descriptor", infos[0]);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AccessToNonThreadSafeStaticFieldFromInstanceVisitor();
  }

  class AccessToNonThreadSafeStaticFieldFromInstanceVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      if (expression.getQualifierExpression() != null) {
        return;
      }
      final PsiType type = expression.getType();
      if (!(type instanceof PsiClassType)) {
        return;
      }
      final PsiClassType classType = (PsiClassType)type;
      final String className = classType.rawType().getCanonicalText();
      boolean deepCheck = false;
      if (!nonThreadSafeClasses.contains(className)) {
        if (!TypeUtils.isExpressionTypeAssignableWith(expression, nonThreadSafeClasses)) {
          return;
        }
        deepCheck = true;
      }
      final PsiElement target = expression.resolve();
      if (!(target instanceof PsiField)) {
        return;
      }
      final PsiField field = (PsiField)target;
      if (!field.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }

      final PsiModifierListOwner parent =
        PsiTreeUtil.getParentOfType(expression, PsiField.class, PsiMethod.class, PsiClassInitializer.class);
      if (parent == null) {
        return;
      }
      if (parent instanceof PsiMethod || parent instanceof PsiClassInitializer) {
        if (parent.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
          return;
        }
        final PsiSynchronizedStatement synchronizedStatement = PsiTreeUtil.getParentOfType(expression, PsiSynchronizedStatement.class);
        if (synchronizedStatement != null) {
          return;
        }
      }
      if (parent instanceof PsiField || parent instanceof PsiClassInitializer) {
        if (parent.hasModifierProperty(PsiModifier.STATIC)) {
          return;
        }
      }

      if (deepCheck) {
        final PsiExpression initializer = field.getInitializer();
        if (initializer == null) {
          return;
        }
        final PsiType initializerType = initializer.getType();
        if (!(initializerType instanceof PsiClassType)) {
          return;
        }
        final PsiClassType classType2 = (PsiClassType)initializerType;
        final String className2 = classType2.rawType().getCanonicalText();
        if (!nonThreadSafeClasses.contains(className2)) {
          return;
        }
        registerError(expression, className2);
      }
      else {
        registerError(expression, className);
      }
    }
  }
}
