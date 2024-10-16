// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.compiler;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.groovy.compiler.rt.GroovyRtConstants;
import org.jetbrains.jps.incremental.groovy.JpsGroovycRunner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public abstract class GroovycTestBase extends GroovyCompilerTest {
  public void test_navigate_from_stub_to_source() {
    myFixture.addFileToProject("a.groovy", "class Groovy3 { InvalidType type }").getVirtualFile();
    myFixture.addClass("class Java4 extends Groovy3 {}");

    final Optional<CompilerMessage> msg = make().stream().filter(it -> it.getMessage().contains("InvalidType")).findFirst();
    assert msg.isPresent();
    assert msg.get().getVirtualFile() != null;

    EdtTestUtil.runInEdtAndWait(() -> {
        ApplicationManager.getApplication().runWriteAction(() -> {
          try { msg.get().getVirtualFile().delete(this); }
          catch (IOException e) { throw new RuntimeException(e); }
        });
    });

    List<CompilerMessage> messages = make();
    assert messages != null;
    final Optional<CompilerMessage> msg2 = messages.stream().filter(it -> it.getMessage().contains("InvalidType")).findFirst();
    assert msg2.isPresent();
    assert msg2.get().getVirtualFile() != null;

    assert ReadAction.compute(() -> {
        return myFixture
          .findClass("Groovy3")
          .equals(GroovyStubNotificationProvider.findClassByStub(getProject(), msg2.get().getVirtualFile()));
    });
  }

  public void test_config_script() throws IOException {
    File script = FileUtil.createTempFile("configScriptTest", ".groovy", true);
    FileUtil.writeToFile(script, "import groovy.transform.*; withConfig(configuration) { ast(CompileStatic) }");

    GroovyCompilerConfiguration.getInstance(getProject()).setConfigScript(script.getPath());

    myFixture.addFileToProject("a.groovy", "class A { int s = 'foo' }");
    GroovyCompilerTestCase.shouldFail(make());
  }

  public void test_user_level_diagnostic_for_missing_dependency_of_groovy_all() {
    myFixture.addFileToProject("Bar.groovy", """
      import groovy.util.logging.Commons
      @Commons
      class Bar {}
    """);
    CompilerMessage msg = UsefulTestCase.assertOneElement(make());
    assert msg.getMessage().contains("Please");
    assert msg.getMessage().contains("org.apache.commons.logging.Log");
  }

public void test_circular_dependency_with_in_process_class_loading_resolving() throws IOException {
    PsiFile groovyFile = myFixture.addFileToProject("mix/GroovyClass.groovy", """
      package mix
      @groovy.transform.CompileStatic
      class GroovyClass {
          JavaClass javaClass
          String bar() {
              return javaClass.foo()
          }
      }
    """);
    myFixture.addFileToProject("mix/JavaClass.java", """
        package mix;
        public class JavaClass {
            GroovyClass groovyClass;
            public String foo() {
                return "foo";
            }
        }
    """);

    final var configuration = CompilerConfiguration.getInstance(getProject());
    configuration.setBuildProcessVMOptions(
      configuration.getBuildProcessVMOptions() + " -D" + JpsGroovycRunner.GROOVYC_IN_PROCESS + "=true -D" + GroovyRtConstants.GROOVYC_ASM_RESOLVING_ONLY + "=false"
    );
    assert(make().isEmpty());

    touch(groovyFile.getVirtualFile());

    List<CompilerMessage> messages = make();
    
    /* since only groovy file is changed, its class file is deleted, but javac isn't called (JavaBuilder.compile returns early), so 
       GroovyClass.class file from the generated stub isn't produced, and the classloader failed to load JavaClass during compilation of
       GroovyClass. After chunk rebuild is requested, javac is called so it compiles the stub and groovyc finishes successfully.  
     */
    assert ContainerUtil.map(messages, m -> m.getMessage()).equals(chunkRebuildMessage("Groovy compiler"));
  }

  @Override
  protected List<String> chunkRebuildMessage(String builder) {
    return new ArrayList<>(Arrays.asList("Builder \"" + builder + "\" requested rebuild of module chunk \"mainModule\""));
  }

}
