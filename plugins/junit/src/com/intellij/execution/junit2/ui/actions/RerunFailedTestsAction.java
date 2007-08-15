package com.intellij.execution.junit2.ui.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionRegistry;
import com.intellij.execution.configurations.*;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.TestMethods;
import com.intellij.execution.testframework.Filter;
import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties;
import com.intellij.execution.runners.JavaProgramRunner;
import com.intellij.execution.runners.RunStrategyImpl;
import com.intellij.execution.runners.RunnerInfo;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Alexey
 */
public class RerunFailedTestsAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit2.ui.actions.RerunFailedTestsAction");
  private JUnitRunningModel myModel;
  private final JUnitConsoleProperties myConsoleProperties;
  private final RunnerSettings myRunnerSettings;
  private final ConfigurationPerRunnerSettings myConfigurationPerRunnerSettings;

  public RerunFailedTestsAction(final JUnitConsoleProperties consoleProperties,
                                final RunnerSettings runnerSettings,
                                final ConfigurationPerRunnerSettings configurationSettings) {
    super(ExecutionBundle.message("rerun.failed.tests.action.name"),
          ExecutionBundle.message("rerun.failed.tests.action.description"),
          IconLoader.getIcon("/runConfigurations/rerunFailedTests.png"));
    myConsoleProperties = consoleProperties;
    myRunnerSettings = runnerSettings;
    myConfigurationPerRunnerSettings = configurationSettings;
  }

  public void setModel(JUnitRunningModel model) {
    myModel = model;
  }

  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(isActive(e));
  }

  private boolean isActive(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) return false;
    if (myModel == null || myModel.getRoot() == null) return false;
    List<AbstractTestProxy> failed = getFailedTests();
    return !failed.isEmpty();
  }

  @NotNull private List<AbstractTestProxy> getFailedTests() {
    List<TestProxy> myAllTests = myModel.getRoot().getAllTests();
    return Filter.DEFECTIVE_LEAF.and(Filter.METHOD(myModel.getProject())).select(myAllTests);
  }

  public void actionPerformed(AnActionEvent e) {
    List<AbstractTestProxy> failed = getFailedTests();

    final DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    JUnitConfiguration configuration = myModel.getProperties().getConfiguration();
    final TestMethods testMethods = new TestMethods(project, configuration, myRunnerSettings, myConfigurationPerRunnerSettings, failed);
    boolean isDebug = myConsoleProperties.getDebugSession() != null;
    final JavaProgramRunner defaultRunner = isDebug ? ExecutionRegistry.getInstance().getDebuggerRunner() : ExecutionRegistry.getInstance().getDefaultRunner();

    try {
      final RunProfile profile = new RunProfile() {
        public RunProfileState getState(DataContext context,
                                        RunnerInfo runnerInfo,
                                        RunnerSettings runnerSettings,
                                        ConfigurationPerRunnerSettings configurationSettings) throws ExecutionException {
          testMethods.clear();
          return testMethods;
        }  

        public String getName() {
          return ExecutionBundle.message("rerun.failed.tests.action.name");
        }

        public void checkConfiguration() throws RuntimeConfigurationException {

        }

        public Module[] getModules() {
          return Module.EMPTY_ARRAY;
        }
      };

      RunStrategyImpl.getInstance().execute(profile, dataContext, defaultRunner, myRunnerSettings, myConfigurationPerRunnerSettings);
    }
    catch (ExecutionException e1) {
      LOG.error(e1);
    }
    finally{
      testMethods.clear();
    }
  }
}
