package com.intellij.execution.junit;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;

import java.util.Iterator;

public class TestMethodConfigurationProducer extends JUnitConfigurationProducer {
  private Location<PsiMethod> myMethodLocation;

  protected RunnerAndConfigurationSettingsImpl createConfigurationByElement(final Location location, final ConfigurationContext context) {
    final Project project = location.getProject();

    myMethodLocation = getTestMethod(location);
    if (myMethodLocation == null) return null;
    RunnerAndConfigurationSettingsImpl settings = cloneTemplateConfiguration(project, context);
    final JUnitConfiguration configuration = (JUnitConfiguration)settings.getConfiguration();
    final Module originalModule = configuration.getConfigurationModule().getModule();
    configuration.beMethodConfiguration(myMethodLocation);
    configuration.restoreOriginalModule(originalModule);
    configuration.setUpCoverageFilters();
    return settings;
  }

  public PsiElement getSourceElement() {
    return myMethodLocation.getPsiElement();
  }

  private static Location<PsiMethod> getTestMethod(final Location<?> location) {
    for (Iterator<Location<PsiMethod>> iterator = location.getAncestors(PsiMethod.class, false); iterator.hasNext();) {
      final Location<PsiMethod> methodLocation = iterator.next();
      if (JUnitUtil.isTestMethod(methodLocation)) return methodLocation;
    }
    return null;
  }
}

