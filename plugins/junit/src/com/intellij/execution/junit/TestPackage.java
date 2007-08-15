package com.intellij.execution.junit;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ConfigurationUtil;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.rt.execution.junit.JUnitStarter;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class TestPackage extends TestObject {
  public TestPackage(final Project project,
                     final JUnitConfiguration configuration,
                     RunnerSettings runnerSettings,
                     ConfigurationPerRunnerSettings configurationSettings) {
    super(project, configuration, runnerSettings, configurationSettings);
  }


  public SourceScope getSourceScope() {
    final JUnitConfiguration.Data data = myConfiguration.getPersistentData();
    return data.getScope().getSourceScope(myConfiguration);
  }

  protected void initialize() throws ExecutionException {
    super.initialize();
    final Project project = myConfiguration.getProject();
    final PsiManager psiManager = PsiManager.getInstance(project);
    final JUnitConfiguration.Data data = myConfiguration.getPersistentData();
    final String packageName = data.getPackageName();
    final PsiPackage aPackage = psiManager.findPackage(packageName);
    if (aPackage == null) throw CantRunException.packageNotFound(packageName);
    final TestClassFilter filter = getClassFilter(aPackage);
    final ExecutionException[] exception = new ExecutionException[1];
    findTestsWithProgress(new FindCallback() {
      public void found(@NotNull final Collection<PsiClass> classes, final boolean isJunit4) {
        if (classes.isEmpty()) {
          exception[0] = new CantRunException(ExecutionBundle.message("no.tests.found.in.package.error.message", packageName));
          return;
        }

        if (isJunit4) {
          myJavaParameters.getProgramParametersList().add(JUnitStarter.JUNIT4_PARAMETER);
        }

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
        addClassesListToJavaParameters(classes, packageName);
      }
    }, filter);
    if (exception[0] != null) {
      throw exception[0];
    }
  }

  private TestClassFilter getClassFilter(final PsiPackage aPackage) throws JUnitUtil.NoJUnitException {
    Module module = myConfiguration.getConfigurationModule().getModule();
    if (myConfiguration.getPersistentData().getScope() == TestSearchScope.WHOLE_PROJECT){
      module = null;
    }
    final TestClassFilter classFilter = TestClassFilter.create(getSourceScope(), module);
    return classFilter.intersectionWith(GlobalSearchScope.packageScope(aPackage, true));
  }

  public String suggestActionName() {
    final String configurationName = myConfiguration.getName();
    if (!myConfiguration.isGeneratedName()) {
      return "'" + configurationName + "'";
    }
    final JUnitConfiguration.Data data = myConfiguration.getPersistentData();
    if (data.getPackageName().trim().length() > 0) {
      return ExecutionBundle.message("test.in.scope.presentable.text", data.getPackageName());
    }
    return ExecutionBundle.message("all.tests.scope.presentable.text");
  }

  public RefactoringElementListener getListener(final PsiElement element, final JUnitConfiguration configuration) {
    if (!(element instanceof PsiPackage)) return null;
    return RefactoringListeners.getListener((PsiPackage)element, configuration.myPackage);
  }

  public boolean isConfiguredByElement(final JUnitConfiguration configuration, final PsiElement element) {
    final PsiPackage aPackage;
    if (element instanceof PsiPackage) {
      aPackage = (PsiPackage)element;
    }
    else if (element instanceof PsiDirectory) {
      aPackage = ((PsiDirectory)element).getPackage();
    }
    else {
      return false;
    }
    return aPackage != null
           && Comparing.equal(aPackage.getQualifiedName(), configuration.getPersistentData().getPackageName());
  }

  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();
    final String packageName = myConfiguration.getPersistentData().getPackageName();
    final PsiPackage aPackage = PsiManager.getInstance(myConfiguration.getProject()).findPackage(packageName);
    if (aPackage == null) {
      throw new RuntimeConfigurationWarning(ExecutionBundle.message("package.does.not.exist.error.message", packageName));
    }
    if (getSourceScope() == null) {
      myConfiguration.getConfigurationModule().checkForWarning();
    }
  }

  public static void findTestsWithProgress(final FindCallback callback, final TestClassFilter classFilter) {
    if (isSyncSearch()) {
      THashSet<PsiClass> classes = new THashSet<PsiClass>();
      boolean isJUnit4 = ConfigurationUtil.findAllTestClasses(classFilter, classes);
      callback.found(classes, isJUnit4);
      return;
    }

    final THashSet<PsiClass> classes = new THashSet<PsiClass>();
    final boolean[] isJunit4 = new boolean[1];
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        isJunit4[0] = ConfigurationUtil.findAllTestClasses(classFilter, classes);
      }
    }, ExecutionBundle.message("seaching.test.progress.title"), true, classFilter.getProject());

    callback.found(classes, isJunit4[0]);
  }

  private static boolean isSyncSearch() {
    return ApplicationManager.getApplication().isUnitTestMode();
  }

  public static interface FindCallback {
    /**
     * Invoked in dispatch thread
     */
    void found(@NotNull Collection<PsiClass> classes, final boolean isJunit4);
  }
}
