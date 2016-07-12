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
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInsight.generation.OverrideImplementExploreUtil
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition

/**
 * Created by Max Medvedev on 17/02/14
 */
class Groovy23HighlightingTest extends GrHighlightingTestBase {

  @Override
  @NotNull
  protected LightProjectDescriptor getProjectDescriptor() {
    return GroovyLightProjectDescriptor.GROOVY_2_3;
  }

  void testSam1() {
    testHighlighting('''
interface Action<T, X> {
    void execute(T t, X x)
}

public <T, X> void exec(T t, Action<T, X> f, X x) {
}

def foo() {
    exec('foo', { String t, Integer x -> ; }, 1)
    exec<warning descr="'exec' in '_' cannot be applied to '(java.lang.String, groovy.lang.Closure<java.lang.Void>, java.lang.Integer)'">('foo', { Integer t, Integer x -> ; }, 1)</warning>
}
''')
  }

  void testSam2() {
    testHighlighting('''
interface Action<T> {
    void execute(T t)
}

public <T> void exec(T t, Action<T> f) {
}

def foo() {
    exec('foo') {print it.toUpperCase() ;print 2 }
    exec('foo') {print it.<warning descr="Cannot resolve symbol 'intValue'">intValue</warning>() ;print 2 }
}
''')
  }

  void testSam3() {
    testHighlighting('''
 interface Action<T, X> {
    void execute(T t, X x)
}

public <T, X> void exec(T t, Action<T, X> f, X x) {
    f.execute(t, x)
}

def foo() {
    exec('foo', { String s, Integer x -> print s + x }, 1)
    exec<warning descr="'exec' in '_' cannot be applied to '(java.lang.String, groovy.lang.Closure, java.lang.Integer)'">('foo', { Integer s, Integer x -> print 9 }, 1)</warning>
}
''')
  }

  void 'test default parameter in trait methods'() {
    testHighlighting '''\
trait T {
  abstract foo(x = 3);
  def bar(y = 6) {}
}
'''
  }

  void 'test concrete trait property'() {
    testHighlighting '''\
trait A {
  def foo
}
class B implements A {}
'''
  }

  void 'test abstract trait property'() {
    testHighlighting '''\
trait A {
  abstract foo
}
<error descr="Method 'getFoo' is not implemented">class B implements A</error> {}
'''
    testHighlighting '''\
trait A {
  abstract foo
}
class B implements A {
  def foo
}
'''
  }

  void 'test traits have only abstract and non-final methods'() {
    def file = myFixture.addFileToProject('T.groovy', '''\
trait T {
  def foo
  abstract bar
  def baz() {}
  abstract doo()
}
''') as GroovyFile
    def definition = file.classes[0] as GrTypeDefinition
    for (method in definition.methods) {
      assert method.hasModifierProperty(GrModifier.ABSTRACT)
      assert !method.hasModifierProperty(GrModifier.FINAL)
    }
  }

  void 'test trait with method with default parameters'() {
    testHighlighting '''\
trait A {
  def foo(a, b = null, c = null) {}
}
class B implements A {}
'''
  }

  void 'test trait with abstract method with default parameters'() {
    testHighlighting '''
trait A {
  abstract foo(a, b = null, c = null)
}
<error descr="Method 'foo' is not implemented">class B implements A</error> {}
'''
    def definition = fixture.findClass('B') as GrTypeDefinition
    def map = OverrideImplementExploreUtil.getMapToOverrideImplement(definition, true)
    assert map.size() == 1 // need to implement only foo(a, b, c)
  }

  final InspectionProfileEntry[] customInspections = [new GroovyAssignabilityCheckInspection(), new GrUnresolvedAccessInspection()]
}
