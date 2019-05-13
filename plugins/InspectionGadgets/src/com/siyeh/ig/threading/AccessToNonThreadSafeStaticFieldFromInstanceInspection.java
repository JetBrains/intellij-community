// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.threading;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.ui.ExternalizableStringSet;
import com.siyeh.ig.ui.UiUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AccessToNonThreadSafeStaticFieldFromInstanceInspection extends
                                                                    BaseInspection {

  @SuppressWarnings("PublicField")
  public final ExternalizableStringSet nonThreadSafeClasses =
    new ExternalizableStringSet("java.text.SimpleDateFormat",
                                "java.text.MessageFormat",
                                "java.text.DecimalFormat",
                                "java.text.ChoiceFormat",
                                "java.util.Calendar");
  /**
   * Don't remove, otherwise user inspection profiles will be modified.
   */
  @NonNls
  @SuppressWarnings({"PublicField", "unused"})
  public String nonThreadSafeTypes = "";

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return UiUtils.createTreeClassChooserList(
      nonThreadSafeClasses, InspectionGadgetsBundle.message("access.to.non.thread.safe.static.field.from.instance.option.title"),
      InspectionGadgetsBundle.message("access.to.non.thread.safe.static.field.from.instance.class.chooser.title"));
  }

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