// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.compiler

import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.fixtures.MavenDependencyUtil
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.TestLibrary
import org.junit.Assert
import org.junit.Ignore

@CompileStatic
class Groovyc25Test extends GroovycTestBase {

  @Override
  protected TestLibrary getGroovyLibrary() {
    GroovyProjectDescriptors.LIB_GROOVY_2_5
  }

  @Override
  protected boolean isRebuildExpectedAfterChangeInJavaClassExtendedByGroovy() {
    return true
  }

  @Override
  protected boolean isRebuildExpectedAfterChangesInGroovyWhichUseJava() {
    return false
  }

  @Override
  void "test extend groovy classes with additional dependencies"() {
    ModuleRootModificationUtil.updateModel(module) { model ->
      MavenDependencyUtil.addFromMaven(model, "org.codehaus.groovy:groovy-test:2.5.23", false)
    }
    super.'test extend groovy classes with additional dependencies'()
  }

  @Override
  void "test recompile one file that triggers chunk rebuild inside"() {
    'do test recompile one file that triggers chunk rebuild inside'(false)
  }

  protected boolean isRebuildExpectedWhileCompilingDependentTrait() {
    return true
  }
  
  void 'test dependent trait'() {
    def ca = myFixture.addFileToProject('A.groovy', 'class A implements B { }')
    myFixture.addFileToProject('B.groovy', 'trait B { A aaa }')
    assertEmpty make()
    touch(ca.virtualFile)
    if (isRebuildExpectedWhileCompilingDependentTrait()) {
      assert make().collect { it.message } == chunkRebuildMessage("Groovy stub generator")
    }
    else {
      assertEmpty(make())
    }
  }

  @Ignore("The rebuild was caused by a bug in groovy compiler, which is fixed in 2.5.16")
  void '_test dependent class instanceof'() {
    def ca = myFixture.addFileToProject('A.groovy', 'class A { def usage(x) { x instanceof B } }')
    myFixture.addFileToProject('B.groovy', 'class B { A aaa }')
    assertEmpty make()
    touch(ca.virtualFile)
    Assert.assertEquals(chunkRebuildMessage("Groovy compiler"), make().collect { it.message })
  }

  @Ignore("The rebuild was caused by a bug in groovy compiler, which is fixed in 2.5.16")
  void '_test dependent class exception'() {
    def ca = myFixture.addFileToProject('A.groovy', 'class A { def usage(x) throws B {} }')
    myFixture.addFileToProject('B.groovy', 'class B extends Throwable { A aaa }')
    assertEmpty make()
    touch(ca.virtualFile)
    Assert.assertEquals(chunkRebuildMessage("Groovy compiler"), make().collect { it.message })
  }

  @Ignore("The rebuild was caused by a bug in groovy compiler, which is fixed in 2.5.16")
  void '_test dependent class literal'() {
    def ca = myFixture.addFileToProject('A.groovy', 'class A { def usage() { B.class } }')
    myFixture.addFileToProject('B.groovy', '@groovy.transform.PackageScope class B { A aaa }')
    assertEmpty make()
    touch(ca.virtualFile)
    Assert.assertEquals(chunkRebuildMessage("Groovy compiler"), make().collect { it.message })
  }

  @Ignore("The rebuild was caused by a bug in groovy compiler, which is fixed in 2.5.16")
  void '_test dependent class array'() {
    def ca = myFixture.addFileToProject('A.groovy', 'class A { def usage() { new B[0] } }')
    myFixture.addFileToProject('B.groovy', 'class B { A aaa }')
    assertEmpty make()
    touch(ca.virtualFile)
    Assert.assertEquals(chunkRebuildMessage("Groovy compiler"), make().collect { it.message })
  }
}
