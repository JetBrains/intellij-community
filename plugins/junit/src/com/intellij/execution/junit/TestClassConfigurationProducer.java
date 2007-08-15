package com.intellij.execution.junit;

import com.intellij.execution.ExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;

public class TestClassConfigurationProducer extends JUnitConfigurationProducer {
  private PsiClass myTestClass;

  protected RunnerAndConfigurationSettingsImpl createConfigurationByElement(Location location, final ConfigurationContext context) {
    location = ExecutionUtil.stepIntoSingleClass(location);
    final Project project = location.getProject();

    myTestClass = JUnitUtil.getTestClass(location);
    if (myTestClass == null) return null;
    RunnerAndConfigurationSettingsImpl settings = cloneTemplateConfiguration(project, context);
    final JUnitConfiguration configuration = (JUnitConfiguration)settings.getConfiguration();
    final Module[] modules = configuration.getModules();
    final Module originalModule = modules == null || modules.length == 0 ? null : modules[0];
    configuration.beClassConfiguration(myTestClass);
    configuration.restoreOriginalModule(originalModule);
    configuration.setUpCoverageFilters();
    return settings;
  }

  public PsiElement getSourceElement() {
    return myTestClass;
  }


}
