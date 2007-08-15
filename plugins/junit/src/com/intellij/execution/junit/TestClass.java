package com.intellij.execution.junit;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.rt.execution.junit.JUnitStarter;

class TestClass extends TestObject {
  public TestClass(final Project project,
                   final JUnitConfiguration configuration,
                   RunnerSettings runnerSettings,
                   ConfigurationPerRunnerSettings configurationSettings) {
    super(project, configuration, runnerSettings, configurationSettings);
  }

  protected void initialize() throws ExecutionException {
    super.initialize();
    final JUnitConfiguration.Data data = myConfiguration.getPersistentData();
    RunConfigurationModule module = myConfiguration.getConfigurationModule();
    configureModule(myJavaParameters, module, data.getMainClassName());
    Location<PsiClass> classLocation = PsiLocation.fromClassQualifiedName(module.getProject(), data.getMainClassPsiName());
    if (JUnitUtil.isJUnit4TestClass(classLocation.getPsiElement())) {
      myJavaParameters.getProgramParametersList().add(JUnitStarter.JUNIT4_PARAMETER);
    }
    myJavaParameters.getProgramParametersList().add(data.getMainClassName());
  }

  public String suggestActionName() {
    return ExecutionUtil.shortenName(ExecutionUtil.getShortClassName(myConfiguration.getPersistentData().MAIN_CLASS_NAME), 0);
  }

  public RefactoringElementListener getListener(final PsiElement element, final JUnitConfiguration configuration) {
    return RefactoringListeners.getClassOrPackageListener(element, configuration.myClass);
  }

  public boolean isConfiguredByElement(final JUnitConfiguration configuration, final PsiElement element) {
    final PsiClass aClass = JUnitUtil.getTestClass(element);
    if (aClass == null) {
      return false;
    }
    final PsiMethod method = JUnitUtil.getTestMethod(element);
    if (method != null) {
      // 'test class' configuration is not equal to the 'test method' configuration!
      return false;
    }
    return Comparing.equal(ExecutionUtil.getRuntimeQualifiedName(aClass), configuration.getPersistentData().getMainClassName());
  }

  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();
    final String testClassName = myConfiguration.getPersistentData().getMainClassName();
    final RunConfigurationModule configurationModule = myConfiguration.getConfigurationModule();
    final PsiClass testClass = configurationModule.checkModuleAndClassName(testClassName, ExecutionBundle.message("no.test.class.specified.error.text"));
    if (!JUnitUtil.isTestClass(testClass)) {
      throw new RuntimeConfigurationWarning(ExecutionBundle.message("class.isnt.test.class.error.message", testClassName));
    }
  }
}
