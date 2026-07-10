// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.codeInsight.MetaAnnotationUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.JUnitBundle;
import com.intellij.execution.JavaTestFrameworkRunnableState;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.target.local.LocalTargetEnvironment;
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest;
import com.intellij.execution.testframework.AbstractInClassConfigurationProducer;
import com.intellij.execution.testframework.SearchForTestsTask;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.testframework.sm.runner.ui.TestResultsViewer;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.task.ProjectTaskManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.siyeh.ig.junit.JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST_TEMPLATE;

public final class TestInClassConfigurationProducer extends JUnitConfigurationProducer
  implements DumbAware {
  private final JUnitInClassConfigurationProducerDelegate myDelegate = new JUnitInClassConfigurationProducerDelegate();

  @Override
  protected boolean setupConfigurationFromContext(@NotNull JUnitConfiguration configuration,
                                                  @NotNull ConfigurationContext context,
                                                  @NotNull Ref<PsiElement> sourceElement) {
    return myDelegate.setupConfigurationFromContext(configuration, context, sourceElement);
  }

  @Override
  public void onFirstRun(@NotNull ConfigurationFromContext configuration,
                         @NotNull ConfigurationContext fromContext,
                         @NotNull Runnable performRunnable) {
    myDelegate.onFirstRun(configuration, fromContext, performRunnable);
  }

  @Override
  public boolean isConfigurationFromContext(@NotNull JUnitConfiguration configuration, @NotNull ConfigurationContext context) {
    String[] nodeIds = UniqueIdConfigurationProducer.getNodeIds(context);
    if (nodeIds != null && nodeIds.length > 0) return false;
    return super.isConfigurationFromContext(configuration, context);
  }

  @Override
  protected boolean isApplicableTestType(String type, ConfigurationContext context) {
    return myDelegate.isApplicableTestType(type, context);
  }

  private static class JUnitInClassConfigurationProducerDelegate extends AbstractInClassConfigurationProducer<JUnitConfiguration> {
    @Override
    public @NotNull ConfigurationFactory getConfigurationFactory() {
      return JUnitConfigurationType.getInstance().getConfigurationFactories()[0];
    }

    @Override
    public void onFirstRun(@NotNull ConfigurationFromContext configuration,
                           @NotNull ConfigurationContext fromContext,
                           @NotNull Runnable performRunnable) {
      PsiElement sourceElement = configuration.getSourceElement();
      if (configuration.getConfiguration() instanceof JUnitConfiguration junitConfiguration &&
          !junitConfiguration.needPrepareTarget() &&
          sourceElement instanceof PsiMethod method &&
          ReadAction.computeBlocking(() -> method.isValid() &&
                                           MetaAnnotationUtil.isMetaAnnotated(method, Set.of(ORG_JUNIT_JUPITER_API_TEST_TEMPLATE,
                                                                                             JUnitUtil.TEST5_FACTORY_ANNOTATION)))) {
        Project project = fromContext.getProject();
        Editor editor = CommonDataKeys.EDITOR.getData(fromContext.getDataContext());
        RunnerAndConfigurationSettings settings = configuration.getConfigurationSettings();
        String methodName = ReadAction.computeBlocking(method::getName);
        super.onFirstRun(configuration, fromContext, () -> new JUnitTestParameterChooser(project, settings, methodName, junitConfiguration, performRunnable)
          .choose(editor, methodName, fromContext));
      }
      else {
        super.onFirstRun(configuration, fromContext, performRunnable);
      }
    }

    @Override
    protected boolean isApplicableTestType(String type, ConfigurationContext context) {
      return JUnitConfiguration.TEST_CLASS.equals(type) || JUnitConfiguration.TEST_METHOD.equals(type);
    }

    @Override
    protected boolean isRequiredVisibility(PsiMember psiElement) {
      if (JUnitUtil.isJUnit5(psiElement)) {
        return true;
      }
      return super.isRequiredVisibility(psiElement);
    }

    @Override
    protected boolean setupConfigurationFromContext(@NotNull JUnitConfiguration configuration,
                                                    @NotNull ConfigurationContext context,
                                                    @NotNull Ref<PsiElement> sourceElement) {
      return super.setupConfigurationFromContext(configuration, context, sourceElement);
    }
  }

  private static class JUnitTestParameterChooser extends TestParameterChooser<Invocation> {
    private final @NotNull Project myProject;
    private final @NotNull RunnerAndConfigurationSettings mySettings;
    private final @NotNull String myMethodName;
    private final @NlsSafe @NotNull JUnitConfiguration myJUnitConfiguration;
    private final @NotNull Runnable myPerformRunnable;

    private JUnitTestParameterChooser(@NotNull Project project,
                                      @NotNull RunnerAndConfigurationSettings settings,
                                      @NlsSafe @NotNull String methodName,
                                      @NotNull JUnitConfiguration junitConfiguration,
                                      @NotNull Runnable performRunnable) {
      myProject = project;
      mySettings = settings;
      myMethodName = methodName;
      myJUnitConfiguration = junitConfiguration;
      myPerformRunnable = performRunnable;
    }

    @Override
    protected void collectParameters(@NotNull Consumer<? super List<Invocation>> onCollected) {
      new ParameterCollector(myProject, mySettings, myMethodName).collect(onCollected);
    }

    @Override
    protected @NlsSafe @NotNull String getItemText(@NotNull Invocation item) {
      return item.displayName();
    }

    @Override
    protected void runAllParameters() {
      myPerformRunnable.run();
    }

    @Override
    protected void runParameter(@NotNull Invocation item) {
      JUnitConfiguration.Data data = myJUnitConfiguration.getPersistentData();
      data.setUniqueIds(item.uniqueId());
      data.TEST_OBJECT = JUnitConfiguration.TEST_UNIQUE_ID;
      myJUnitConfiguration.setName(myMethodName + " " + item.displayName());
      myPerformRunnable.run();
    }
  }

  private record Invocation(@NotNull String uniqueId, @NotNull @NlsSafe String displayName) {
  }

  private static final class ParameterCollector {
    private static final Logger LOG = Logger.getInstance(TestInClassConfigurationProducer.class);
    private static final String COLLECT_PARAMETERS_PROPERTY = "idea.junit.collect.parameters";

    private final Project myProject;
    private final RunnerAndConfigurationSettings mySettings;
    private final @NlsSafe String myMethodName;

    // Runtime state of the single fork run by this collector.
    private final CountDownLatch myTreeReady = new CountDownLatch(1);
    private OSProcessHandler myProcess;
    private SMTRunnerConsoleView myConsole;

    ParameterCollector(@NotNull Project project, @NotNull RunnerAndConfigurationSettings settings, @NotNull @NlsSafe String methodName) {
      myProject = project;
      mySettings = settings;
      myMethodName = methodName;
    }

    public void collect(@NotNull Consumer<? super List<Invocation>> onCollected) {
      Module[] modules = configuration().getModules();
      if (modules.length == 0) {
        runInBackground(onCollected);
        return;
      }
      ProjectTaskManager.getInstance(myProject).build(modules)
        .onSuccess(result -> {
          if (result.isAborted() || result.hasErrors()) {
            onCollected.accept(List.of());
          }
          else {
            runInBackground(onCollected);
          }
        })
        .onError(error -> {
          LOG.warn("Build before parameter collection failed", error);
          onCollected.accept(List.of());
        });
    }

    private void runInBackground(@NotNull Consumer<? super List<Invocation>> onCollected) {
      ProgressManager.getInstance().run(
        new Task.Backgroundable(myProject, JUnitBundle.message("run.single.parameter.collect.progress", myMethodName), true) {
          private List<Invocation> myInvocations = List.of();

          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            try {
              Executor executor = DefaultRunExecutor.getRunExecutorInstance();
              JavaTestFrameworkRunnableState<?> state = createDryRunState(executor);
              if (state == null) {
                myInvocations = List.of();
              }
              else {
                launch(state, executor, indicator);
                try {
                  myInvocations = awaitTree(indicator) ? collectInvocations() : List.of();
                }
                finally {
                  disposeConsole();
                }
              }
            }
            catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            catch (ExecutionException e) {
              LOG.warn("Failed to collect parameterized JUnit invocations", e);
            }
          }

          @Override
          public void onSuccess() {
            onCollected.accept(myInvocations);
          }
        });
    }

    private @Nullable JavaTestFrameworkRunnableState<?> createDryRunState(@NotNull Executor executor) throws ExecutionException {
      ProgramRunner<?> runner = ProgramRunner.getRunner(DefaultRunExecutor.EXECUTOR_ID, configuration());
      if (runner == null) return null;
      ExecutionEnvironment environment = new ExecutionEnvironment(executor, runner, mySettings, myProject);
      return configuration().getState(executor, environment) instanceof JavaTestFrameworkRunnableState<?> state ? state : null;
    }

    private void launch(@NotNull JavaTestFrameworkRunnableState<?> state,
                        @NotNull Executor executor,
                        @NotNull ProgressIndicator indicator) throws ExecutionException {
      state.downloadAdditionalDependencies(state.getJavaParameters());
      state.appendForkInfo(executor);
      state.appendRepeatMode();
      JavaParameters parameters = state.getJavaParameters();
      // Signal "collect" mode to the forked JUnit runner; it turns this into the framework-specific config parameters
      // (e.g. Jupiter extension auto-detection) and the interceptor reads it back from the ExtensionContext.
      parameters.getVMParametersList().addProperty(COLLECT_PARAMETERS_PROPERTY, "true");
      parameters.setUseDynamicClasspath(myProject);
      state.resolveServerSocketPort(new LocalTargetEnvironment(new LocalTargetEnvironmentRequest()));

      myProcess = new OSProcessHandler(parameters.toCommandLine());
      attachConsole(executor);

      SearchForTestsTask searchTask = state.createSearchingForTestsTask(new LocalTargetEnvironment(new LocalTargetEnvironmentRequest()));
      if (searchTask != null) {
        searchTask.run(indicator);
        ApplicationManager.getApplication().invokeAndWait(searchTask::onSuccess);
      }
      myProcess.startNotify();
    }

    private void attachConsole(@NotNull Executor executor) {
      SMTRunnerConsoleProperties properties = configuration().createTestConsoleProperties(executor);
      properties.setIdBasedTestTree(true);
      ApplicationManager.getApplication().invokeAndWait(() -> {
        myConsole = (SMTRunnerConsoleView)SMTestRunnerConnectionUtil.createConsole("JUnit", properties);
        myConsole.getResultsViewer().addEventsListener(new TestResultsViewer.EventsListener() {
          @Override
          public void onTestingFinished(@NotNull TestResultsViewer sender) {
            myTreeReady.countDown();
          }
        });
        myConsole.attachToProcess(myProcess);
      });
    }

    private boolean awaitTree(@NotNull ProgressIndicator indicator) throws InterruptedException {
      while (!myTreeReady.await(100, TimeUnit.MILLISECONDS)) {
        if (indicator.isCanceled()) {
          myProcess.destroyProcess();
          return false;
        }
        if (myProcess.isProcessTerminated()) {
          myTreeReady.await(2, TimeUnit.SECONDS); // let the console drain any buffered output
          return true;
        }
      }
      return true;
    }

    private @NotNull List<Invocation> collectInvocations() {
      return ReadAction.nonBlocking(() -> collectLeaves(myConsole.getResultsViewer().getTestsRootNode(), new ArrayList<>()))
        .executeSynchronously();
    }

    private void disposeConsole() {
      if (myConsole != null) {
        SMTRunnerConsoleView console = myConsole;
        ApplicationManager.getApplication().invokeAndWait(() -> Disposer.dispose(console));
      }
    }

    private @NotNull JUnitConfiguration configuration() {
      return (JUnitConfiguration)mySettings.getConfiguration();
    }

    private static @NotNull List<Invocation> collectLeaves(@Nullable SMTestProxy node, @NotNull List<Invocation> result) {
      if (node == null) return result;
      if (node.isLeaf()) {
        String nodeId = node.getUserData(SMTestProxy.NODE_ID);
        if (nodeId != null) {
          result.add(new Invocation(nodeId, node.getPresentableName()));
        }
        return result;
      }
      for (SMTestProxy child : node.getChildren()) {
        collectLeaves(child, result);
      }
      return result;
    }
  }
}
