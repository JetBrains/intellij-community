package com.siyeh.ig.junit;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: ddt
 * Date: 5/22/13
 */
public class JUnitDatapointInspection extends BaseInspection {
  public static final String DATAPOINT_FQN = "org.junit.experimental.theories.DataPoint";


  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("junit.datapoint.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return (String)infos[0];
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return infos.length > 1 ? new MakePublicStaticFix((String)infos[1], true) : null;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitField(PsiField field) {
        final boolean dataPointAnnotated = AnnotationUtil.isAnnotated(field, DATAPOINT_FQN, false);
        if (dataPointAnnotated) {
          final String errorMessage = JUnitRuleInspection.getPublicStaticErrorMessage(field, false, true);
          if (errorMessage != null) {
            registerError(field.getNameIdentifier(),
                          InspectionGadgetsBundle.message("junit.datapoint.problem.descriptor", errorMessage),
                          "Make field " + errorMessage, DATAPOINT_FQN);
          }
        }
      }
    };
  }
}
