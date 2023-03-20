// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  /**
   * @see org.codehaus.groovy.control.ClassNodeResolver#tryAsLoaderClassOrScript(java.lang.String, org.codehaus.groovy.control.CompilationUnit)
   */
  @Override
  void testTransitiveJavaDependencyThroughGroovy() {
    // in 2.4:
    // - the Foo is not well-formed (IFoo doesn't exist), so we throw NCDFE;
    // - NCDFE forces loading Foo class node from Foo.groovy file;
    // - Foo.groovy is added to the current compile session;
    // => no chunk rebuild
    //
    // in 2.5:
    // - the Foo is loaded as decompiled node;
    // - when the Bar stub is being written on the disk, it throws NCDFE when trying to resolve IFoo;
    // => chunk rebuild
    doTestTransitiveJavaDependencyThroughGroovy(true)
  }

  @Override
  void testStubForGroovyExtendingJava() {
    // same as in testTransitiveJavaDependencyThroughGroovy
    doTestStubForGroovyExtendingJava(true)
  }

  @Override
  void 'test changed groovy refers to java which refers to changed groovy and fails in stub generator'() {
    'do test changed groovy refers to java which refers to changed groovy and fails in stub generator'(false)
  }

  @Override
  void "test changed groovy refers to java which refers to changed groovy and fails in compiler"() {
    'do test changed groovy refers to java which refers to changed groovy and fails in compiler'(false)
  }

  @Override
  void testMakeInDependentModuleAfterChunkRebuild() {
    doTestMakeInDependentModuleAfterChunkRebuild(false)
  }

  @Override
  void "test inner java class references with incremental recompilation"() {
    'do test inner java class references with incremental recompilation'(false)
  }

  @Override
  void "test extend groovy classes with additional dependencies"() {
    ModuleRootModificationUtil.updateModel(module) { model ->
      MavenDependencyUtil.addFromMaven(model, "org.codehaus.groovy:groovy-test:2.5.17", false)
    }
    super.'test extend groovy classes with additional dependencies'()
  }

  @Override
  void "test recompile one file that triggers chunk rebuild inside"() {
    'do test recompile one file that triggers chunk rebuild inside'(false)
  }

  void 'test dependent trait'() {
    def ca = myFixture.addFileToProject('A.groovy', 'class A implements B { }')
    myFixture.addFileToProject('B.groovy', 'trait B { A aaa }')
    assertEmpty make()
    touch(ca.virtualFile)
    assert make().collect { it.message } == chunkRebuildMessage("Groovy stub generator")
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
