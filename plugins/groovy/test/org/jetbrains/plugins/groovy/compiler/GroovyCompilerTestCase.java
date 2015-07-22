/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.compiler;

import com.intellij.compiler.server.BuildManager;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.application.ApplicationConfigurationType;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.DefaultJavaProgramRunner;
import com.intellij.execution.process.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.CompilerTester;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyFacetUtil;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunConfiguration;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunConfigurationType;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author aalmiray
 * @author peter
 */
public abstract class GroovyCompilerTestCase extends JavaCodeInsightFixtureTestCase {
  @SuppressWarnings("AbstractMethodCallInConstructor") private CompilerTester myCompilerTester;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myCompilerTester = new CompilerTester(myModule);
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) throws Exception {
    moduleBuilder.setLanguageLevel(LanguageLevel.JDK_1_6);
    moduleBuilder.addJdk(IdeaTestUtil.getMockJdk17Path().getPath());
    super.tuneFixture(moduleBuilder);
  }

  @Override
  protected void runTest() throws Throwable {
    if (PlatformTestUtil.COVERAGE_ENABLED_BUILD) return;

    super.runTest();
  }

  protected static void addGroovyLibrary(final Module to) {
    File jar = GroovyFacetUtil.getBundledGroovyJar();
    PsiTestUtil.addLibrary(to, "groovy", jar.getParent(), jar.getName());
  }

  @Override
  protected void tearDown() throws Exception {
    final File systemRoot = BuildManager.getInstance().getBuildSystemDirectory();
    try {
      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        @Override
        public void run() {
          try {
            myCompilerTester.tearDown();
            myCompilerTester = null;
          }
          catch (Exception e) {
            throw new RuntimeException(e);
          }
          finally {
            try {
              GroovyCompilerTestCase.super.tearDown();
            }
            catch (Exception e) {
              throw new RuntimeException(e);
            }
          }
        }
      });
    }
    finally {
      FileUtil.delete(systemRoot);
    }
  }

  protected void setupTestSources() {
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        final ModuleRootManager rootManager = ModuleRootManager.getInstance(myModule);
        final ModifiableRootModel rootModel = rootManager.getModifiableModel();
        final ContentEntry entry = rootModel.getContentEntries()[0];
        entry.removeSourceFolder(entry.getSourceFolders()[0]);
        entry.addSourceFolder(myFixture.getTempDirFixture().findOrCreateDir("src"), false);
        entry.addSourceFolder(myFixture.getTempDirFixture().findOrCreateDir("tests"), true);
        rootModel.commit();
      }
    }.execute();
  }

  protected Module addDependentModule() {
    Module module = addModule("dependent", true);
    ModuleRootModificationUtil.addDependency(module, myModule);
    return module;
  }

  protected Module addModule(final String name, final boolean withSource) {
    return new WriteCommandAction<Module>(getProject()) {
      @Override
      protected void run(@NotNull Result<Module> result) throws Throwable {
        final VirtualFile depRoot = myFixture.getTempDirFixture().findOrCreateDir(name);

        final ModifiableModuleModel moduleModel = ModuleManager.getInstance(getProject()).getModifiableModel();
        String moduleName = moduleModel.newModule(depRoot.getPath() + "/" + name + ".iml", StdModuleTypes.JAVA.getId()).getName();
        moduleModel.commit();

        final Module dep = ModuleManager.getInstance(getProject()).findModuleByName(moduleName);
        ModuleRootModificationUtil.setModuleSdk(dep, ModuleRootManager.getInstance(myModule).getSdk());
        if (withSource) {
          PsiTestUtil.addSourceRoot(dep, depRoot);
        } else {
          PsiTestUtil.addContentRoot(dep, depRoot);
        }
        IdeaTestUtil.setModuleLanguageLevel(dep, LanguageLevelModuleExtensionImpl.getInstance(myModule).getLanguageLevel());

        result.setResult(dep);
      }
    }.execute().getResultObject();
  }

  protected void deleteClassFile(final String className) throws IOException {
    myCompilerTester.deleteClassFile(className);
  }

  @Nullable
  protected VirtualFile findClassFile(String className) {
    return findClassFile(className, myModule);
  }

  @Nullable
  protected VirtualFile findClassFile(String className, Module module) {
    return myCompilerTester.findClassFile(className, module);
  }

  protected void touch(VirtualFile file) throws IOException {
    myCompilerTester.touch(file);
  }

  protected void setFileText(final PsiFile file, final String barText) throws IOException {
    myCompilerTester.setFileText(file, barText);
  }

  protected void setFileName(final PsiFile bar, final String name) {
    myCompilerTester.setFileName(bar, name);
  }

  protected List<CompilerMessage> make() {
    return myCompilerTester.make();
  }

  protected List<CompilerMessage> rebuild() {
    return myCompilerTester.rebuild();
  }

  protected List<CompilerMessage> compileModule(final Module module) {
    return myCompilerTester.compileModule(module);
  }

  protected List<CompilerMessage> compileFiles(final VirtualFile... files) {
    return myCompilerTester.compileFiles(files);
  }

  protected void assertOutput(String className, String output) throws ExecutionException {
    assertOutput(className, output, myModule);
  }

  protected void assertOutput(String className, String expected, final Module module) throws ExecutionException {
    final StringBuffer sb = new StringBuffer();
    ProcessHandler process = runProcess(className, module, DefaultRunExecutor.class, new ProcessAdapter() {
      @Override
      public void onTextAvailable(ProcessEvent event, Key outputType) {
        if (ProcessOutputTypes.SYSTEM != outputType) {
          sb.append(event.getText());
        }
      }
    }, ProgramRunner.PROGRAM_RUNNER_EP.findExtension(DefaultJavaProgramRunner.class));
    process.waitFor();
    assertEquals(expected.trim(), StringUtil.convertLineSeparators(sb.toString().trim()));
  }

  protected ProcessHandler runProcess(String className,
                                      Module module,
                                      final Class<? extends Executor> executorClass,
                                      final ProcessListener listener, final ProgramRunner runner) throws ExecutionException {
    final ApplicationConfiguration configuration = createApplicationConfiguration(className, module);
    return runConfiguration(executorClass, listener, runner, configuration);
  }

  protected ProcessHandler runConfiguration(Class<? extends Executor> executorClass,
                                          final ProcessListener listener,
                                          ProgramRunner runner,
                                          RunProfile configuration) throws ExecutionException {
    final Executor executor = Executor.EXECUTOR_EXTENSION_NAME.findExtension(executorClass);
    final ExecutionEnvironment environment = new ExecutionEnvironmentBuilder(getProject(), executor).runProfile(configuration).build();
    final Semaphore semaphore = new Semaphore();
    semaphore.down();

    final AtomicReference<ProcessHandler> processHandler = new AtomicReference<ProcessHandler>();
    runner.execute(environment, new ProgramRunner.Callback() {
      @Override
      public void processStarted(final RunContentDescriptor descriptor) {
        if (descriptor == null) {
          throw new AssertionError("Null descriptor!");
        }
        disposeOnTearDown(new Disposable() {
          @Override
          public void dispose() {
            Disposer.dispose(descriptor);
          }
        });
        final ProcessHandler handler = descriptor.getProcessHandler();
        assert handler != null;
        handler.addProcessListener(listener);
        processHandler.set(handler);
        semaphore.up();
      }
    });
    if (!semaphore.waitFor(20000)) {
      fail("Process took too long");
    }
    return processHandler.get();
  }

  protected ApplicationConfiguration createApplicationConfiguration(String className, Module module) {
    final ApplicationConfiguration configuration =
      new ApplicationConfiguration("app", getProject(), ApplicationConfigurationType.getInstance());
    configuration.setModule(module);
    configuration.setMainClassName(className);
    return configuration;
  }

  protected GroovyScriptRunConfiguration createScriptConfiguration(String scriptPath, Module module) {
    final GroovyScriptRunConfiguration configuration =
      new GroovyScriptRunConfiguration("app", getProject(), GroovyScriptRunConfigurationType.getInstance().getConfigurationFactories()[0]);
    configuration.setModule(module);
    configuration.setScriptPath(scriptPath);
    return configuration;
  }

}
