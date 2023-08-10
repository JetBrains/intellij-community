// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.codeInspection.confusing.ClashingTraitMethodsInspection

/**
 * Created by Max Medvedev on 09/06/14
 */
class ClashingTraitMethodsTest extends GrHighlightingTestBase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_3_0

  @Override
  InspectionProfileEntry[] getCustomInspections() {
    [new ClashingTraitMethodsInspection()]
  }

  void testClash() {
    doTestHighlighting('''
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
    doTestHighlighting('''
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
    doTestHighlighting('''
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
    doTestHighlighting('''
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
    doTestHighlighting('''
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
    doTestHighlighting('''
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
    doTestHighlighting('''
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
