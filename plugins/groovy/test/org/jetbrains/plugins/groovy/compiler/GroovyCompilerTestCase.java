/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.compiler.CompilerManagerImpl;
import com.intellij.compiler.CompilerTestUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.application.ApplicationConfigurationType;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.DefaultJavaProgramRunner;
import com.intellij.execution.process.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunConfiguration;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunConfigurationType;
import org.jetbrains.plugins.groovy.util.GroovyUtils;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author peter
 */
public abstract class GroovyCompilerTestCase extends JavaCodeInsightFixtureTestCase {
  private TempDirTestFixture myMainOutput;

  protected abstract boolean useJps();

  @Override
  protected void setUp() throws Exception {
    myMainOutput = new TempDirTestFixtureImpl();
    myMainOutput.setUp();
    super.setUp();
    getProject().getComponent(GroovyCompilerLoader.class).projectOpened();
    CompilerManagerImpl.testSetup();

    new WriteCommandAction(getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
        //noinspection ConstantConditions
        CompilerProjectExtension.getInstance(getProject()).setCompilerOutputUrl(myMainOutput.findOrCreateDir("out").getUrl());
        if (useJps()) {
          CompilerTestUtil.enableExternalCompiler(getProject());
          ModuleRootModificationUtil.setModuleSdk(myModule, JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk());
        }
        else {
          CompilerTestUtil.disableExternalCompiler(getProject());
        }
      }
    }.execute();
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) throws Exception {
    moduleBuilder.setMockJdkLevel(JavaModuleFixtureBuilder.MockJdkLevel.jdk15);
    moduleBuilder.addJdk(JavaSdkImpl.getMockJdk17Path().getPath());
    super.tuneFixture(moduleBuilder);
  }

  protected static void addGroovyLibrary(final Module to) {
    File jar = GroovyUtils.getBundledGroovyJar();
    PsiTestUtil.addLibrary(to, "groovy", jar.getParent(), jar.getName());
  }

  @Override
  protected void tearDown() throws Exception {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          if (useJps()) {
            CompilerTestUtil.disableExternalCompiler(getProject());
          }

          myMainOutput.tearDown();
          myMainOutput = null;
          GroovyCompilerTestCase.super.tearDown();
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  protected void setupTestSources() {
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
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
      protected void run(Result<Module> result) throws Throwable {
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
        IdeaTestUtil.setModuleLanguageLevel(dep, LanguageLevelModuleExtension.getInstance(myModule).getLanguageLevel());

        result.setResult(dep);
      }
    }.execute().getResultObject();
  }

  protected void deleteClassFile(final String className) throws IOException {
    AccessToken token = WriteAction.start();
    try {
      if (useJps()) {
        //noinspection ConstantConditions
        touch(myFixture.getJavaFacade().findClass(className).getContainingFile().getVirtualFile());
      }
      else {
        //noinspection ConstantConditions
        findClassFile(className).delete(this);
      }
    }
    finally {
      token.finish();
    }
  }

  @Nullable
  protected VirtualFile findClassFile(String className) {
    return findClassFile(className, myModule);
  }

  @Nullable
  protected VirtualFile findClassFile(String className, Module module) {
    //noinspection ConstantConditions
    VirtualFile path = ModuleRootManager.getInstance(module).getModuleExtension(CompilerModuleExtension.class).getCompilerOutputPath();
    path.getChildren();
    assert path != null;
    path.refresh(false, true);
    return path.findChild(className + ".class");
  }

  protected static void touch(VirtualFile file) throws IOException {
    file.setBinaryContent(file.contentsToByteArray(), file.getModificationStamp() + 1, file.getTimeStamp() + 1);
    File ioFile = VfsUtil.virtualToIoFile(file);
    assert ioFile.setLastModified(ioFile.lastModified() - 100000);
    file.refresh(false, false);
  }

  protected static void setFileText(final PsiFile file, final String barText) throws IOException {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          final VirtualFile virtualFile = file.getVirtualFile();
          VfsUtil.saveText(ObjectUtils.assertNotNull(virtualFile), barText);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
    touch(file.getVirtualFile());
  }

  protected void setFileName(final PsiFile bar, final String name) {
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
        bar.setName(name);
      }
    }.execute();
  }

  protected List<CompilerMessage> make() {
    return runCompiler(new Consumer<ErrorReportingCallback>() {
      @Override
      public void consume(ErrorReportingCallback callback) {
        CompilerManager.getInstance(getProject()).make(callback);
      }
    });
  }

  protected List<CompilerMessage> rebuild() {
    return runCompiler(new Consumer<ErrorReportingCallback>() {
      @Override
      public void consume(ErrorReportingCallback callback) {
        CompilerManager.getInstance(getProject()).rebuild(callback);
      }
    });
  }

  protected List<CompilerMessage> compileModule(final Module module) {
    return runCompiler(new Consumer<ErrorReportingCallback>() {
      @Override
      public void consume(ErrorReportingCallback callback) {
        CompilerManager.getInstance(getProject()).compile(module, callback);
      }
    });
  }

  protected List<CompilerMessage> compileFiles(final VirtualFile... files) {
    return runCompiler(new Consumer<ErrorReportingCallback>() {
      @Override
      public void consume(ErrorReportingCallback callback) {
        CompilerManager.getInstance(getProject()).compile(files, callback);
      }
    });
  }

  private List<CompilerMessage> runCompiler(final Consumer<ErrorReportingCallback> runnable) {
    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    final ErrorReportingCallback callback = new ErrorReportingCallback(semaphore);
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          if (useJps()) {
            getProject().save();
            CompilerTestUtil.saveSdkTable();
            File ioFile = VfsUtil.virtualToIoFile(myModule.getModuleFile());
            if (!ioFile.exists()) {
              getProject().save();
              assert ioFile.exists() : "File does not exist: " + ioFile.getPath();
            }
          }
          runnable.consume(callback);
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });

    //tests run in awt
    while (!semaphore.waitFor(100)) {
      if (SwingUtilities.isEventDispatchThread()) {
        UIUtil.dispatchAllInvocationEvents();
      }
    }
    callback.throwException();
    return callback.getMessages();
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
    final ExecutionEnvironment environment = new ExecutionEnvironment(configuration, getProject(),
                                                                      new RunnerSettings<JDOMExternalizable>(null, null), null, null);
    final Semaphore semaphore = new Semaphore();
    semaphore.down();

    final AtomicReference<ProcessHandler> processHandler = new AtomicReference<ProcessHandler>();
    runner.execute(executor, environment, new ProgramRunner.Callback() {
      @Override
      public void processStarted(final RunContentDescriptor descriptor) {
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
    semaphore.waitFor();
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

  private static class ErrorReportingCallback implements CompileStatusNotification {
    private final Semaphore mySemaphore;
    private Throwable myError;
    private final List<CompilerMessage> myMessages = new ArrayList<CompilerMessage>();

    public ErrorReportingCallback(Semaphore semaphore) {
      mySemaphore = semaphore;
    }

    @Override
    public void finished(boolean aborted, int errors, int warnings, final CompileContext compileContext) {
      try {
        for (CompilerMessageCategory category : CompilerMessageCategory.values()) {
          CompilerMessage[] messages = compileContext.getMessages(category);
          for (CompilerMessage message : messages) {
            if (category != CompilerMessageCategory.INFORMATION || !message.getMessage().startsWith("Compilation completed successfully")) {
              myMessages.add(message);
            }
          }
        }
        assertFalse("Code did not compile!", aborted);
      }
      catch (Throwable t) {
        myError = t;
      }
      finally {
        mySemaphore.up();
      }
    }

    void throwException() {
      if (myError != null) {
        throw new RuntimeException(myError);
      }
    }

    public List<CompilerMessage> getMessages() {
      return myMessages;
    }
  }
}
