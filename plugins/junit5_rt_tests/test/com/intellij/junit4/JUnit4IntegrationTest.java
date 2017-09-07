/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.execution.Executor;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
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
import com.intellij.java.execution.BaseConfigurationTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager;
import org.jetbrains.idea.maven.aether.ProgressConsumer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

@RunWith(Parameterized.class)
public class JUnit4IntegrationTest extends BaseConfigurationTestCase {

  public static final String CLASS_NAME = "a.Test1";
  private static final String METHOD_NAME = "simple";

  @Rule public final TestName myNameRule = new TestName();

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(createParams("4.12"),
                         createParams("4.11"),
                         createParams("4.10"),
                         createParams("4.9"),
                         createParams("4.8.2"),
                         createParams("4.5"),
                         createParams("4.4")
    );
  }

  private static Object[] createParams(final String version) {
    return new Object[]{version};
  }

  @Parameterized.Parameter
  public String myJUnitVersion;

  @Before
  public void before() throws Throwable {
    EdtTestUtil.runInEdtAndWait(() -> {
      setUp();
      Module module = createEmptyModule();
      String communityPath = PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/');
      String methodName = myNameRule.getMethodName();
      methodName = methodName.substring(0, methodName.indexOf("["));
      String testDataPath = communityPath + File.separator + "plugins" + File.separator + "junit5_rt_tests" +
                            File.separator + "testData" + File.separator + "integration" + File.separator + methodName;

      final String userHome = System.getProperty("user.home", null);
      final File localRepo = userHome != null ? new File(userHome, ".m2/repository") : new File(".m2/repository");

      final ArtifactRepositoryManager repoManager = new ArtifactRepositoryManager(
        localRepo,
        Collections.singletonList(ArtifactRepositoryManager.createRemoteRepository("maven", "http://maven.labs.intellij.net/repo1")),
        ProgressConsumer.DEAF
      );

      final Collection<File> files = repoManager.resolveDependency("junit", "junit", myJUnitVersion, true);
      for (File artifact : files) {
        ModuleRootModificationUtil.addModuleLibrary(module, VfsUtilCore.pathToUrl(artifact.getPath()));
      }

      ModuleRootModificationUtil.setModuleSdk(module, JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk());
      ModuleRootModificationUtil.updateModel(module, model -> {
        ContentEntry contentEntry = model.addContentEntry(VfsUtilCore.pathToUrl(testDataPath));
        contentEntry.addSourceFolder(VfsUtilCore.pathToUrl(testDataPath + File.separator + "test"), true);
        CompilerModuleExtension moduleExtension = model.getModuleExtension(CompilerModuleExtension.class);
        moduleExtension.inheritCompilerOutputPath(false);
        moduleExtension.setCompilerOutputPathForTests(VfsUtilCore.pathToUrl(testDataPath + File.separator + "out"));
      });
    });
  }

  @After
  public void after() throws Throwable {
    EdtTestUtil.runInEdtAndWait(() -> {
      tearDown();
    });
  }

  @Override
  public String getName() {
    return myNameRule.getMethodName();
  }

  @Test
  public void ignoredTestMethod() throws Throwable {
    EdtTestUtil.runInEdtAndWait(() -> {
      PsiClass psiClass = findClass(getModule1(), CLASS_NAME);
      assertNotNull(psiClass);
      PsiMethod testMethod = psiClass.findMethodsByName(METHOD_NAME, false)[0];
      JUnitConfiguration configuration = createConfiguration(testMethod);
      Executor executor = DefaultRunExecutor.getRunExecutorInstance();
      RunnerAndConfigurationSettingsImpl settings = new RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(getProject()), configuration, false);
      ExecutionEnvironment environment = new ExecutionEnvironment(executor, ProgramRunnerUtil.getRunner(DefaultRunExecutor.EXECUTOR_ID, settings), settings, getProject());
      TestObject state = configuration.getState(executor, environment);
      JavaParameters parameters = state.getJavaParameters();
      parameters.setUseDynamicClasspath(getProject());
      GeneralCommandLine commandLine = parameters.toCommandLine();
      StringBuffer buf = new StringBuffer();
      StringBuffer err = new StringBuffer();
      OSProcessHandler process = new OSProcessHandler(commandLine);
      process.addProcessListener(new ProcessAdapter() {
        @Override
        public void onTextAvailable(ProcessEvent event, Key outputType) {
          String text = event.getText();
          try {
            if (outputType == ProcessOutputTypes.STDOUT && !text.isEmpty() && ServiceMessage.parse(text.trim()) == null) {
              buf.append(text);
            }

            if (outputType == ProcessOutputTypes.STDERR) {
              err.append(text);
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

      String testOutput = buf.toString();
      assertEmpty(err.toString());
      switch (myJUnitVersion) {
        case "4.4": case "4.5": break; //shouldn't work for old versions
        default:
          assertTrue(testOutput, testOutput.contains("Test1"));
      }
    });
  }


}
