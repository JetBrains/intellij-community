package com.siyeh.ig.junit;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.psiutils.TestUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class TestMethodInProductCodeInspection extends MethodInspection {


  public String getID() {
    return "JUnitTestMethodInProductSource";
  }

  public String getGroupDisplayName() {
    return GroupNames.JUNIT_GROUP_NAME;
  }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "test.method.in.product.code.problem.descriptor");
    }

  public BaseInspectionVisitor buildVisitor() {
    return new TestCaseInProductCodeVisitor();
  }

  private static class TestCaseInProductCodeVisitor extends BaseInspectionVisitor {

      public void visitMethod(PsiMethod method) {
          final PsiClass containingClass = method.getContainingClass();
          if (TestUtils.isTest(containingClass)) {
              return;
          }
          if (!AnnotationUtil.isAnnotated(method, "org.junit.Test", true)) {
              return;
          }
          registerMethodError(method);
      }
  }
}
