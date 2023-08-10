// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.compiler

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.FileUtil
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.TestLibrary

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait

@CompileStatic
abstract class GroovycTestBase extends GroovyCompilerTest {

  protected abstract TestLibrary getGroovyLibrary()

  @Override
  protected final void addGroovyLibrary(Module to) {
    groovyLibrary.addTo(to)
  }

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
    shouldFail { make() }
  }

  void "test user-level diagnostic for missing dependency of groovy-all"() {
    myFixture.addFileToProject 'Bar.groovy', '''import groovy.util.logging.Commons
@Commons
class Bar {}'''
    def msg = assertOneElement(make())
    assert msg.message.contains('Please')
    assert msg.message.contains('org.apache.commons.logging.Log')
  }

  protected List<String> chunkRebuildMessage(String builder) {
    return ['Builder "' + builder + '" requested rebuild of module chunk "mainModule"']
  }
}
