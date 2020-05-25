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

  @Bombed(year = 2029)
  @Override
  void testClassLoadingDuringBytecodeGeneration() {
    super.testClassLoadingDuringBytecodeGeneration()
  }

  @Bombed(year = 2029)
  @Override
  void "test inner java class references with incremental recompilation"() {
    super.'test inner java class references with incremental recompilation'()
  }

  @Bombed(year = 2029)
  @Override
  void testTransitiveJavaDependencyThroughGroovy() {
    super.testTransitiveJavaDependencyThroughGroovy()
  }

  @Bombed(year = 2029)
  @Override
  void testMakeInDependentModuleAfterChunkRebuild() {
    super.testMakeInDependentModuleAfterChunkRebuild()
  }

  @Bombed(year = 2029)
  @Override
  void "test extend groovy classes with additional dependencies"() {
    super.'test extend groovy classes with additional dependencies'()
  }

  @Bombed(year = 2029)
  @Override
  void testStubForGroovyExtendingJava() {
    super.testStubForGroovyExtendingJava()
  }

  @Bombed(year = 2029)
  @Override
  void "test recompile one file that triggers chunk rebuild inside"() {
    super.'test recompile one file that triggers chunk rebuild inside'()
  }

  @Bombed(year = 2029)
  @Override
  void testPartialCrossRecompile() {
    super.testPartialCrossRecompile()
  }
}
