// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.openapi.util.RecursionManager
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors

class LambdaTypeInferenceTest extends TypeInferenceTestBase {
  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_3_0

  void testPlusEquals() {
    doTest '''
class Test {
    def plus = (a) -> "a"
}
def test = new Test()
test += 2
te<caret>st
''', "java.lang.String"
  }

  void testGetAtLambda() {
    doTest '''
class Test {
    def getAt = a -> "a"
}
def test = new Test()
def test2 = test[2]

print t<caret>est2
''', "java.lang.String"
  }

  void 'test binding from inside'() {
    doTest "list = ['a', 'b']; list.each (it) -> <caret>it", "java.lang.String"
  }

  void testMethodCallInvokedOnArrayAccess() {
    doTest '''
def foo(String s) {
  return this
}

def bar(String s) {
  return 2
}

def foo = [(a)->{this}]

def var = foo[0] "a" bar "a"
print  va<caret>r
''', "java.lang.Integer"
  }

  void testInferWithClosureType() {
    doTest '''
class C {
  Date field
}

enum E {
  val((C c) -> {
    def data = c.with( (it) -> it.field )
    print d<caret>ata
  })
}''', "java.util.Date"
  }

  void '_test return type 1'() {
    doTest '''
class A {
  static def fact = (int i) -> {
    if (i > 1) {
      return call(i - 1)
    } else {
      return 1
    }
  }

  public static void main(String[] args) {
    def var = A.fact(5)
    println(<caret>var)
  }
}
''', "java.lang.Integer"
  }

  void 'test return type 2'() {
    RecursionManager.disableMissedCacheAssertions(testRootDisposable)
    doTest '''
class A {
  static def fact = (int i) -> {
    if (i > 1) {
      return A.fact(i - 1)
    } else {
      return 1
    }
  }

  public static void main(String[] args) {
    def var = A.fact(5)
    println(<caret>var)
  }
}
''', "java.lang.Integer"
  }

  void 'test return type 3'() {
    doTest '''
class A {
  static def fact = int i -> {
    if (i > 1) {
      return A.fact(i - 1)
    } else {
      return 1
    }
  }

  public static void main(String[] args) {
        Closure cl = A.fact
        int var = cl(5)
        println(<caret>var.intValue())
    }
}
''', "int"
  }

  void testWildcardClosureParam() {
    doTest('''
class Tx {
    def methodOfT() {}
}

def method(List<? extends Tx> t) {
    t.collect (it) -> { print i<caret>t }
}
''', '? extends Tx')
  }

  void testTypeOfGroupBy() {
    doTest('''
[1, 2, 3].with {
  def by = groupBy((i)->{2})
  print b<caret>y
}
''', 'java.util.Map<java.lang.Integer,java.util.List<java.lang.Integer>>')
  }

  void testTypeOfCall() {
    doTest('''
  def by = () -> {2}
  def res = by()
  re<caret>s
''', 'java.lang.Integer')
  }

  void testContravariantType() {
    doTest('''\
import groovy.transform.CompileStatic
import java.util.concurrent.Callable

@CompileStatic
class TestCase {

    interface Action<T> {
        void execute(T thing)
    }

    static class Wrapper<T> {

        private final T thing

        Wrapper(T thing) {
            this.thing = thing
        }

        void contravariantTake(Action<? super T> action) {
            action.execute(thing)
        }

    }

    static <T> Wrapper<T> wrap(Callable<T> callable) {
        new Wrapper(callable.call())
    }

    static Integer dub(Integer integer) {
        integer * 2
    }

    static void main(String[] args) {
        wrap(()-> 1).contravariantTake((it) -> dub(i<caret>t))
        }
    }
}
''', 'java.lang.Integer')
  }

  void testSAMInference() {
    doTest('''\
import groovy.transform.CompileStatic

interface CustomCallable<T> {
  T call()
}

class Thing {
  static <T> T customType(CustomCallable<T> callable) {
    callable.call()
  }

  @CompileStatic
  static void run() {
    customType( () -> [] ).ad<caret>d(1) 
  }
}
''', "boolean")
  }

  void testSAMInference2() {
    doTest('''\
import groovy.transform.CompileStatic

interface CustomCallable<T> {
  List<T> call()
}

class Thing {
  static <T> T first(CustomCallable<T> callable) {
    callable.call().iterator().next()
  }

  @CompileStatic
  static void run() {
    first(() -> [[]] ).ad<caret>d(1) 
  }
}
''', "boolean")
  }

  void testSAMInference3() {
    doTest('''\
import groovy.transform.CompileStatic

interface CustomCallable<K, V> {
    Map<K, V> call()
}

class Thing {
    static <K, V> Map<K, V> customType(CustomCallable<K, V> callable) {
        callable.call()
    }

    @CompileStatic
    static void run() {
        customType(() -> [(1):3] ).pu<caret>t(1, 5)
    }
}

''', 'java.lang.Integer')
  }

  void testSamInference4() {
    doTest('''
interface Action<T> {
    void execute(T t)
}

public <T> void exec(T t, Action<T> f) {
}


def foo() {
    exec('foo', it -> {print i<caret>t.toUpperCase() ;print 2 })
}

''', 'java.lang.String')
  }

  void testSamInference5() {
    doTest('''
interface Action<T> {
    void execute(T t)
}

public <T> void exec(T t, Action<T> f) {
}


def foo() {
    exec('foo', it -> i<caret>t.toUpperCase())
}

''', 'java.lang.String')
  }

  void testSamInference6() {
    doTest('''
interface Action<T> {
    void execute(T t)
}

public <T> void exec(T t, Action<T> f) {
}


def foo() {
    exec('foo', it -> {print i<caret>t.toUpperCase() })
}

''', 'java.lang.String')
  }

  void testSamInference7() {
    doTest('''
interface CustomCallable<T> {
    T call()
}

class Thing {
    static <T> T customType(CustomCallable<T> callable) {
    }

    static void run() {
        customType (it) -> i<caret>t
    }
}''', null)
  }


  void testClosureParamsUsingGenerics() {
    doTest("""\
    import groovy.transform.CompileStatic

    @CompileStatic
    class Idea {
      public static void main(String[] args) {
        ["bc", "a", ].sort (it) -> i<caret>t.size() 
      }
    }""", "java.lang.String")
  }

  void '_test owner type'() {
    doTest '''\
class W {
  def c = ()->{
    <caret>owner
  }
}
''', 'W'
  }

  void 'test use parent DFA'() {
    doTest '''
  def foo(a) {
    a = 1
    1.with(x -> {
      <caret>a
    })
  }
''', 'java.lang.Integer'
  }

  void 'test use outer types inside unknown lambdas'() {
    doTest '''
def foo() {
  def a = 1
  lambda = x -> {
    <caret>a
  }
  a = ""
}
''', 'java.lang.Integer'
  }
}
