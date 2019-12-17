// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.codeInsight.generation.OverrideImplementUtil
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMirrorElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.transformations.TransformationUtilKt
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Max Medvedev
 */
class DelegateTest extends GroovyResolveTestCase {
  @Override
  protected String getBasePath() {
    return "${TestUtils.testDataPath}resolve/delegate/"
  }

  private <T> T doTest(String text, Class<T> clazz) {
    def ref = configureByText(text)
    def resolved = ref.resolve()

    if (clazz != null) {
      assertNotNull resolved
      assertInstanceOf resolved, clazz
      return resolved.asType(clazz)
    } else {
      assertNull resolved
    }
  }

  private PsiMirrorElement doTest(String text) {
    return doTest(text, PsiMirrorElement)
  }


  void testSimple() {
    doTest('''
class A {
  def foo(){}
}

class B {
  @Delegate A a
}

new B().fo<caret>o()
''')
  }

  void testSimple2() {
    doTest('''
class A {
  def foo(){}
}

class B {
  @Delegate A getA(){return new A()}
}

new B().fo<caret>o()
''')
  }

  void testMethodDelegateUnresolved() {
    doTest('''
class A {
  def foo(){}
}

class B {
  @Delegate A getA(int i){return new A()}
}

new B().fo<caret>o()
''', null)
  }

  void testInheritance() {
    doTest('''
class Base {
  def foo(){}
}

class A extends Base {} 

class B {
  @Delegate A a
}

new B().fo<caret>o()
''')
  }

  void testSelectFirst1() {
    def resolved = doTest('''
class A1 {
  def foo(){}
}

class A2 {
  def foo(){}
}
  

class B {
  @Delegate A1 a1
  @Delegate A2 a2
}

new B().fo<caret>o()
''')

    def prototype = resolved.prototype as PsiMethod
    def cc = prototype.containingClass
    assertEquals 'A1', cc.name
  }

  void testSelectFirst2() {
    def resolved = doTest('''
class A1 {
  def foo(){}
}

class A2 {
  def foo(){}
}


class B {
  @Delegate A2 a2
  @Delegate A1 a1
}

new B().fo<caret>o()
''')

    def prototype = resolved.prototype as PsiMethod
    def cc = prototype.containingClass
    assertEquals 'A2', cc.name
  }

  void testSelectFirst3() {
    def resolved = doTest('''
class A1 {
  def foo(){}
}

class A2 {
  def foo(){}
}


class B {
  @Delegate A2 bar()
  @Delegate A1 bar2()
}

new B().fo<caret>o()
''')

    def prototype = resolved.prototype as PsiMethod
    def cc = prototype.containingClass
    assertEquals 'A2', cc.name
  }

  void testSelectFirst4() {
    def resolved = doTest('''
class A1 {
  def foo(){}
}

class A2 {
  def foo(){}
}


class B {
  @Delegate A1 bar2()
  @Delegate A2 bar // fields are processed before methods
  
}

new B().fo<caret>o()
''')

    def prototype = resolved.prototype as PsiMethod
    def cc = prototype.containingClass
    assertEquals 'A2', cc.name
  }

  void testSubstitutor1() {
    def resolved = doTest('''
class Base<T> {
  def foo(T t){}
}

class A<T> extends Base<List<T>> {
}


class B {
  @Delegate A<String> a2
}

new B().fo<caret>o([''])
''')

    assertInstanceOf(resolved, PsiMethod)
    def parameters = (resolved as PsiMethod).parameterList.parameters
    assertEquals 'java.util.List<java.lang.String>', parameters[0].type.canonicalText
  }

  void testCycle() {
    TransformationUtilKt.disableAssertOnRecursion(testRootDisposable)
    def file = myFixture.configureByText('a.groovy', '''
class A {
    def foo() {}
    
    @Delegate C c
}

class B {
    def test() {}
    
    @Delegate A a
}

class C {
    def bar(){}
    
    @Delegate B b
}



new A().foo()
new B().foo()
new C().foo()

new A().bar()
new B().bar()
new C().bar()

new A().test()
new B().test()
new C().test()
''') as GroovyFile

    def result = file.statements.findAll {
      def method = (it as GrMethodCall).resolveMethod()
      !method
    }
    assert result.size() <= 1: result.collect { it.text }
  }


  void testJavaDelegate() {
    myFixture.addFileToProject('A.java', '''
class Base {
  public void foo(){}
}
class A extends Base{}
''')

    doTest('''
class B {
  @Delegate A a
}

new B().fo<caret>o()''')
  }

  void testInheritanceCycle() {
    TransformationUtilKt.disableAssertOnRecursion(testRootDisposable)
    def file = myFixture.configureByText('a.groovy', '''
class Base {
  @Delegate A a
}
class A extends Base {
  void foo(){}
}

new Base().foo()
new A().foo()''') as GroovyFile

    file.statements.each {
      assertNotNull((it as GrMethodCall).resolveMethod())
    }
  }

  void testCompoundHierarchy() {
    myFixture.addFileToProject('Foo.java', '''\
public interface Foo {
    String getA();

    @Deprecated
    String getB();
}
''')
    myFixture.addFileToProject('Bar.java', '''\
public abstract class Bar implements Foo {
    public String getA() {
        return null;
    }

    @Deprecated
    public String getB() {
        return null;
    }
}
''')
    myFixture.addFileToProject('FooBar.java', '''
public class FooBar extends Bar implements Foo {
}
''')
    assertAllMethodsImplemented('Baz.groovy', '''\
class Baz {
    @Delegate(deprecated = true)
    FooBar bare
}
''')
  }

  private void assertAllMethodsImplemented(String fileName, String text) {
    def file = myFixture.configureByText(fileName, text) as GroovyFile

    assertNotNull(file)
    final clazz = file.classes[0]
    assertNotNull(clazz)
    assertEmpty OverrideImplementUtil.getMethodSignaturesToImplement(clazz)
  }

  void testDeprecatedFalse() {
    myFixture.addFileToProject('Foo.groovy', '''\
interface Foo {
    @Deprecated
    void foo()
}
''')
    assertAllMethodsImplemented('text.groovy', '''\
class FooImpl implements Foo {
    @Delegate(deprecated = false) Foo delegate
}
''')
  }

  void 'test delegate with generics'() {
    assertAllMethodsImplemented('a.groovy', '''
class MyClass {
    @Delegate
    HashMap<String, Integer> map = new HashMap<String, Integer>()
}
''')
  }

  void 'test delegate method with generics'() {
    assertAllMethodsImplemented('a.groovy', '''
class MyClass {
    @Delegate
    HashMap<String, Integer> getMap() {return new HashMap<>}
}
''')
  }
}
