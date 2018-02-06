// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.junit4;

import com.intellij.execution.*;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.DefaultJavaProgramRunner;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.TestObject;
import com.intellij.execution.process.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.SearchForTestsTask;
import com.intellij.java.execution.BaseConfigurationTestCase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.ui.UIUtil;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager;
import org.jetbrains.idea.maven.aether.ProgressConsumer;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;

import java.io.File;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class JUnitAbstractIntegrationTest extends BaseConfigurationTestCase {
  public static ProcessOutput doStartTestsProcess(RunConfiguration configuration) throws ExecutionException {
    Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    Project project = configuration.getProject();
    RunnerAndConfigurationSettingsImpl
      settings = new RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(project), configuration, false);
    ExecutionEnvironment
      environment = new ExecutionEnvironment(executor, ProgramRunnerUtil.getRunner(DefaultRunExecutor.EXECUTOR_ID, settings), settings, project);
    TestObject state = ((JUnitConfiguration)configuration).getState(executor, environment);
    state.appendRepeatMode();

    JavaParameters parameters = state.getJavaParameters();
    parameters.setUseDynamicClasspath(project);
    ExecutionResult result = state.execute(new DefaultRunExecutor(), DefaultJavaProgramRunner.getInstance());
    ProcessHandler handler = result.getProcessHandler();
    ProcessOutput processOutput = new ProcessOutput();
    handler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        String text = event.getText();
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
        }
      }
    });
    handler.startNotify();
    while (!handler.waitFor(100)) {
      UIUtil.dispatchAllInvocationEvents();
    }
    handler.destroyProcess();
    return processOutput;
  }

  protected void addLibs(Module module,
                         JpsMavenRepositoryLibraryDescriptor descriptor,
                         ArtifactRepositoryManager repoManager) throws Exception {
    
    Collection<File> files = repoManager.resolveDependency(descriptor.getGroupId(), descriptor.getArtifactId(), descriptor.getVersion(),
                                                           descriptor.isIncludeTransitiveDependencies());
    for (File artifact : files) {
      VirtualFile libJarLocal = LocalFileSystem.getInstance().findFileByIoFile(artifact);
      assertNotNull(libJarLocal);
      VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(libJarLocal);
      ModuleRootModificationUtil.addModuleLibrary(module, jarRoot.getUrl());
    }
  }

  protected ArtifactRepositoryManager getRepoManager() {
    final String userHome = System.getProperty("user.home", null);
    final File localRepo = userHome != null ? new File(userHome, ".m2/repository") : new File(".m2/repository");

    return new ArtifactRepositoryManager(
      localRepo,
      Collections.singletonList(ArtifactRepositoryManager.createRemoteRepository("maven", "http://maven.labs.intellij.net/repo1")),
      ProgressConsumer.DEAF
    );
  }

  public static class ProcessOutput {
    public List<String> out = new ArrayList<>();
    public List<String> err = new ArrayList<>();
    public List<String> sys = new ArrayList<>();
    public List<ServiceMessage> messages = new ArrayList<>();
  }
}
