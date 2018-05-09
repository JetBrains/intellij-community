// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner.history;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.history.actions.AbstractImportTestsAction;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.OutputStream;

public class ImportedTestRunnableState implements RunProfileState {
  private final AbstractImportTestsAction.ImportRunProfile myRunProfile;
  private final File myFile;

  public ImportedTestRunnableState(AbstractImportTestsAction.ImportRunProfile profile, File file) {
    myRunProfile = profile;
    myFile = file;
  }

  @Nullable
  @Override
  public ExecutionResult execute(Executor executor, @NotNull ProgramRunner runner) {
    final MyEmptyProcessHandler handler = new MyEmptyProcessHandler();
    final SMTRunnerConsoleProperties properties = myRunProfile.getProperties();
    RunProfile configuration;
    final String frameworkName;
    if (properties != null) {
      configuration = properties.getConfiguration();
      frameworkName = properties.getTestFrameworkName();
    }
    else {
      configuration = myRunProfile;
      frameworkName = "Import Test Results";
    }
    final ImportedTestConsoleProperties consoleProperties = new ImportedTestConsoleProperties(properties, myFile, handler, myRunProfile.getProject(),
                                                                                              configuration, frameworkName, executor);
    final BaseTestsOutputConsoleView console = SMTestRunnerConnectionUtil.createConsole(consoleProperties.getTestFrameworkName(), 
                                                                                        consoleProperties);
    final JComponent component = console.getComponent();
    AbstractRerunFailedTestsAction rerunFailedTestsAction = null;
    if (component instanceof TestFrameworkRunningModel) {
      rerunFailedTestsAction = consoleProperties.createRerunFailedTestsAction(console);
      if (rerunFailedTestsAction != null) {
        rerunFailedTestsAction.setModelProvider(() -> (TestFrameworkRunningModel)component);
      }
    }
    
    console.attachToProcess(handler);
    final DefaultExecutionResult result = new DefaultExecutionResult(console, handler);
    if (rerunFailedTestsAction != null) {
      result.setRestartActions(rerunFailedTestsAction);
    }
    return result;
  }

  private static class MyEmptyProcessHandler extends ProcessHandler {
    @Override
    protected void destroyProcessImpl() {}

    @Override
    protected void detachProcessImpl() {
      notifyProcessTerminated(0);
    }

    @Override
    public boolean detachIsDefault() {
      return false;
    }

    @Nullable
    @Override
    public OutputStream getProcessInput() {
      return null;
    }
  }
}
