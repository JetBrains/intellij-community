/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.android.run.testing;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import org.jetbrains.android.dom.manifest.Instrumentation;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.run.AndroidApplicationLauncher;
import org.jetbrains.android.run.AndroidRunConfigurationBase;
import org.jetbrains.android.run.AndroidRunConfigurationEditor;
import org.jetbrains.android.run.AndroidRunningState;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 27, 2009
 * Time: 2:23:56 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidTestRunConfiguration extends AndroidRunConfigurationBase {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.run.testing.AndroidTestRunConfiguration");

  public static final int TEST_ALL_IN_MODULE = 0;
  public static final int TEST_ALL_IN_PACKAGE = 1;
  public static final int TEST_CLASS = 2;
  public static final int TEST_METHOD = 3;

  public int TESTING_TYPE = TEST_ALL_IN_MODULE;
  public String INSTRUMENTATION_RUNNER_CLASS = "";

  public String METHOD_NAME = "";
  public String CLASS_NAME = "";
  public String PACKAGE_NAME = "";

  public AndroidTestRunConfiguration(final String name, final Project project, final ConfigurationFactory factory) {
    super(name, project, factory);
  }

  @Override
  public void checkConfiguration(@NotNull AndroidFacet facet) throws RuntimeConfigurationException {
    Module module = facet.getModule();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(module.getProject());
    switch (TESTING_TYPE) {
      case TEST_ALL_IN_PACKAGE:
        final PsiPackage testPackage = facade.findPackage(PACKAGE_NAME);
        if (testPackage == null) {
          throw new RuntimeConfigurationWarning(ExecutionBundle.message("package.does.not.exist.error.message", PACKAGE_NAME));
        }
        break;
      case TEST_CLASS:
        final PsiClass testClass =
          getConfigurationModule().checkModuleAndClassName(CLASS_NAME, ExecutionBundle.message("no.test.class.specified.error.text"));
        if (!JUnitUtil.isTestClass(testClass)) {
          throw new RuntimeConfigurationWarning(ExecutionBundle.message("class.isnt.test.class.error.message", CLASS_NAME));
        }
        break;
      case TEST_METHOD:
        checkTestMethod();
        break;
    }
    if (INSTRUMENTATION_RUNNER_CLASS.length() > 0) {
      if (facade.findClass(INSTRUMENTATION_RUNNER_CLASS, module.getModuleWithDependenciesAndLibrariesScope(true)) == null) {
        throw new RuntimeConfigurationError(AndroidBundle.message("instrumentation.runner.class.not.specified.error"));
      }
    }
  }

  private void checkTestMethod() throws RuntimeConfigurationException {
    JavaRunConfigurationModule configurationModule = getConfigurationModule();
    final PsiClass testClass =
      configurationModule.checkModuleAndClassName(CLASS_NAME, ExecutionBundle.message("no.test.class.specified.error.text"));
    if (!JUnitUtil.isTestClass(testClass)) {
      throw new RuntimeConfigurationWarning(ExecutionBundle.message("class.isnt.test.class.error.message", CLASS_NAME));
    }
    if (METHOD_NAME == null || METHOD_NAME.trim().length() == 0) {
      throw new RuntimeConfigurationError(ExecutionBundle.message("method.name.not.specified.error.message"));
    }
    final JUnitUtil.TestMethodFilter filter = new JUnitUtil.TestMethodFilter(testClass);
    boolean found = false;
    boolean testAnnotated = false;
    for (final PsiMethod method : testClass.findMethodsByName(METHOD_NAME, true)) {
      if (filter.value(method)) found = true;
      if (JUnitUtil.isTestAnnotated(method)) testAnnotated = true;
    }
    if (!found) {
      throw new RuntimeConfigurationWarning(ExecutionBundle.message("test.method.doesnt.exist.error.message", METHOD_NAME));
    }

    if (!AnnotationUtil.isAnnotated(testClass, JUnitUtil.RUN_WITH, true) && !testAnnotated) {
      try {
        final PsiClass testCaseClass = JUnitUtil.getTestCaseClass(configurationModule.getModule());
        if (!testClass.isInheritor(testCaseClass, true)) {
          throw new RuntimeConfigurationError(ExecutionBundle.message("class.isnt.inheritor.of.testcase.error.message", CLASS_NAME));
        }
      }
      catch (JUnitUtil.NoJUnitException e) {
        throw new RuntimeConfigurationWarning(ExecutionBundle.message(AndroidBundle.message("cannot.find.testcase.error")));
      }
    }
  }

  @Override
  protected ModuleBasedConfiguration createInstance() {
    return new AndroidTestRunConfiguration(getName(), getProject(), AndroidTestRunConfigurationType.getInstance().getFactory());
  }

  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    Project project = getProject();
    AndroidRunConfigurationEditor<AndroidTestRunConfiguration> editor =
      new AndroidRunConfigurationEditor<AndroidTestRunConfiguration>(project);
    editor.setConfigurationSpecificEditor(new TestRunParameters(project, editor.getModuleSelector()));
    return editor;
  }

  @NotNull
  @Override
  protected ConsoleView attachConsole(AndroidRunningState state, Executor executor) throws ExecutionException {
    TestConsoleProperties properties = new AndroidTestConsoleProperties(this, executor);
    BaseTestsOutputConsoleView consoleView = SMTestRunnerConnectionUtil
      .attachRunner("Android", state.getProcessHandler(), properties, state.getRunnerSettings(), state.getConfigurationSettings()
      );
    Disposer.register(state.getAndroidFacet().getModule().getProject(), consoleView);
    return consoleView;
  }

  @Override
  protected boolean supportMultipleDevices() {
    return false;
  }

  @Override
  protected AndroidApplicationLauncher getApplicationLauncher(AndroidFacet facet) {
    String runner = INSTRUMENTATION_RUNNER_CLASS.length() > 0 ? INSTRUMENTATION_RUNNER_CLASS : getRunnerFromManifest(facet);
    return new MyApplicationLauncher(runner);
  }

  @Nullable
  private static String getRunnerFromManifest(AndroidFacet facet) {
    Manifest manifest = facet.getManifest();
    if (manifest != null) {
      for (Instrumentation instrumentation : manifest.getInstrumentations()) {
        if (instrumentation != null) {
          PsiClass instrumentationClass = instrumentation.getInstrumentationClass().getValue();
          if (instrumentationClass != null) {
            return instrumentationClass.getQualifiedName();
          }
        }
      }
    }
    return null;
  }

  private class MyApplicationLauncher extends AndroidApplicationLauncher {
    private final String myInstrumentationTestRunner;

    private MyApplicationLauncher(String instrumentationTestRunner) {
      this.myInstrumentationTestRunner = instrumentationTestRunner;
    }

    public boolean launch(@NotNull AndroidRunningState state, @NotNull IDevice device)
      throws IOException, AdbCommandRejectedException, TimeoutException {
      state.getProcessHandler().notifyTextAvailable("Running tests", ProcessOutputTypes.STDOUT);
      RemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(state.getPackageName(), myInstrumentationTestRunner, device);
      switch (TESTING_TYPE) {
        case TEST_ALL_IN_PACKAGE:
          runner.setTestPackageName(PACKAGE_NAME);
          break;
        case TEST_CLASS:
          runner.setClassName(CLASS_NAME);
          break;
        case TEST_METHOD:
          runner.setMethodName(CLASS_NAME, METHOD_NAME);
          break;
      }
      runner.setDebug(state.isDebugMode());
      try {
        runner.run(new AndroidTestListener(state));
      }
      catch (ShellCommandUnresponsiveException e) {
        LOG.info(e);
        state.getProcessHandler().notifyTextAvailable("Error: time out", ProcessOutputTypes.STDERR);
      }
      return true;
    }
  }
}
