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
public class GrUnnecessaryPublicModifierInspectionTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

  void 'test highlighting and fix'() {
    fixture.with {
      enableInspections GrUnnecessaryPublicModifierInspection
      configureByText '_.groovy', '''\
<warning descr="Modifier 'public' is not necessary">pu<caret>blic</warning> class A {
    <warning descr="Modifier 'public' is not necessary">public</warning> A() {}
    <warning descr="Modifier 'public' is not necessary">public</warning> foo() {}
    public x
    <warning descr="Modifier 'public' is not necessary">public</warning> class B {}
}
<warning descr="Modifier 'public' is not necessary">public</warning> enum E {}
<warning descr="Modifier 'public' is not necessary">public</warning> interface I {}
'''
      checkHighlighting()
      launchAction findSingleIntention("Fix all 'Unnecessary 'public''")
      checkResult '''\
class A {
    A() {}
    def foo() {}
    public x
    class B {}
}
enum E {}
interface I {}
'''
    }
  }
}