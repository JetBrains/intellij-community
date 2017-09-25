/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.junit4;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.TestObject;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.SearchForTestsTask;
import com.intellij.java.execution.BaseConfigurationTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
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

    JavaParameters parameters = state.getJavaParameters();
    parameters.setUseDynamicClasspath(project);
    GeneralCommandLine commandLine = parameters.toCommandLine();

    OSProcessHandler process = new OSProcessHandler(commandLine);
    final SearchForTestsTask searchForTestsTask = state.createSearchingForTestsTask();
    if (searchForTestsTask != null) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
                                                                          searchForTestsTask.run(new EmptyProgressIndicator());
                                                                          searchForTestsTask.onSuccess();
                                                                        },
                                                                        "", false, project, null);
    }

    ProcessOutput processOutput = new ProcessOutput();
    process.addProcessListener(new ProcessAdapter() {
      @Override
      public void startNotified(@NotNull ProcessEvent event) {
        if (searchForTestsTask != null) {
          searchForTestsTask.finish();
        }
      }

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
    process.startNotify();
    process.waitFor();
    process.destroyProcess();

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

  static class ProcessOutput {
    List<String> out = new ArrayList<>();
    List<String> err = new ArrayList<>();
    List<String> sys = new ArrayList<>();
    List<ServiceMessage> messages = new ArrayList<>();
  }
}
