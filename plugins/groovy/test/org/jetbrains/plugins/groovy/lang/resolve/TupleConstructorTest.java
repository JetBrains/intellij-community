// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;

@TestApplication
public class TupleConstructorTest implements HighlightingTest {
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(GroovyProjectDescriptors.GROOVY_2_5);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  @Test
  public void manyConstructors() {
    runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void manyConstructorsWithExcludes() {
    runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void manyConstructorsWithIncludes() {
    runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void manyConstructorsWithRawIncludes() {
    runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void includesInducesOrderOfParameters() {
    runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void internalNamesAreNotAmongParameters() {
    runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void includeInternalNames() {
    runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void includesDoesNotAffectInternalName() {
    runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void defaultsRemovesAdditionalConstructors() {
    runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void defaultsWithSuperclass() {
    runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void allPropertiesEnableJavaBeanSupport() {
    runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void allPropertiesDoNotAffectSuperclasses() {
    runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void closuresInAnnotationHaveAccessToClassMembers() {
    runInEdtAndWait(() -> {
      highlightingTest(
        """
          @groovy.transform.CompileStatic
          @groovy.transform.TupleConstructor(pre = { foo() }, post = { q == 1 })
          class Rr {
              int q
              def foo() {}
          }""");
    });
  }

  @Test
  public void simultaneousIncludesAndExcludes() {
    runInEdtAndWait(() -> {
      highlightingTest(
        """
          @groovy.transform.TupleConstructor(<error>includes = "a"</error>, <error>excludes = ['b']</error>)
          class Rr {}
          """);
    });
  }

  @Test
  public void emptyExcludes() {
    runInEdtAndWait(() -> {
      highlightingTest(
        """
          @groovy.transform.TupleConstructor(includes = "a", excludes = [])
          class Rr {}
          """);
    });
  }

  @Test
  public void wrongExpressionsAtPreAndPost() {
    runInEdtAndWait(() -> {
      highlightingTest(
        """
          @groovy.transform.TupleConstructor(pre = <error>Integer</error>, post = <error>String</error>)
          class Rr {}
          """);
    });
  }

  @Test
  public void forbiddenInitializer() {
    runInEdtAndWait(() -> {
      highlightingTest(
        """
          @groovy.transform.TupleConstructor(defaults = false, includes = ['a', 'b'])
          class Rr {
            String a = <error>""</error>
            int b = <error>1000</error>
            boolean c = true
          }
          """);
    });
  }

  @Test
  public void testVisibilityOptions() {
    runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testVisibilityOptionsWithValue() {
    runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void superResolveForPre() {
    runInEdtAndWait(() -> {
      highlightingTest("""
                         class NN { NN(String s) {} }
                       
                         @groovy.transform.CompileStatic
                         @groovy.transform.TupleConstructor(pre = { super("") })
                         class Rr extends NN {
                         }""");
    });
  }

  @Test
  public void superConstructorHighlighting() {
    runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void preHighlighting() {
    runInEdtAndWait(() -> {
      highlightingTest(
        """
          class NN { NN(String s) {} }
        
          @groovy.transform.CompileStatic
          @groovy.transform.TupleConstructor(<error>pre = { }</error>)
          class Rr extends NN {
          }""");
    });
  }

  @Test
  public void preHighlighting2() {
    runInEdtAndWait(() -> {
      highlightingTest(
        """
          class NN { }
        
          @groovy.transform.CompileStatic
          @groovy.transform.TupleConstructor(pre = { super() }, <error>callSuper = true</error>)
          class Rr extends NN {
          }""");
    });
  }

  @Test
  public void finalFieldsInConstructor() {
    runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void innerClass() {
    runInEdtAndWait(() -> {
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
    });
  }
}
