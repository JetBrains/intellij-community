// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.junit.Test;

public class TupleConstructorTest extends GroovyLatestTest implements HighlightingTest {
  @Test
  public void manyConstructors() {
    highlightingTest(
      """
        @groovy.transform.TupleConstructor
        class Rr {
            String actionType = ""
            long referrerCode;
            String referrerUrl;
        }
        
        @groovy.transform.CompileStatic
        static void main(String[] args) {
            new Rr("")
            new Rr("", 1)
            new Rr("", 1, "groovy")
        }
        """);
  }

  @Test
  public void manyConstructorsWithExcludes() {
    highlightingTest(
      """
        @groovy.transform.TupleConstructor(excludes = ['actionType'])
        class Rr {
            String actionType = ""
            long referrerCode;
            String referrerUrl;
        }
        
        @groovy.transform.CompileStatic
        static void main(String[] args) {
            new Rr()
            new Rr(1)
            new Rr(1, "groovy")
        }
        """);
  }

  @Test
  public void manyConstructorsWithIncludes() {
    highlightingTest(
      """
        @groovy.transform.TupleConstructor(includes = ['actionType', 'referrerUrl'])
        class Rr {
            String actionType = ""
            long referrerCode;
            String referrerUrl;
        }
        
        @groovy.transform.CompileStatic
        static void main(String[] args) {
            new Rr()
            new Rr("")
            new Rr("a", "groovy")
        }
        """);
  }

  @Test
  public void manyConstructorsWithRawIncludes() {
    highlightingTest(
      """
        @groovy.transform.TupleConstructor(includes = 'actionType,  referrerUrl ')
        class Rr {
            String actionType = ""
            long referrerCode;
            String referrerUrl;
        }
        
        @groovy.transform.CompileStatic
        static void main(String[] args) {
            new Rr()
            new Rr("")
            new Rr("a", "groovy")
        }
        """);
  }

  @Test
  public void includesInducesOrderOfParameters() {
    highlightingTest(
      """
        @groovy.transform.TupleConstructor(includes = 'referrerUrl, actionType ')
        class Rr {
            String actionType = ""
            long referrerCode;
            boolean referrerUrl;
        }
        
        @groovy.transform.CompileStatic
        static void main(String[] args) {
            new Rr(true, "groovy")
        }
        """);
  }

  @Test
  public void internalNamesAreNotAmongParameters() {
    highlightingTest(
      """
        @groovy.transform.TupleConstructor()
        class Rr {
            String $actionType = ""
        }
        
        @groovy.transform.CompileStatic
        static void main(String[] args) {
            new Rr<error>("")</error>
        }
        """);
  }

  @Test
  public void includeInternalNames() {
    highlightingTest(
      """
        @groovy.transform.TupleConstructor(allNames = true)
        class Rr {
            String $actionType = ""
        }
        
        @groovy.transform.CompileStatic
        static void main(String[] args) {
            new Rr("")
        }
        """);
  }

  @Test
  public void includesDoesNotAffectInternalName() {
    highlightingTest(
      """
        @groovy.transform.TupleConstructor(includes = "$actionType")
        class Rr {
            String $actionType = ""
        }
        
        @groovy.transform.CompileStatic
        static void main(String[] args) {
            new Rr<error>("")</error>
        }
        """);
  }

  @Test
  public void defaultsRemovesAdditionalConstructors() {
    highlightingTest(
      """
        @groovy.transform.TupleConstructor(defaults = false)
        class Rr {
            String actionType
            long referrerCode;
            boolean referrerUrl;
        }
        
        @groovy.transform.CompileStatic
        static void main(String[] args) {
            new Rr<error>("")</error>
            new Rr<error>("", 1)</error>
            new Rr("", 1, true)
            new Rr<error>(actionType: "a", referrerUrl: true, referrerCode: 1)</error>
        }
        """);
  }

  @Test
  public void defaultsWithSuperclass() {
    highlightingTest(
      """
        class NN {
            String top
        }
        
        @groovy.transform.TupleConstructor(defaults = false, includeSuperProperties = true)
        class Rr extends NN {
            String actionType
            long referrerCode;
            boolean referrerUrl;
        }
        
        @groovy.transform.CompileStatic
        static void main(String[] args) {
            new Rr<error>("")</error>
            new Rr<error>("", 1)</error>
            new Rr<error>("", 1, true)</error>
            new Rr("", "", 1, true)
            new Rr<error>(actionType: "a", referrerUrl: true, referrerCode: 1)</error>
        }""");
  }

  @Test
  public void allPropertiesEnableJavaBeanSupport() {
    highlightingTest(
      """
        @groovy.transform.TupleConstructor(allProperties = true)
        class Rr {
            Closure actionType
            long referrerCode;
        
            void setProp(String s) {
        
            }
        
            private int referrerId;
        
            boolean referrerUrl;
        }
        
        @groovy.transform.CompileStatic
        static void main(String[] args) {
            new Rr({})
            new Rr({}, 1)
            new Rr({}, 1, true)
            new Rr({}, 1, true, "")
            new Rr(actionType: {}, referrerUrl: true, referrerCode: 1, prop: "a")
        }""");
  }

