/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang;

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
import com.intellij.ide.DataManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl;
import com.intellij.util.concurrency.Semaphore;
import junit.framework.AssertionFailedError;
import org.jetbrains.plugins.groovy.compiler.GroovyCompilerLoader;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.util.GroovyUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

/**
 * @author peter
 */
public class GroovyCompilerTest extends JavaCodeInsightFixtureTestCase {
  private TempDirTestFixture myMainOutput;

  @Override
  protected void setUp() throws Exception {
    myMainOutput = new TempDirTestFixtureImpl();
    myMainOutput.setUp();
    super.setUp();
    getProject().getComponent(GroovyCompilerLoader.class).projectOpened();
    CompilerManagerImpl.testSetup();
  }

  @Override
  protected void tearDown() throws Exception {
    myMainOutput.tearDown();
    myMainOutput = null;
    super.tearDown();
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) throws Exception {
    super.tuneFixture(moduleBuilder);
    final String s = myMainOutput.getTempDirPath() + "/out/production";
    new File(s).mkdirs();
    moduleBuilder.setOutputPath(s);
    final File[] groovyJars = GroovyUtils.getFilesInDirectoryByPattern(PathManager.getHomePath() + "/community/lib", GroovyConfigUtils.GROOVY_ALL_JAR_PATTERN);
    assert groovyJars.length == 1;
    moduleBuilder.addLibrary("Groovy", groovyJars[0].getPath());
    //moduleBuilder.addJdk(CompilerConfigurationImpl.getTestsExternalCompilerHome());
  }

  public void testPlainGroovy() throws Throwable {
    myFixture.addFileToProject("A.groovy", "println '239'");
    assertEmpty(make());
    assertOutput("A", "239");
  }

  public void testJavaDependsOnGroovy() throws Throwable {
    myFixture.addClass("public class Foo {" +
                       "public static void main(String[] args) { " +
                       "  System.out.println(new Bar().foo());" +
                       "}" +
                       "}");
    myFixture.addFileToProject("Bar.groovy", "class Bar {" +
                                             "  def foo() {" +
                                             "    239" +
                                             "  }" +
                                             "}");
    assertEmpty(make());
    assertOutput("Foo", "239");
  }

  public void testCorrectFailAndCorrect() throws Exception {
    myFixture.addClass("public class Foo {" +
                       "public static void main(String[] args) { " +
                       "  System.out.println(new Bar().foo());" +
                       "}" +
                       "}");
    final String barText = "class Bar {" + "  def foo() { 239  }" + "}";
    final PsiFile file = myFixture.addFileToProject("Bar.groovy", barText);
    assertEmpty(make());
    assertOutput("Foo", "239");

    setFileText(file, "class Bar {}");
    try {
      make();
      fail("Make should fail");
    }
    catch (RuntimeException e) {
      if (!(e.getCause() instanceof AssertionFailedError)) {
        throw e;
      }
    }

    setFileText(file, barText);
    assertEmpty(make());
    assertOutput("Foo", "239");
  }

  public void testRenameToJava() throws Throwable {
    myFixture.addClass("public class Foo {" +
                       "public static void main(String[] args) { " +
                       "  System.out.println(new Bar().foo());" +
                       "}" +
                       "}");

    final PsiFile bar =
      myFixture.addFileToProject("Bar.groovy", "public class Bar {" + "public int foo() { " + "  return 239;" + "}" + "}");

    assertEmpty(make());
    assertOutput("Foo", "239");

    new WriteCommandAction(getProject()) {
      protected void run(Result result) throws Throwable {
        bar.setName("Bar.java");
      }
    }.execute();

    assertEmpty(make());
    assertOutput("Foo", "239");
  }

  public void testTransitiveJavaDependency() throws Throwable {
    final VirtualFile ifoo = myFixture.addClass("public interface IFoo { int foo(); }").getContainingFile().getVirtualFile();
    myFixture.addClass("public class Foo implements IFoo {" +
                       "  public int foo() { return 239; }" +
                       "}");
    final PsiFile bar = myFixture.addFileToProject("Bar.groovy", "class Bar {" +
                                                                 "Foo foo\n" +
                                                                 "public static void main(String[] args) { " +
                                                                 "  System.out.println(new Foo().foo());" +
                                                                 "}" +
                                                                 "}");
    assertEmpty(make());
    assertOutput("Bar", "239");

    touch(ifoo);
    touch(bar.getVirtualFile());

    assertTrue(assertOneElement(make()).contains("WARNING: Groovyc stub generation failed"));
    assertOutput("Bar", "239");
  }

