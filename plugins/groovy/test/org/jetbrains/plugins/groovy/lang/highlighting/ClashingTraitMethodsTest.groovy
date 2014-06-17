/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.codeInspection.confusing.ClashingTraitMethodsInspection

/**
 * Created by Max Medvedev on 09/06/14
 */
class ClashingTraitMethodsTest extends GrHighlightingTestBase {

  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    GroovyLightProjectDescriptor.GROOVY_2_3
  }

  @Override
  InspectionProfileEntry[] getCustomInspections() {
    [new ClashingTraitMethodsInspection()]
  }

  public void testClash() {
    testHighlighting('''
trait T1 {
  def foo(){}
}

trait T2 {
  def foo(){}
}

class <warning descr="Traits T1, T2 contain clashing methods with signature foo()">A</warning> implements T1, T2 {

}
''')
  }

  public void testCustomImplementationNoClash() {
    testHighlighting('''
trait T1 {
  def foo(){}
}

trait T2 {
  def foo(){}
}

class A implements T1, T2 {
    def foo() {}
}
''')
  }

  public void testNoClash() {
    testHighlighting('''
trait T1 {
  def foo(){}
}

trait T2 {
  abstract def foo()
}

class A implements T1, T2 {
}
''')
  }

  public void testNoClashWithInterface() {
    testHighlighting('''
trait T1 {
  def foo(){}
}

interface T2 {
  def foo()
}

class A implements T1, T2 {
}
''')
  }


  public void testNoClashInInheritor() {
    testHighlighting('''
trait T1 {
  def foo(){}
}

interface T2 {
  def foo()
}

class A implements T1, T2 {
}

class B extends A{}
''')
  }
}
