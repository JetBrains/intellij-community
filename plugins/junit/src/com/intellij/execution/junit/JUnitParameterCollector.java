// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.JUnitBundle;
import com.intellij.execution.JavaTestFrameworkRunnableState;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.target.local.LocalTargetEnvironment;
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest;
import com.intellij.execution.testframework.JavaTestLocator;
import com.intellij.execution.testframework.SearchForTestsTask;
import com.intellij.execution.testframework.sm.ServiceMessageUtil;
import com.intellij.execution.testframework.sm.runner.OutputEventSplitter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.rt.junit.JUnitStarter;
import com.intellij.task.ProjectTaskManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import jetbrains.buildServer.messages.serviceMessages.TestStarted;
import jetbrains.buildServer.messages.serviceMessages.TestSuiteStarted;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApiStatus.Internal
public final class JUnitParameterCollector {
  private static final Logger LOG = Logger.getInstance(JUnitParameterCollector.class);

  private final @NotNull Project myProject;
  private final @NotNull RunnerAndConfigurationSettings mySettings;
  private final @NotNull JUnitConfiguration myConfiguration;
  private final @NotNull @NlsSafe String myPresentableName;
  private final @NotNull String myLocationHint;

  /**
   * @param settings        the launch to collect the parameters of. It is launched as it is, so it has to be the instance the run
   *                        itself uses: the InheritorChooser modifies that very configuration in place.
   * @param presentableName the name of the test, as the progress and the popups show it
   */
  public JUnitParameterCollector(@NotNull RunnerAndConfigurationSettings settings, @NotNull @NlsSafe String presentableName) {
    mySettings = settings;
    myConfiguration = (JUnitConfiguration)settings.getConfiguration();
    myProject = myConfiguration.getProject();
    myPresentableName = presentableName;

    JUnitConfiguration.Data data = myConfiguration.getPersistentData();
    String className = data.getMainClassName();
    myLocationHint = StringUtil.isEmpty(className) ? ""
                                                   : JUnitConfiguration.TEST_CLASS.equals(data.TEST_OBJECT)
                                                     ? JavaTestLocator.SUITE_PROTOCOL + "://" + className
                                                     : JavaTestLocator.TEST_PROTOCOL + "://" + className + "/" + data.getMethodName();
  }

  public record Parameter(@NotNull List<String> ids, @NotNull @NlsSafe String displayName) {
  }

  private record Node(@NotNull String id, @NotNull String parentId, @NotNull @NlsSafe String name, @Nullable String locationHint) {
  }

  /**
   * What becomes of the collected parameters. Exactly one of the two methods runs exactly once for every {@link #collect} call, so no
   * launch can be silently dropped.
   */
  public interface Callback {
    /** The parameters to choose from. An empty list means there is nothing to choose from, so the test itself has to run. */
    void onCollected(@NotNull List<Parameter> parameters);

    /** The user cancelled the build or the collection. Nothing may be launched. */
    void onCancelled();
  }

  /** Builds what the test needs, then collects its parameters. Calls the callback exactly once, whatever the build and the fork do. */
  public void collect(@NotNull Callback callback) {
    Module[] modules = myConfiguration.getModules();
    if (modules.length == 0) {
      collectInBackground(callback);
    } else {
      ProjectTaskManager.getInstance(myProject).build(modules)
        .onError(error -> LOG.warn("Build before parameter collection failed", error))
        // onProcessed runs on a resolved and on a rejected build alike, so it is the one exit of this stage
        .onProcessed(result -> {
          if (result != null && result.isAborted()) {
            callback.onCancelled(); // a launch would start that very build again
          }
          else if (result == null || result.hasErrors()) {
            // nothing can be collected out of code that does not compile, so run the test and let its own build report the errors
            callback.onCollected(List.of());
          }
          else {
            collectInBackground(callback);
          }
        });
    }
  }

