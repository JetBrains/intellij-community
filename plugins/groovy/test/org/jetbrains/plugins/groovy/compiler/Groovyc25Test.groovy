// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.compiler

import com.intellij.idea.Bombed
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.TestLibrary

@CompileStatic
class Groovyc25Test extends GroovycTestBase {

  @Override
  protected TestLibrary getGroovyLibrary() {
    GroovyProjectDescriptors.LIB_GROOVY_2_5
  }

  @Bombed(user = 'daniil', year = 2029, month = Calendar.JANUARY, day = 4)
  @Override
  void testClassLoadingDuringBytecodeGeneration() {
    super.testClassLoadingDuringBytecodeGeneration()
  }

  @Bombed(user = 'daniil', year = 2029, month = Calendar.JANUARY, day = 4)
  @Override
  void "test inner java class references with incremental recompilation"() {
    super.'test inner java class references with incremental recompilation'()
  }

  @Bombed(user = 'daniil', year = 2029, month = Calendar.JANUARY, day = 4)
  @Override
  void testTransitiveJavaDependencyThroughGroovy() {
    super.testTransitiveJavaDependencyThroughGroovy()
  }

  @Bombed(user = 'daniil', year = 2029, month = Calendar.JANUARY, day = 4)
  @Override
  void testMakeInDependentModuleAfterChunkRebuild() {
    super.testMakeInDependentModuleAfterChunkRebuild()
  }

  @Bombed(user = 'daniil', year = 2029, month = Calendar.JANUARY, day = 4)
  @Override
  void "test extend groovy classes with additional dependencies"() {
    super.'test extend groovy classes with additional dependencies'()
  }

  @Bombed(user = 'daniil', year = 2029, month = Calendar.JANUARY, day = 4)
  @Override
  void testStubForGroovyExtendingJava() {
    super.testStubForGroovyExtendingJava()
  }

  @Bombed(user = 'daniil', year = 2029, month = Calendar.JANUARY, day = 4)
  @Override
  void "test recompile one file that triggers chunk rebuild inside"() {
    super.'test recompile one file that triggers chunk rebuild inside'()
  }

  @Bombed(user = 'daniil', year = 2029, month = Calendar.JANUARY, day = 4)
  @Override
  void testPartialCrossRecompile() {
    super.testPartialCrossRecompile()
  }

  void 'test dependent trait'() {
    def ca = myFixture.addFileToProject('A.groovy', 'class A implements B { }')
    myFixture.addFileToProject('B.groovy', 'trait B { A aaa }')
    assertEmpty make()
    touch(ca.virtualFile)
    assert make().collect { it.message } == chunkRebuildMessage("Groovy stub generator")
  }

  void 'test dependent class instanceof'() {
    def ca = myFixture.addFileToProject('A.groovy', 'class A { def usage(x) { x instanceof B } }')
    myFixture.addFileToProject('B.groovy', 'class B { A aaa }')
    assertEmpty make()
    touch(ca.virtualFile)
    assert make().collect { it.message } == chunkRebuildMessage("Groovy compiler")
  }

  void 'test dependent class exception'() {
    def ca = myFixture.addFileToProject('A.groovy', 'class A { def usage(x) throws B {} }')
    myFixture.addFileToProject('B.groovy', 'class B extends Throwable { A aaa }')
    assertEmpty make()
    touch(ca.virtualFile)
    assert make().collect { it.message } == chunkRebuildMessage("Groovy compiler")
  }

  void 'test dependent class literal'() {
    def ca = myFixture.addFileToProject('A.groovy', 'class A { def usage() { B.class } }')
    myFixture.addFileToProject('B.groovy', '@groovy.transform.PackageScope class B { A aaa }')
    assertEmpty make()
    touch(ca.virtualFile)
    assert make().collect { it.message } == chunkRebuildMessage("Groovy compiler")
  }

  void 'test dependent class array'() {
    def ca = myFixture.addFileToProject('A.groovy', 'class A { def usage() { new B[0] } }')
    myFixture.addFileToProject('B.groovy', 'class B { A aaa }')
    assertEmpty make()
    touch(ca.virtualFile)
    assert make().collect { it.message } == chunkRebuildMessage("Groovy compiler")
  }
}
