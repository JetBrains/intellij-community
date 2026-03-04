// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit5;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.JavaTestConfigurationBase;
import com.intellij.execution.JavaTestFrameworkRunnableState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.target.local.LocalTargetEnvironment;
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest;
import com.intellij.execution.testframework.SearchForTestsTask;
import com.intellij.execution.testframework.sm.runner.OutputEventSplitter;
import com.intellij.java.execution.AbstractTestFrameworkCompilingIntegrationTest;
import com.intellij.java.execution.AbstractTestFrameworkIntegrationTest;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.rt.junit.JUnitStarter;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import jetbrains.buildServer.messages.serviceMessages.TestFailed;
import jetbrains.buildServer.messages.serviceMessages.TestFinished;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;

import java.text.ParseException;
import java.util.List;

public class JUnitVersionSwitchingIntegrationTest extends AbstractTestFrameworkCompilingIntegrationTest {

  @Override
  protected String getTestContentRoot() {
    return VfsUtilCore.pathToUrl(
      PlatformTestUtil.getCommunityPath() + "/plugins/junit5_rt_tests/testData/integration/versionSwitching"
    );
  }

  @Override
  protected void setupModule() throws Exception {
    super.setupModule();
    ModuleRootModificationUtil.updateModel(myModule, model -> model.addContentEntry(getTestContentRoot())
      .addSourceFolder(getTestContentRoot() + "/test", true));
    ArtifactRepositoryManager repoManager = getRepoManager();
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("junit", "junit", "3.8.2", false, List.of()), repoManager);
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", "5.13.0", false, List.of()),
                 repoManager);
  }

  public void testFallbackToJUnit3WhenJUnit4NotOnClasspath() throws Exception {
    GlobalSearchScope moduleScope = GlobalSearchScope.moduleWithLibrariesScope(myModule);

    assertNull("org.junit.Test must not be on the classpath to trigger JUNIT4→JUNIT3 fallback",
               JavaPsiFacade.getInstance(myProject).findClass("org.junit.Test", moduleScope));

    PsiClass testClass = JavaPsiFacade.getInstance(myProject).findClass("org.example.SimpleJUnit3Test",
                                                                        GlobalSearchScope.projectScope(myProject));
    assertNotNull("SimpleJUnit3Test not found", testClass);

    RunConfiguration configuration = createConfiguration(testClass);
    assertNotNull(configuration);

    ProcessOutput output = doStartTestsProcessWithExtraJUnitParam(configuration, JUnitStarter.JUNIT4_PARAMETER);
    assertEmpty(output.err);

    assertTrue(output.sys.toString().contains("-junit4"));
    assertTrue(ContainerUtil.exists(output.out, s -> s.contains("junit4.classes.present=false")));

    List<ServiceMessage> messages = output.messages;
    assertEquals(2, messages.stream().filter(TestFinished.class::isInstance).count());
    assertEquals(0, messages.stream().filter(TestFailed.class::isInstance).count());
  }

  public void testJUnit5PlatformMissingShowsError() throws Exception {
    GlobalSearchScope moduleScope = GlobalSearchScope.moduleWithLibrariesScope(myModule);

    assertNull("TestEngine must not be on the classpath to trigger JUnit Platform missing error",
               JavaPsiFacade.getInstance(myProject).findClass("org.junit.platform.engine.TestEngine", moduleScope));

    PsiClass testClass = JavaPsiFacade.getInstance(myProject)
      .findClass("org.example.SimpleJUnit5Test", GlobalSearchScope.projectScope(myProject));
    assertNotNull("SimpleJUnit5Test not found", testClass);

    RunConfiguration configuration = createConfiguration(testClass);
    assertNotNull(configuration);

    ProcessOutput output = doStartTestsProcessWithExtraJUnitParam(configuration, JUnitStarter.JUNIT5_PARAMETER);

    assertTrue(output.sys.toString().contains("-junit5"));
    assertTrue("stderr must contain the 'JUnit Platform is not available' error message",
               ContainerUtil.exists(output.err, s -> s.contains("JUnit Platform is not available")));
    assertTrue("No test messages should be emitted when JUnit Platform is missing",
               output.messages.isEmpty());
  }

  public void testJUnit6FallsBackToJUnit5WhenJUnit6NotAvailable() throws Exception {
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", "5.13.0"), getRepoManager());

    PsiClass testClass = JavaPsiFacade.getInstance(myProject)
      .findClass("org.example.SimpleJUnit5Test", GlobalSearchScope.projectScope(myProject));
    assertNotNull("SimpleJUnit5Test not found", testClass);

    RunConfiguration configuration = createConfiguration(testClass);
    assertNotNull(configuration);

    ProcessOutput output = doStartTestsProcessWithExtraJUnitParam(configuration, JUnitStarter.JUNIT6_PARAMETER);
    assertEmpty(output.err);

    assertTrue(output.sys.toString().contains("-junit6"));
    assertTrue("Test output must report that JUnit6 classes are absent (junit6.classes.present=false)",
               ContainerUtil.exists(output.out, s -> s.contains("junit6.classes.present=false")));

    List<ServiceMessage> messages = output.messages;
    assertEquals("Test should finish under JUNIT5 fallback runner", 1,
                 messages.stream().filter(TestFinished.class::isInstance).count());
    assertEquals("Test should not fail", 0,
                 messages.stream().filter(TestFailed.class::isInstance).count());
  }

  private static ProcessOutput doStartTestsProcessWithExtraJUnitParam(RunConfiguration config, String extraParam)
    throws ExecutionException {
    Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    Project project = config.getProject();
    RunnerAndConfigurationSettingsImpl settings =
      new RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(project), config, false);
    ExecutionEnvironment environment = new ExecutionEnvironment(
      executor,
      ProgramRunner.getRunner(DefaultRunExecutor.EXECUTOR_ID, settings.getConfiguration()),
      settings,
      project
    );

    JavaTestFrameworkRunnableState<?> state = ((JavaTestConfigurationBase)config).getState(executor, environment);
    state.downloadAdditionalDependencies(state.getJavaParameters());
    state.appendForkInfo(executor);
    state.appendRepeatMode();

    JavaParameters parameters = state.getJavaParameters();
    parameters.getProgramParametersList().add(extraParam);

    parameters.setUseDynamicClasspath(project);
    state.resolveServerSocketPort(new LocalTargetEnvironment(new LocalTargetEnvironmentRequest()));
    GeneralCommandLine commandLine = parameters.toCommandLine();

    OSProcessHandler process = new OSProcessHandler(commandLine);

    SearchForTestsTask searchForTestsTask =
      state.createSearchingForTestsTask(new LocalTargetEnvironment(new LocalTargetEnvironmentRequest()));
    if (searchForTestsTask != null) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
        searchForTestsTask.run(new EmptyProgressIndicator());
        ApplicationManager.getApplication().invokeLater(searchForTestsTask::onSuccess);
      }, "", false, project, null);
    }

    AbstractTestFrameworkIntegrationTest.ProcessOutput processOutput =
      new AbstractTestFrameworkIntegrationTest.ProcessOutput(process);

    process.addProcessListener(new ProcessListener() {
      final SearchForTestsTask task = searchForTestsTask;
      final OutputEventSplitter splitter = new OutputEventSplitter() {
        @Override
        public void onTextAvailable(@NotNull String text, @NotNull Key<?> outputType) {
          if (StringUtil.isEmptyOrSpaces(text)) return;
          try {
            if (outputType == ProcessOutputTypes.STDOUT) {
              ServiceMessage serviceMessage = ServiceMessage.parse(text.trim());
              if (serviceMessage == null) {
                processOutput.out.add(text);
              }
              else {
                processOutput.messages.add(serviceMessage);
              }
            }
            if (outputType == ProcessOutputTypes.SYSTEM) {
              processOutput.sys.add(text);
            }
            if (outputType == ProcessOutputTypes.STDERR) {
              processOutput.err.add(text);
            }
          }
          catch (ParseException e) {
            e.printStackTrace();
            System.err.println(text);
          }
        }
      };

      @Override
      public void startNotified(@NotNull ProcessEvent event) {
        if (task != null) task.finish();
      }

      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        splitter.flush();
      }

      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        splitter.process(event.getText(), outputType);
      }
    });

    process.startNotify();

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    process.waitFor(10000);
    process.destroyProcess();

    return processOutput;
  }
}
