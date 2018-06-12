// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor

/**
 * Created by Max Medvedev on 10/02/14
 */
class TypeInference2_3Test extends TypeInferenceTestBase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return GroovyLightProjectDescriptor.GROOVY_2_3
  }

  void testContravariantType() throws Exception {
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
        wrap {
            1
        } contravariantTake {
            dub(i<caret>t) // fails static compile, 'it' is not known to be Integer
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
    customType { [] }.ad<caret>d(1) // return type is not inferred - fails compile
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
    first { [[]] }.ad<caret>d(1) // return type is not inferred - fails compile
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
        customType { [(1):3] }.pu<caret>t(1, 5) // return type is not inferred - fails compile
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
    exec('foo') {print i<caret>t.toUpperCase() ;print 2 }
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
    exec('foo') {i<caret>t.toUpperCase() }
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
    exec('foo') {print i<caret>t.toUpperCase() }
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
        customType { i<caret>t }
    }
}''', null)
  }


  void testClosureParamsUsingGenerics() {
   doTest("""\
    import groovy.transform.CompileStatic

    @CompileStatic
    class Idea {
      public static void main(String[] args) {
        ["bc", "a", ].sort { i<caret>t.size() }
      }
    }""", "java.lang.String")
  }

  void testSmartTypeOnDef() {
    doTest("""\
    class Idea {
      public static void main(String[] args) {
       def aa = new Object()
       aa = "as"
       a<caret>a
      }
    }""", "java.lang.String")
  }

  void testSmartType() {
    doTest("""\
    class Idea {
      public static void main(String[] args) {
       Object aa = new Object()
       aa = "as"
       a<caret>a
      }
    }""", "java.lang.String")
  }

  void testSmartTypeIf() {
    doTest("""\
    import groovy.transform.CompileStatic

    @CompileStatic
    class Idea {
      public static void main(String[] args) {
       Object aa = new Object()
       if (aa instanceof String) {
        a<caret>a
       }
       
      }
    }""", "java.lang.String")
  }

  void testSmartTypeIfOnDef() {
    doTest("""\
    class Idea {
      public static void main(String[] args) {
       def aa = new Object()
       if (aa instanceof String) {
        a<caret>a
       }
       
      }
    }""", "java.lang.String")
  }

  void testSmartTypeAssert() {
    doTest("""\
    class Idea {
      public static void main(String[] args) {
       Object aa = new Object()
       assert aa instanceof String 
       a<caret>a
       
       
      }
    }""", "java.lang.String")
  }

  void testSmartTypeAssertOnDef() {
    doTest("""\
    class Idea {
      public static void main(String[] args) {
       def aa = new Object()
       assert aa instanceof String 
       a<caret>a
       
       
      }
    }""", "java.lang.String")
  }
}
