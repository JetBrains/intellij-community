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
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInsight.generation.OverrideImplementExploreUtil
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitMethod

/**
 * Created by Max Medvedev on 17/02/14
 */
class Groovy23HighlightingTest extends GrHighlightingTestBase {

  @Override
  @NotNull
  protected LightProjectDescriptor getProjectDescriptor() {
    return GroovyLightProjectDescriptor.GROOVY_2_3
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
    exec('foo', { Integer t, Integer x -> ; }, 1)
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
    exec('foo', { Integer s, Integer x -> print 9 }, 1)
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

  void 'test trait with middle interface'() {
    testHighlighting '''\
trait T<E> {
    E foo() { null }
}

interface I<G> extends T<G> {}

class C implements I<String> {}

new C().fo<caret>o()
'''
    def element = file.findElementAt(fixture.editor.caretModel.offset)
    def call = PsiTreeUtil.getParentOfType(element, GrMethodCallExpression)
    assert call.type.equalsToText("java.lang.String")
  }

  void 'test trait order'() {
    fixture.configureByText '_.groovy','''\
trait T1 {
    def foo() { 1 }
}
trait T2 {
    def foo() { 2 }
}
interface I1 extends T1 {}
interface I2 extends T2 {}
interface I3 extends I1, I2 {}
class TT implements I3 {}
new TT().fo<caret>o()
'''
    def resolved = fixture.file.findReferenceAt(fixture.editor.caretModel.offset).resolve()
    assert resolved instanceof GrTraitMethod
    def original = resolved.prototype
    assert original instanceof GrMethod
    assert original.containingClass.name == 'T2'
  }

  void 'test non-static inner class in trait not allowed'() {
    testHighlighting '''\
interface I {}

trait T {
  def a = new <error descr="Non-static inner classes are not allowed in traits">I</error>() {}
  def foo() {
    new <error descr="Non-static inner classes are not allowed in traits">I</error>() {}
  }
  class <error descr="Non-static inner classes are not allowed in traits">A</error> {}
  static class B {
    def foo() {
      new I() {} //no error here
    }
  }
}
'''
  }

  void 'test static trait members not resolved in direct access'() {
    testHighlighting '''\
trait StaticsContainer {
  public static boolean CALLED = false
  static void init() { CALLED = true }
  static foo() { init() }
}

class NoName implements StaticsContainer {}

NoName.init()
assert NoName.StaticsContainer__CALLED

StaticsContainer.<warning descr="Cannot resolve symbol 'init'">init</warning>()
StaticsContainer.<warning descr="Cannot resolve symbol 'CALLED'">CALLED</warning>
'''
    fixture.configureByText 'Consumer.java', '''\
public class Consumer {
  public static void main(String[] args) {
    System.out.println(NoName.StaticsContainer__CALLED);
    System.out.println(StaticsContainer.<error descr="Cannot resolve symbol 'CALLED'">CALLED</error>);
  }
}
'''
    fixture.testHighlighting()
  }

  void 'test class initializers in traits'() {
    testHighlighting '''\
trait T {
  static {
  }

  {
  }
}
'''
  }

  void 'test abstract property in class'() {
    fixture.with {
      configureByText '_.groovy', '''\
class A {
    abstract f
}
'''
      checkHighlighting()
    }
  }

  void 'test static modifier on toplevel definition (not trait) is not allowed'() {
    testHighlighting '''\
<error descr="Modifier 'static' not allowed here">static</error> class A {}
<error descr="Modifier 'static' not allowed here">static</error> interface I {}
static trait T {}
'''
  }

  final InspectionProfileEntry[] customInspections = [new GroovyAssignabilityCheckInspection(), new GrUnresolvedAccessInspection()]
}
