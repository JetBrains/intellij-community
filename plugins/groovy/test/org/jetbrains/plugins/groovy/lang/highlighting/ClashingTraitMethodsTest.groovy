// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.codeInspection.confusing.ClashingTraitMethodsInspection

/**
 * Created by Max Medvedev on 09/06/14
 */
class ClashingTraitMethodsTest extends GrHighlightingTestBase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    GroovyLightProjectDescriptor.GROOVY_3_0
  }

  @Override
  InspectionProfileEntry[] getCustomInspections() {
    [new ClashingTraitMethodsInspection()]
  }

  void testClash() {
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

  void testCustomImplementationNoClash() {
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

  void testNoClash() {
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

  void testNoClashWithInterface() {
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

  void testClashWithDefaultMethodInterfaces() {
    testHighlighting('''
interface T1 {
  default foo(){}
}

interface T2 {
  default foo() {}
}

class <warning descr="Traits T1, T2 contain clashing methods with signature foo()">A</warning> implements T1, T2 {
}
''')
  }

  void testClashTraitWithDefaultMethodInterface() {
    testHighlighting('''
trait T1 {
  def foo(){}
}

interface T2 {
  default foo() {}
}

class <warning descr="Traits T1, T2 contain clashing methods with signature foo()">A</warning> implements T1, T2 {
}
''')
  }


  void testNoClashInInheritor() {
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
