package com.intellij.execution.junit;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Location;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.rt.execution.junit.JUnitStarter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TestMethods extends TestMethod {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit.TestMethods");

  private final Collection<AbstractTestProxy> myFailedTests;

  public TestMethods(final Project project,
                     final JUnitConfiguration configuration,
                     RunnerSettings runnerSettings,
                     ConfigurationPerRunnerSettings configurationSettings,
                     Collection<AbstractTestProxy> failedTests) {
    super(project, configuration, runnerSettings, configurationSettings);
    myFailedTests = failedTests;
  }

  protected void initialize() throws ExecutionException {
    final boolean isMerge = myConfiguration.isMergeWithPreviousResults();
    try {
      myConfiguration.setMergeWithPreviousResults(true);
      defaultInitialize();
    }
    finally {
      myConfiguration.setMergeWithPreviousResults(isMerge);
    }

    final JUnitConfiguration.Data data = myConfiguration.getPersistentData();
    RunConfigurationModule module = myConfiguration.getConfigurationModule();
    final Project project = module.getProject();
    addJUnit4Parameter(data, project);
    final ExecutionException[] exception = new ExecutionException[1];
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        try {
          myConfiguration.configureClasspath(myJavaParameters);
        }
        catch (CantRunException e) {
          exception[0] = e;
        }
      }
    });
    if (exception[0] != null) throw exception[0];
    List<PsiMethod> methods = new ArrayList<PsiMethod>();
    for (AbstractTestProxy failedTest : myFailedTests) {
      Location location = failedTest.getLocation(project);
      if (location == null) continue;
      PsiElement psiElement = location.getPsiElement();
      LOG.assertTrue(psiElement instanceof PsiMethod);
      PsiMethod method = (PsiMethod)psiElement;
      methods.add(method);
    }
    addClassesListToJavaParameters(methods, data.getPackageName());

  }
  protected void addJUnit4Parameter(final JUnitConfiguration.Data data, Project project) {
    for (AbstractTestProxy failedTest : myFailedTests) {
      Location location = failedTest.getLocation(project);
      if (!(location instanceof MethodLocation)) continue;
      PsiMethod method = ((MethodLocation)location).getPsiElement();
      if (JUnitUtil.isTestAnnotated(method)) {
        myJavaParameters.getProgramParametersList().add(JUnitStarter.JUNIT4_PARAMETER);
        break;
      }
    }
  }

  public String suggestActionName() {
    return ExecutionBundle.message("rerun.failed.tests.action.name");
  }
}
