// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.junit;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class JUnitRuleInspection extends BaseInspection {
  public static final String RULE_FQN = "org.junit.Rule";
  public static final String CLASS_RULE_FQN = "org.junit.ClassRule";
  public boolean REPORT_RULE_PROBLEMS = true;
  public boolean REPORT_CLASS_RULE_PROBLEMS = true;

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("junit.rule.display.name");
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox("Report @Rule problems", "REPORT_RULE_PROBLEMS");
    panel.addCheckbox("Report @ClassRule problems", "REPORT_CLASS_RULE_PROBLEMS");
    return panel;
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return (String)infos[0];
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return infos.length > 1 ? new MakePublicStaticFix((String)infos[1], infos[2].equals(CLASS_RULE_FQN)) : null;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitField(PsiField field) {
        final boolean ruleAnnotated = REPORT_RULE_PROBLEMS && AnnotationUtil.isAnnotated(field, RULE_FQN, 0);
        final boolean classRuleAnnotated = REPORT_CLASS_RULE_PROBLEMS && AnnotationUtil.isAnnotated(field, CLASS_RULE_FQN, 0);
        if (ruleAnnotated || classRuleAnnotated) {
          String annotation = ruleAnnotated ? RULE_FQN : CLASS_RULE_FQN;
          String errorMessage = getPublicStaticErrorMessage(field, ruleAnnotated, classRuleAnnotated);
          if (errorMessage != null) {
            registerError(field.getNameIdentifier(), InspectionGadgetsBundle.message("junit.rule.problem.descriptor", annotation, errorMessage), "Make field " + errorMessage, annotation);
          }
          final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(field.getType());
          if (!InheritanceUtil.isInheritor(aClass, false, "org.junit.rules.TestRule") &&
              !InheritanceUtil.isInheritor(aClass, false, "org.junit.rules.MethodRule")) {
            registerError(field.getNameIdentifier(), InspectionGadgetsBundle.message("junit.rule.type.problem.descriptor"));
          }
        }
      }
    };
  }

  static String getPublicStaticErrorMessage(PsiModifierListOwner field, boolean shouldBeNonStatic, boolean shouldBeStatic) {
    String errorMessage = null;
    final boolean hasStatic = field.hasModifierProperty(PsiModifier.STATIC);
    final boolean hasPublic = field.hasModifierProperty(PsiModifier.PUBLIC);
    if (!hasPublic) {
      if (shouldBeStatic) {
        if (!hasStatic) {
          errorMessage = "'public' and 'static'";
        } else {
          errorMessage = "'public'";
        }
      }
      else {
        if (!hasStatic){
          errorMessage = "'public'";
        } else {
          errorMessage = "'public' and non-static";
        }
      }
    }
    else {
      if (!hasStatic) {
        if (shouldBeStatic) {
          errorMessage = "'static'";
        }
      }
      else if (shouldBeNonStatic) {
        errorMessage = "non-static";
      }
    }
    return errorMessage;
  }
}
