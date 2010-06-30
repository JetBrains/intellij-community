package org.jetbrains.plugins.groovy.compiler;

import com.intellij.compiler.CompilerManagerImpl;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.application.ApplicationConfigurationType;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.DefaultJavaProgramRunner;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.util.GroovyUtils;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public abstract class GroovyCompilerTestCase extends JavaCodeInsightFixtureTestCase {
  private TempDirTestFixture myMainOutput;

  @Override
  protected void setUp() throws Exception {
    myMainOutput = new TempDirTestFixtureImpl();
    myMainOutput.setUp();
    super.setUp();
    getProject().getComponent(GroovyCompilerLoader.class).projectOpened();
    CompilerManagerImpl.testSetup();

    new WriteCommandAction(getProject()) {
      protected void run(Result result) throws Throwable {
        CompilerProjectExtension.getInstance(getProject()).setCompilerOutputUrl(myMainOutput.findOrCreateDir("out").getUrl());
      }
    }.execute();

    addGroovyLibrary(myModule, getName().contains("1_7"));

  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) throws Exception {
    moduleBuilder.setMockJdkLevel(JavaModuleFixtureBuilder.MockJdkLevel.jdk15);
    super.tuneFixture(moduleBuilder);
  }

  protected static void addGroovyLibrary(final Module to, boolean version17) {
    final String root = version17 ? TestUtils.getRealGroovy1_7LibraryHome() : PathManager.getHomePath() + "/community/lib/";
    final File[] groovyJars = GroovyUtils.getFilesInDirectoryByPattern(root, GroovyConfigUtils.GROOVY_ALL_JAR_PATTERN);
    assert groovyJars.length == 1;
    PsiTestUtil.addLibrary(to, "groovy", root, groovyJars[0].getName());
  }

  @Override
  protected void tearDown() throws Exception {
    myMainOutput.tearDown();
    myMainOutput = null;
    super.tearDown();
  }

  protected void setupTestSources() {
    new WriteCommandAction(getProject()) {
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
    Module dep = new WriteCommandAction<Module>(getProject()) {
      @Override
      protected void run(Result<Module> result) throws Throwable {
        final ModifiableModuleModel moduleModel = ModuleManager.getInstance(getProject()).getModifiableModel();
        moduleModel.newModule("dependent/dependent.iml", StdModuleTypes.JAVA);
        moduleModel.commit();

        final Module dep = ModuleManager.getInstance(getProject()).findModuleByName("dependent");
        final ModifiableRootModel model = ModuleRootManager.getInstance(dep).getModifiableModel();
        model.addModuleOrderEntry(myModule);
        final VirtualFile depRoot = myFixture.getTempDirFixture().getFile("dependent");
        final ContentEntry entry = model.addContentEntry(depRoot);
        entry.addSourceFolder(depRoot, false);
        model.setSdk(ModuleRootManager.getInstance(myModule).getSdk());

        //model.getModuleExtension(CompilerModuleExtension.class).inheritCompilerOutputPath(true);

        model.commit();
        result.setResult(dep);
      }
    }.execute().getResultObject();
    return dep;
  }

  protected void deleteClassFile(final String className) throws IOException {
    new WriteCommandAction(getProject()) {
      protected void run(Result result) throws Throwable {
        final CompilerModuleExtension extension = ModuleRootManager.getInstance(myModule).getModuleExtension(CompilerModuleExtension.class);
        //noinspection ConstantConditions
        extension.getCompilerOutputPath().findChild(className + ".class").delete(this);
      }
    }.execute();
  }

  protected static void touch(VirtualFile file) throws IOException {
    file.setBinaryContent(file.contentsToByteArray(), file.getModificationStamp() + 1, file.getTimeStamp() + 1);
  }

  protected static void setFileText(final PsiFile file, final String barText) throws IOException {
    Runnable runnable = new Runnable() {
      public void run() {
        try {
          VfsUtil.saveText(ObjectUtils.assertNotNull(file.getVirtualFile()), barText);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    };
    ApplicationManager.getApplication().invokeAndWait(runnable, ModalityState.NON_MODAL);

  }

  protected void setFileName(final PsiFile bar, final String name) {
    new WriteCommandAction(getProject()) {
      protected void run(Result result) throws Throwable {
        bar.setName(name);
      }
    }.execute();
  }

  protected List<String> make() {
    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    final ErrorReportingCallback callback = new ErrorReportingCallback(semaphore);
    CompilerManager.getInstance(getProject()).make(callback);
    semaphore.waitFor();
    callback.throwException();
    return callback.getMessages();
  }

  protected void assertOutput(String className, String output) throws ExecutionException {
    assertOutput(className, output, myModule);
  }

  protected void assertOutput(String className, String output, final Module module) throws ExecutionException {
    final ApplicationConfiguration configuration =
      new ApplicationConfiguration("app", getProject(), ApplicationConfigurationType.getInstance());
    configuration.setModule(module);
    configuration.setMainClassName(className);
    final DefaultRunExecutor extension = Executor.EXECUTOR_EXTENSION_NAME.findExtension(DefaultRunExecutor.class);
    final ExecutionEnvironment environment = new ExecutionEnvironment(configuration, getProject(),
                                                                      new RunnerSettings<JDOMExternalizable>(null, null), null, null);
    final DefaultJavaProgramRunner runner = ProgramRunner.PROGRAM_RUNNER_EP.findExtension(DefaultJavaProgramRunner.class);
    final StringBuffer sb = new StringBuffer();
    final Semaphore semaphore = new Semaphore();
    semaphore.down();

    runner.execute(extension, environment, new ProgramRunner.Callback() {
      public void processStarted(final RunContentDescriptor descriptor) {
        Disposer.register(myFixture.getProject(), new Disposable() {
          public void dispose() {
            descriptor.dispose();
          }
        });
        final ProcessHandler handler = descriptor.getProcessHandler();
        assert handler != null;
        handler.addProcessListener(new ProcessAdapter() {
          public void onTextAvailable(ProcessEvent event, Key outputType) {
            if (ProcessOutputTypes.SYSTEM != outputType) {
              sb.append(event.getText());
            }
          }

          @Override
          public void processTerminated(ProcessEvent event) {
            semaphore.up();
          }
        });
      }
    });
    semaphore.waitFor();
    assertEquals(output.trim(), StringUtil.convertLineSeparators(sb.toString().trim()));
  }

  private static class ErrorReportingCallback implements CompileStatusNotification {
    private final Semaphore mySemaphore;
    private Throwable myError;
    private final List<String> myMessages = new ArrayList<String>();

    public ErrorReportingCallback(Semaphore semaphore) {
      mySemaphore = semaphore;
    }

    public void finished(boolean aborted, int errors, int warnings, final CompileContext compileContext) {
      try {
        assertFalse("Code did not compile!", aborted);
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