  private void collectInBackground(@NotNull Callback callback) {
    ProgressManager.getInstance().run(
      new Task.Backgroundable(myProject, JUnitBundle.message("run.single.parameter.collect.progress", myPresentableName), true) {
        private @NotNull List<Parameter> myParameters = List.of();

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            myParameters = parse(runCollectingFork(indicator));
          }
          catch (ExecutionException e) {
            LOG.warn("Failed to collect the parameters of " + myPresentableName, e);
          }
        }

        @Override
        public void onSuccess() {
          callback.onCollected(myParameters);
        }

        @Override
        public void onCancel() {
          callback.onCancelled();
        }

        @Override
        public void onThrowable(@NotNull Throwable error) {
          super.onThrowable(error);
          callback.onCollected(List.of()); // nothing was collected, so run the test itself
        }
      });
  }

  private @NotNull List<String> runCollectingFork(@NotNull ProgressIndicator indicator) throws ExecutionException {
    Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    ProgramRunner<?> runner = ProgramRunner.getRunner(DefaultRunExecutor.EXECUTOR_ID, myConfiguration);
    if (runner == null) return List.of();
    ExecutionEnvironment environment = new ExecutionEnvironment(executor, runner, mySettings, myProject);
    if (!(myConfiguration.getState(executor, environment) instanceof JavaTestFrameworkRunnableState<?> state)) return List.of();

    List<String> messages = Collections.synchronizedList(new ArrayList<>());
    // the splitter hands over whole service messages, whatever chunks the process output arrives in
    OutputEventSplitter splitter = new OutputEventSplitter(false, false) {
      @Override
      public void onTextAvailable(@NotNull String text, @NotNull Key<?> outputType) {
        messages.add(text);
      }
    };
    SearchForTestsTask searchTask = prepareFork(state);
    OSProcessHandler process = new OSProcessHandler(state.getJavaParameters().toCommandLine());
    process.addProcessListener(new ProcessListener() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        splitter.process(event.getText(), outputType);
      }

      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        splitter.flush(); // the last service message waits in the buffer, and no more output can come
      }
    });
    if (searchTask != null) {
      searchTask.attachTaskToProcess(process);
    }
    process.startNotify();
    while (!process.waitFor(100)) {
      if (indicator.isCanceled()) {
        process.destroyProcess();
        // an empty list would mean the test has no parameters, and the test would be launched
        throw new ProcessCanceledException();
      }
    }
    // processTerminated ran before waitFor returned, so every flushed message is in the list
    return messages;
  }

  /**
   * Everything {@link JavaTestFrameworkRunnableState} needs before its parameters may be launched, in the order it needs it. This is
   * the only place that prepares a collecting fork, so no step of it can be forgotten.
   *
   * @return the task that hands the tests to run over to the fork, or null if the fork needs none
   */
  private @Nullable SearchForTestsTask prepareFork(@NotNull JavaTestFrameworkRunnableState<?> state) throws ExecutionException {
    LocalTargetEnvironment environment = new LocalTargetEnvironment(new LocalTargetEnvironmentRequest());
    state.downloadAdditionalDependencies(state.getJavaParameters());
    state.appendForkInfo(state.getEnvironment().getExecutor());
    state.appendRepeatMode();
    JavaParameters parameters = state.getJavaParameters();
    // Ask the forked runner for a dry run; it turns this into the framework-specific config parameters
    // (e.g. Jupiter extension auto-detection) and the interceptor reads it back from the ExtensionContext.
    parameters.getVMParametersList().addProperty(JUnitStarter.DRY_RUN_PROPERTY, "true");
    parameters.setUseDynamicClasspath(myProject);
    state.resolveServerSocketPort(environment);
    return state.createSearchingForTestsTask(environment);
  }

  /**
   * The parameters of the test this collector answers for, out of the service messages a run reported: the invocations of a
   * parameterized method, the dynamic tests of a factory, the parameter sets of a parameterized class. They are the nodes reported
   * under the test itself, so nothing here has to know the uniqueId grammar. An empty list means there is nothing to choose from.
   * <p>
   * The same parameter is reported once per enclosing invocation — a method of a {@code @ParameterizedClass} runs for every parameter
   * set of that class — so the nodes are grouped by what they are presented as, and running such a parameter runs all of its ids.
   */
  public @NotNull List<Parameter> parse(@NotNull List<String> serviceMessages) {
    List<Node> nodes = ContainerUtil.mapNotNull(serviceMessages, JUnitParameterCollector::parseNode);
    Map<String, Node> byId = new HashMap<>();
    for (Node node : nodes) {
      byId.putIfAbsent(node.id(), node);
    }
    List<Node> ofTheTest = ContainerUtil.filter(nodes, node -> isOfTheTest(node, byId));
    Set<String> containers = ContainerUtil.map2Set(ofTheTest, Node::parentId);

    MultiMap<String, String> idsByName = MultiMap.createLinked();
    for (Node node : ofTheTest) {
      // something of the test runs inside this node, so the node is the test itself rather than one of its invocations
      if (containers.contains(node.id())) continue;
      idsByName.putValue(node.name(), node.id());
    }
    // one entry is the test itself rather than an invocation of it: a pruned tree reports the two the same way, and either way
    // there is nothing to choose from
    if (idsByName.size() < 2) return List.of();
    return ContainerUtil.map(idsByName.entrySet(), byName -> new Parameter(List.copyOf(byName.getValue()), byName.getKey()));
  }

  /**
   * Whether the node belongs to the test this collector answers for — it either points there itself, or has no location of its own and
   * hangs under a node that points there, as the dynamic tests of a factory do. The tree is pruned to what runs, so the test's own
   * node is not always reported and the parent may be missing altogether.
   */
  private boolean isOfTheTest(@NotNull Node node, @NotNull Map<String, Node> byId) {
    if (node.locationHint() != null) return myLocationHint.equals(node.locationHint());
    Node parent = byId.get(node.parentId());
    return parent != null && myLocationHint.equals(parent.locationHint());
  }

  /** What the runner reports as the location of the nodes of the test this collector answers for. */
  @VisibleForTesting
  @NotNull String locationHint() {
    return myLocationHint;
  }

  /**
   * The runner reports the test tree as {@code testStarted} and {@code testSuiteStarted} service messages carrying the JUnit
   * uniqueId of the node and of its parent.
   */
  private static @Nullable Node parseNode(@NotNull String text) {
    ServiceMessage message = ServiceMessageUtil.parse(text.trim(), false, false);
    if (!(message instanceof TestStarted) && !(message instanceof TestSuiteStarted)) return null;
    Map<String, String> attributes = message.getAttributes();
    String id = attributes.get("nodeId");
    String parentId = attributes.get("parentNodeId");
    String name = attributes.get("name");
    return id == null || parentId == null || name == null ? null : new Node(id, parentId, name, attributes.get("locationHint"));
  }
}
