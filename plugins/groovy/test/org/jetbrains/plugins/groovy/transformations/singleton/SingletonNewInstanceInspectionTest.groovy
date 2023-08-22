// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.singleton

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.bugs.NewInstanceOfSingletonInspection

class SingletonNewInstanceInspectionTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST

  void setUp() {
    super.setUp()
    fixture.enableInspections NewInstanceOfSingletonInspection
  }

  private void doTest(String before, String after) {
    fixture.with {
      configureByText '_.groovy', before
      def action = findSingleIntention(GroovyBundle.message("replace.new.expression.with.instance.access"))
      assert action
      launchAction action
      checkResult after
    }
  }

  void 'test fix simple'() {
    doTest '''\
@Singleton
class A {}

new <caret>A()
''', '''\
@Singleton
class A {}

A.instance
'''
  }

  void 'test fix custom property name'() {
    doTest '''\
@Singleton(property = "coolInstance")
class A {}

new <caret>A()
''', '''\
@Singleton(property = "coolInstance")
class A {}

A.coolInstance
'''
  }
}
