// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.compiler

import com.intellij.compiler.CompilerConfiguration
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.IndexingTestUtil
import groovy.transform.CompileStatic
import org.jetbrains.groovy.compiler.rt.GroovyRtConstants
import org.jetbrains.jps.incremental.groovy.JpsGroovycRunner

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait

@CompileStatic
abstract class GroovycTestBase extends GroovyCompilerTest {

  void "test navigate from stub to source"() {
    myFixture.addFileToProject("a.groovy", "class Groovy3 { InvalidType type }").virtualFile
    myFixture.addClass("class Java4 extends Groovy3 {}")

    def msg = make().find { it.message.contains('InvalidType') }
    assert msg?.virtualFile
    runInEdtAndWait {
      ApplicationManager.application.runWriteAction { msg.virtualFile.delete(this) }
    }

    def messages = make()
    assert messages
    def error = messages.find { it.message.contains('InvalidType') }
    assert error?.virtualFile
    assert ReadAction.compute { myFixture.findClass("Groovy3") == GroovyStubNotificationProvider.findClassByStub(project, error.virtualFile)}
  }

  void "test config script"() {
    def script = FileUtil.createTempFile("configScriptTest", ".groovy", true)
    FileUtil.writeToFile(script, "import groovy.transform.*; withConfig(configuration) { ast(CompileStatic) }")

    GroovyCompilerConfiguration.getInstance(project).configScript = script.path

    myFixture.addFileToProject("a.groovy", "class A { int s = 'foo' }")
    shouldFail make()
  }

  void "test user-level diagnostic for missing dependency of groovy-all"() {
    myFixture.addFileToProject 'Bar.groovy', '''import groovy.util.logging.Commons
@Commons
class Bar {}'''
    def msg = assertOneElement(make())
    assert msg.message.contains('Please')
    assert msg.message.contains('org.apache.commons.logging.Log')
  }

  void "test circular dependency with in-process class loading resolving"() {
    def groovyFile = myFixture.addFileToProject('mix/GroovyClass.groovy', '''
package mix
@groovy.transform.CompileStatic
class GroovyClass {
    JavaClass javaClass
    String bar() {
        return javaClass.foo() 
    }
}
''')
    myFixture.addFileToProject('mix/JavaClass.java', '''
package mix;
public class JavaClass {
    GroovyClass groovyClass;
    public String foo() {
        return "foo";
    }
}
''')
    CompilerConfiguration.getInstance(project).buildProcessVMOptions +=
      " -D$JpsGroovycRunner.GROOVYC_IN_PROCESS=true -D$GroovyRtConstants.GROOVYC_ASM_RESOLVING_ONLY=false"
    assertEmpty(make())

    touch(groovyFile.virtualFile)

    def messages = make()
    
    /* since only groovy file is changed, its class file is deleted, but javac isn't called (JavaBuilder.compile returns early), so 
       GroovyClass.class file from the generated stub isn't produced, and the classloader failed to load JavaClass during compilation of
       GroovyClass. After chunk rebuild is requested, javac is called so it compiles the stub and groovyc finishes successfully.  
     */
    assert messages.collect { it.message } == chunkRebuildMessage("Groovy compiler")
  }


  protected List<String> chunkRebuildMessage(String builder) {
    return ['Builder "' + builder + '" requested rebuild of module chunk "mainModule"']
  }
}
