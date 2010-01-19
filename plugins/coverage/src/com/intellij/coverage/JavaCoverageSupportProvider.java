package com.intellij.coverage;

import com.intellij.execution.RunJavaConfiguration;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.execution.configurations.coverage.JavaCoverageEnabledConfiguration;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman.Chernyatchik
 */
public class JavaCoverageSupportProvider extends CoverageSupportProvider {
  @Override
  public CoverageEnabledConfiguration createCoverageEnabledConfiguration(final ModuleBasedConfiguration conf) {
    if (conf instanceof RunJavaConfiguration) {
      return new JavaCoverageEnabledConfiguration(conf);
    }
    return null;
  }

  @Nullable
  @Override
  public String getTestMethodName(@NotNull PsiElement element, @NotNull AbstractTestProxy proxy) {
    if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;
      final PsiClass psiClass = method.getContainingClass();
      assert psiClass != null;

      return psiClass.getQualifiedName() + "." + method.getName();
    }

    return null;
  }
}
