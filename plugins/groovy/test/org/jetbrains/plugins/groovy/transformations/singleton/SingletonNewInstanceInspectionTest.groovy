/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.transformations.singleton

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle
import org.jetbrains.plugins.groovy.codeInspection.bugs.NewInstanceOfSingletonInspection

class SingletonNewInstanceInspectionTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

  void setUp() {
    super.setUp()
    fixture.enableInspections NewInstanceOfSingletonInspection
  }

  private void doTest(String before, String after) {
    fixture.with {
      configureByText '_.groovy', before
      def action = findSingleIntention(GroovyInspectionBundle.message("replace.new.expression.with.instance.access"))
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