  @Test
  public void allPropertiesDoNotAffectSuperclasses() {
    highlightingTest(
      """
        @groovy.transform.TupleConstructor(allProperties = true, includeFields = true)
        class NN {
            public int r
            String s
            void setMp(boolean t) {
        
            }
        }
        
        @groovy.transform.TupleConstructor(allProperties = true, includeSuperFields = true, includeSuperProperties = true)
        class Rr extends NN {
            Closure actionType
        }
        
        @groovy.transform.CompileStatic
        static void main(String[] args) {
            new Rr("", 1, {})
            new Rr<error>(1, true, "", {})</error>
        }""");
  }

  @Test
  public void closuresInAnnotationHaveAccessToClassMembers() {
    highlightingTest(
      """
        @groovy.transform.CompileStatic
        @groovy.transform.TupleConstructor(pre = { foo() }, post = { q == 1 })
        class Rr {
            int q
            def foo() {}
        }""");
  }

  @Test
  public void simultaneousIncludesAndExcludes() {
    highlightingTest(
      """
        @groovy.transform.TupleConstructor(<error>includes = "a"</error>, <error>excludes = ['b']</error>)
        class Rr {}
        """);
  }

  @Test
  public void emptyExcludes() {
    highlightingTest(
      """
        @groovy.transform.TupleConstructor(includes = "a", excludes = [])
        class Rr {}
        """);
  }

  @Test
  public void wrongExpressionsAtPreAndPost() {
    highlightingTest(
      """
        @groovy.transform.TupleConstructor(pre = <error>Integer</error>, post = <error>String</error>)
        class Rr {}
        """);
  }

  @Test
  public void forbiddenInitializer() {
    highlightingTest(
      """
        @groovy.transform.TupleConstructor(defaults = false, includes = ['a', 'b'])
        class Rr {
          String a = <error>""</error>
          int b = <error>1000</error>
          boolean c = true
        }
        """);
  }

  @Test
  public void testVisibilityOptions() {
    getFixture().addFileToProject("other.groovy",
                                  """
                                    @groovy.transform.CompileStatic
                                    @groovy.transform.TupleConstructor(defaults = false)
                                    @groovy.transform.VisibilityOptions(constructor = Visibility.PRIVATE)
                                    class Cde {
                                        String actionType
                                        long referrerCode
                                        boolean referrerUrl
                                    }""");
    highlightingTest(
      """
        class X {
        
            @groovy.transform.CompileStatic
            static void main(String[] args) {
                def x = new <error>Cde</error>("mem", 1, true)
            }
        
        }""");
  }

  @Test
  public void testVisibilityOptionsWithValue() {
    getFixture().addFileToProject("other.groovy",
                                  """
                                    @groovy.transform.CompileStatic
                                    @groovy.transform.TupleConstructor(defaults = false)
                                    @groovy.transform.VisibilityOptions(Visibility.PRIVATE)
                                    class Cde {
                                        String actionType
                                        long referrerCode
                                        boolean referrerUrl
                                    }""");
    highlightingTest(
      """
        class X {
        
            @groovy.transform.CompileStatic
            static void main(String[] args) {
                def x = new <error>Cde</error>("mem", 1, true)
            }
        
        }""");
  }

  @Test
  public void superResolveForPre() {
    highlightingTest("""
                       class NN { NN(String s) {} }
                       
                       @groovy.transform.CompileStatic
                       @groovy.transform.TupleConstructor(pre = { super("") })
                       class Rr extends NN {
                       }""");
  }

  @Test
  public void superConstructorHighlighting() {
    highlightingTest(
      """
        class Nn {
            Nn(int a) {}
        }
        
        <error>@groovy.transform.TupleConstructor
        class Rr extends Nn</error> {
            String actionType
        }
        
        @groovy.transform.CompileStatic
        static void main(String[] args) {
            def x = new Rr("")
        }""");
  }

  @Test
  public void preHighlighting() {
    highlightingTest(
      """
        class NN { NN(String s) {} }
        
        @groovy.transform.CompileStatic
        @groovy.transform.TupleConstructor(<error>pre = { }</error>)
        class Rr extends NN {
        }""");
  }

  @Test
  public void preHighlighting2() {
    highlightingTest(
      """
        class NN { }
        
        @groovy.transform.CompileStatic
        @groovy.transform.TupleConstructor(pre = { super() }, <error>callSuper = true</error>)
        class Rr extends NN {
        }""");
  }

  @Test
  public void finalFieldsInConstructor() {
    highlightingTest(
      """
        @groovy.transform.CompileStatic
        @groovy.transform.TupleConstructor(includeFields = true)
        class Rr {
          private final int a = 1
          private final boolean b
          String c
        }
        
        @groovy.transform.CompileStatic
        static void main(String[] args) {
            new Rr<error>("", 2)</error>
            new Rr("", true)
        }""");
  }

  @Test
  public void innerClass() {
    highlightingTest(
      """
        @groovy.transform.CompileStatic
        class DtoTest {
        
            @groovy.transform.TupleConstructor
            class Dto {
                String value
            }
        
            void useGeneratedConstructor() {
                new Dto("abc")
            }
        }
        """);
  }
}
