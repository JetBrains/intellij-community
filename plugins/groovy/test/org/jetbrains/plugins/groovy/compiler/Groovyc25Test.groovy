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
}
