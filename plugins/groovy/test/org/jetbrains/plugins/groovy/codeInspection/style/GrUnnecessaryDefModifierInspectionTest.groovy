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
package org.jetbrains.plugins.groovy.codeInspection.style

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase

@CompileStatic
public class GrUnnecessaryDefModifierInspectionTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

  void 'test highlighting and fix'() {
    fixture.with {
      enableInspections GrUnnecessaryDefModifierInspection
      configureByText '_.groovy', '''\
def foo(<warning descr="Modifier 'def' is not necessary">def</warning> Object a) {}
def baw(<warning descr="Modifier 'def' is not necessary">def</warning> a) {}
<warning descr="Modifier 'def' is not necessary">d<caret>ef</warning> boolean baz(a) {}
<warning descr="Modifier 'def' is not necessary">def</warning> Object bar
def baf
def (int a, b) = [1, 2]
class A {
  <warning descr="Modifier 'def' is not necessary">def</warning> A() {}
}
'''
      checkHighlighting()
      launchAction findSingleIntention("Fix all 'Unnecessary 'def''")
      checkResult '''\
def foo(Object a) {}
def baw(a) {}
boolean baz(a) {}
Object bar
def baf
def (int a, b) = [1, 2]
class A {
  A() {}
}
'''
    }
  }
}