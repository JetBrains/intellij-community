/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInspection.actions.CleanupAllIntention
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase

@CompileStatic
class GrUnnecessaryDefModifierInspectionTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST
  final GrUnnecessaryDefModifierInspection inspection = new GrUnnecessaryDefModifierInspection()

  @Override
  void setUp() {
    super.setUp()
    fixture.addClass 'public @interface DummyAnno {}'
  }

  void 'test method parameter'() {
    doTest false, '''\
def parameter1(<warning descr="Modifier 'def' is not necessary">def</warning> Object a) {}

def parameter2(<warning descr="Modifier 'def' is not necessary">def</warning> a) {}

def parameter3(@DummyAnno <warning descr="Modifier 'def' is not necessary">def</warning> a) {}
''', '''\
def parameter1(Object a) {}

def parameter2(a) {}

def parameter3(@DummyAnno a) {}
'''
  }

  void 'test method parameter typed only'() {
    doTest true, '''\
def parameter1(<warning descr="Modifier 'def' is not necessary">def</warning> Object a) {}

def parameter2(def a) {}

def parameter3(@DummyAnno def a) {}
'''
  }

  void 'test local variable'() {
    doTest false, '''\
<warning descr="Modifier 'def' is not necessary">def</warning> Object localVar1
def localVar2
@DummyAnno <warning descr="Modifier 'def' is not necessary">def</warning> localVar3
def (int a, b) = [1, 2]
''', '''\
Object localVar1
def localVar2
@DummyAnno localVar3
def (int a, b) = [1, 2]
'''
  }

  void 'test local variable typed only'() {
    doTest true, '''\
<warning descr="Modifier 'def' is not necessary">def</warning> Object localVar1
def localVar2
@DummyAnno def localVar3
def (int a, b) = [1, 2]
'''
  }

  void 'test return type'() {
    doTest false, '''\
<warning descr="Modifier 'def' is not necessary">def</warning> void returnType1() {}

def returnType2() {}

@DummyAnno
<warning descr="Modifier 'def' is not necessary">def</warning> returnType3() {}
''', '''\
void returnType1() {}

def returnType2() {}

@DummyAnno
returnType3() {}
'''
  }

  void 'test return type typed only'() {
    doTest true, '''\
<warning descr="Modifier 'def' is not necessary">def</warning> void returnType1() {}

def returnType2() {}

@DummyAnno
def returnType3() {}
'''
  }

  void 'test generics'() {
    doTest false, '''\
def <T> void generics1() {}

synchronized <warning descr="Modifier 'def' is not necessary">def</warning> <T> void generics2() {}

@DummyAnno
<warning descr="Modifier 'def' is not necessary">def</warning> <T> void generics3() {}
''', '''\
def <T> void generics1() {}

synchronized <T> void generics2() {}

@DummyAnno
<T> void generics3() {}
'''
  }

  void 'test generics typed only'() {
    doTest true, '''\
def <T> void generics1() {}

synchronized <warning descr="Modifier 'def' is not necessary">def</warning> <T> void generics2() {}

@DummyAnno
<warning descr="Modifier 'def' is not necessary">def</warning> <T> void generics3() {}
'''
  }

  void 'test constructor'() {
    doTest false, '''\
class A {
  <warning descr="Modifier 'def' is not necessary">def</warning> A() {}
}
''', '''\
class A {
  A() {}
}
'''
  }

  void 'test constructor typed only'() {
    doTest true, '''\
class A {
  <warning descr="Modifier 'def' is not necessary">def</warning> A() {}
}
'''
  }

  void 'test java for-each'() {
    doTest false, '''\
def list = []
for (<warning descr="Modifier 'def' is not necessary">def</warning> Object a : list) {}
for (def a : list) {}
for (@DummyAnno <warning descr="Modifier 'def' is not necessary">def</warning> a : list) {}
''', '''\
def list = []
for (Object a : list) {}
for (def a : list) {}
for (@DummyAnno a : list) {}
'''
  }

  void 'test java for-each typed only'() {
    doTest true, '''\
def list = []
for (<warning descr="Modifier 'def' is not necessary">def</warning> Object a : list) {}
for (def a : list) {}
for (@DummyAnno def a : list) {}
'''
  }

  void 'test java for'() {
    doTest false, '''\
def list = []
for (<warning descr="Modifier 'def' is not necessary">def</warning> int b = 0; b < 10; b++) {}
for (def b = 0; b < 10; b++) {}
for (@DummyAnno <warning descr="Modifier 'def' is not necessary">def</warning> b = 0; b < 10; b++) {}
''', '''\
def list = []
for (int b = 0; b < 10; b++) {}
for (def b = 0; b < 10; b++) {}
for (@DummyAnno b = 0; b < 10; b++) {}
'''
  }

  void 'test java for typed only'() {
    doTest true, '''\
def list = []
for (<warning descr="Modifier 'def' is not necessary">def</warning> int b = 0; b < 10; b++) {}
for (def b = 0; b < 10; b++) {}
for (@DummyAnno def b = 0; b < 10; b++) {}
'''
  }


  void 'test for-each'() {
    doTest false, '''\
def list = []
for (<warning descr="Modifier 'def' is not necessary">def</warning> Object c in list) {}
for (<warning descr="Modifier 'def' is not necessary">def</warning> c in list) {}
for (@DummyAnno <warning descr="Modifier 'def' is not necessary">def</warning> c in list) {}
''', '''\
def list = []
for (Object c in list) {}
for (c in list) {}
for (@DummyAnno c in list) {}
'''
  }

  void 'test for-each typed only'() {
    doTest true, '''\
def list = []
for (<warning descr="Modifier 'def' is not necessary">def</warning> Object c in list) {}
for (def c in list) {}
for (@DummyAnno def c in list) {}
'''
  }

  private void doTest(boolean typed = false, String before, String after = null) {
    inspection.reportExplicitTypeOnly = typed
    fixture.with {
      enableInspections inspection
      configureByText '_.groovy', before
      checkHighlighting()
      if (after != null) {
        launchAction CleanupAllIntention.INSTANCE
        checkResult after
      }
    }
  }
}