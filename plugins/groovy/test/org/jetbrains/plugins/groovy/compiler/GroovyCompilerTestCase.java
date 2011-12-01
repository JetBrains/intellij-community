package org.jetbrains.plugins.groovy.compiler;

import com.intellij.compiler.CompilerManagerImpl;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.compiler.JpsServerManager;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.application.ApplicationConfigurationType;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.DefaultJavaProgramRunner;
import com.intellij.execution.process.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
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
          ApplicationManagerEx.getApplicationEx().doNotSave(false);
          CompilerWorkspaceConfiguration.getInstance(getProject()).USE_COMPILE_SERVER = true;

          JavaAwareProjectJdkTableImpl jdkTable = JavaAwareProjectJdkTableImpl.getInstanceEx();
          Sdk internalJdk = jdkTable.getInternalJdk();
          jdkTable.addJdk(internalJdk);
          final ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
          modifiableModel.setSdk(internalJdk);
          modifiableModel.commit();
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
    final String root = PluginPathManager.getPluginHomePath("groovy") + "/../../lib/";
    final File[] groovyJars = GroovyUtils.getFilesInDirectoryByPattern(root, GroovyConfigUtils.GROOVY_ALL_JAR_PATTERN);
    assert groovyJars.length == 1;
    PsiTestUtil.addLibrary(to, "groovy", root, groovyJars[0].getName());
  }

  @Override
  protected void tearDown() throws Exception {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          if (useJps()) {
            CompilerWorkspaceConfiguration.getInstance(getProject()).USE_COMPILE_SERVER = false;
            ApplicationManagerEx.getApplicationEx().doNotSave(true);
            new WriteCommandAction(getProject()) {
              @Override
              protected void run(Result result) throws Throwable {
                final JavaAwareProjectJdkTableImpl jdkTable = JavaAwareProjectJdkTableImpl.getInstanceEx();
                jdkTable.removeJdk(jdkTable.getInternalJdk());
              }
            }.execute();
            JpsServerManager.getInstance().shutdownServer();
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
    Module module = addModule("dependent");
    addDependency(module, myModule);
    return module;
  }

  protected void addDependency(final Module from, final Module to) {
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
        final ModifiableRootModel model = ModuleRootManager.getInstance(from).getModifiableModel();
        model.addModuleOrderEntry(to);
        model.commit();
      }
    }.execute().getResultObject();

  }
  
  protected Module addModule(final String name) {
    return new WriteCommandAction<Module>(getProject()) {
      @Override
      protected void run(Result<Module> result) throws Throwable {
        final VirtualFile depRoot = myFixture.getTempDirFixture().findOrCreateDir(name);

        final ModifiableModuleModel moduleModel = ModuleManager.getInstance(getProject()).getModifiableModel();
        String moduleName = moduleModel.newModule(depRoot.getPath() + "/" + name + ".iml", StdModuleTypes.JAVA).getName();
        moduleModel.commit();

        final Module dep = ModuleManager.getInstance(getProject()).findModuleByName(moduleName);
        final ModifiableRootModel model = ModuleRootManager.getInstance(dep).getModifiableModel();
        final ContentEntry entry = model.addContentEntry(depRoot);
        entry.addSourceFolder(depRoot, false);
        model.setSdk(ModuleRootManager.getInstance(myModule).getSdk());

        model.commit();
        result.setResult(dep);
      }
    }.execute().getResultObject();
  }

  protected void deleteClassFile(final String className) throws IOException {
    AccessToken token = WriteAction.start();
    try {
      if (useJps()) {
        //noinspection ConstantConditions
        touch(JavaPsiFacade.getInstance(getProject()).findClass(className).getContainingFile().getVirtualFile());
      } else {
        //noinspection ConstantConditions
        findClassFile(className).delete(this);
      }
    }
    finally {
      token.finish();
    }
  }

  @Nullable protected VirtualFile findClassFile(String className) {
    final CompilerModuleExtension extension = ModuleRootManager.getInstance(myModule).getModuleExtension(CompilerModuleExtension.class);
    //noinspection ConstantConditions
    return extension.getCompilerOutputPath().findChild(className + ".class");
  }

  protected static void touch(VirtualFile file) throws IOException {
    file.setBinaryContent(file.contentsToByteArray(), file.getModificationStamp() + 1, file.getTimeStamp() + 1);
    File ioFile = VfsUtil.virtualToIoFile(file);
    assert ioFile.setLastModified(ioFile.lastModified() - 100000);
  }

  protected static void setFileText(final PsiFile file, final String barText) throws IOException {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          VfsUtil.saveText(ObjectUtils.assertNotNull(file.getVirtualFile()), barText);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  protected void setFileName(final PsiFile bar, final String name) {
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
        bar.setName(name);
      }
    }.execute();
  }

  protected List<String> make() {
    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    final ErrorReportingCallback callback = new ErrorReportingCallback(semaphore);
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          if (useJps()) {
            getProject().save();
            File ioFile = VfsUtil.virtualToIoFile(myModule.getModuleFile());
            if (!ioFile.exists()) {
              getProject().save();
              assert ioFile.exists();
            }
          }
          CompilerManager.getInstance(getProject()).make(callback);
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
    final ApplicationConfiguration configuration = new ApplicationConfiguration("app", getProject(), ApplicationConfigurationType.getInstance());
    configuration.setModule(module);
    configuration.setMainClassName(className);
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
            descriptor.dispose();
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

  private static class ErrorReportingCallback implements CompileStatusNotification {
    private final Semaphore mySemaphore;
    private Throwable myError;
    private final List<String> myMessages = new ArrayList<String>();

    public ErrorReportingCallback(Semaphore semaphore) {
      mySemaphore = semaphore;
    }

    @Override
    public void finished(boolean aborted, int errors, int warnings, final CompileContext compileContext) {
      try {
        for (CompilerMessageCategory category : CompilerMessageCategory.values()) {
          for (CompilerMessage message : compileContext.getMessages(category)) {
            final String msg = message.getMessage();
            if (category != CompilerMessageCategory.INFORMATION || !msg.startsWith("Compilation completed successfully")) {
              myMessages.add(category + ": " + msg);
            }
          }
        }
        if (errors > 0) {
          fail("Compiler errors occurred! " + StringUtil.join(myMessages, "\n"));
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

    public List<String> getMessages() {
      return myMessages;
    }
  }
}