  public void testDeleteTransitiveJavaClass() throws Throwable {
    myFixture.addClass("public interface IFoo { int foo(); }");
    myFixture.addClass("public class Foo implements IFoo {" +
                       "  public int foo() { return 239; }" +
                       "}");
    final PsiFile bar = myFixture.addFileToProject("Bar.groovy", "class Bar {" +
                                                                 "Foo foo\n" +
                                                                 "public static void main(String[] args) { " +
                                                                 "  System.out.println(new Foo().foo());" +
                                                                 "}" +
                                                                 "}");
    assertEmpty(make());
    assertOutput("Bar", "239");

    deleteClassFile("IFoo");
    touch(bar.getVirtualFile());

    assertTrue(assertOneElement(make()).contains("WARNING: Groovyc stub generation failed"));
    assertOutput("Bar", "239");
  }

  public void testGroovyDependsOnGroovy() throws Throwable {
    myFixture.addClass("public class JustToMakeGroovyGenerateStubs {}");
    myFixture.addFileToProject("Foo.groovy", "class Foo { }");
    final PsiFile bar = myFixture.addFileToProject("Bar.groovy", "class Bar {" +
                                                                 "def foo(Foo f) {}\n" +
                                                                 "public static void main(String[] args) { " +
                                                                 "  System.out.println(239);" +
                                                                 "}" +
                                                                 "}");
    assertEmpty(make());
    assertOutput("Bar", "239");

    touch(bar.getVirtualFile());

    assertEmpty(make());
    assertOutput("Bar", "239");
  }

  private void deleteClassFile(final String className) throws IOException {
    new WriteCommandAction(getProject()) {
      protected void run(Result result) throws Throwable {
        ModuleRootManager.getInstance(myModule).getModuleExtension(CompilerModuleExtension.class).getCompilerOutputPath()
          .findChild(className + ".class").delete(this);
      }
    }.execute();
  }

  private static void touch(VirtualFile file) throws IOException {
    file.setBinaryContent(file.contentsToByteArray(), file.getModificationStamp() + 1, file.getTimeStamp() + 1);
  }

  private static void setFileText(PsiFile file, String barText) throws IOException {
    VfsUtil.saveText(file.getVirtualFile(), barText);
  }

  private List<String> make() {
    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    final ErrorReportingCallback callback = new ErrorReportingCallback(semaphore);
    CompilerManager.getInstance(getProject()).make(callback);
    semaphore.waitFor();
    callback.throwException();
    return callback.getMessages();
  }

  private void compile(VirtualFile... files) {
    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    final ErrorReportingCallback callback = new ErrorReportingCallback(semaphore);
    CompilerManager.getInstance(getProject()).compile(files, new ErrorReportingCallback(semaphore), false);
    semaphore.waitFor();
    callback.throwException();
  }

  private void assertOutput(String className, String output) throws ExecutionException {
    final ApplicationConfiguration configuration =
      new ApplicationConfiguration("app", getProject(), ApplicationConfigurationType.getInstance());
    configuration.setModule(myModule);
    configuration.setMainClassName(className);
    final DefaultRunExecutor extension = Executor.EXECUTOR_EXTENSION_NAME.findExtension(DefaultRunExecutor.class);
    final ExecutionEnvironment environment = new ExecutionEnvironment(configuration, new RunnerSettings<JDOMExternalizable>(null, null),null, DataManager.getInstance().getDataContext());
    final DefaultJavaProgramRunner runner = ProgramRunner.PROGRAM_RUNNER_EP.findExtension(DefaultJavaProgramRunner.class);
    final StringBuffer sb = new StringBuffer();
    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    runner.execute(extension, environment, new ProgramRunner.Callback() {
      public void processStarted(RunContentDescriptor descriptor) {
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
    private List<String> myMessages = new ArrayList<String>();

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
