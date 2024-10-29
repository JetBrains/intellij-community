// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.introduce.field;

import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.IntroduceConstantTest;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.EnumSet;

import static com.intellij.refactoring.introduce.inplace.OccurrencesChooser.ReplaceChoice.ALL;
import static org.jetbrains.plugins.groovy.refactoring.introduce.field.GrIntroduceFieldSettings.Init.*;

/**
 * @author Maxim.Medvedev
 */
public class GrIntroduceFieldTest extends LightGroovyTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "refactoring/introduceField/";
  }

  public void testSimple() {
    doTest(false, false, CUR_METHOD, false);
  }

  public void testDeclareFinal() {
    doTest(false, true, FIELD_DECLARATION, false);
  }

  public void testCreateConstructor() {
    doTest(false, true, CONSTRUCTOR, true);
  }

  public void testManyConstructors() {
    doTest(false, true, CONSTRUCTOR, true);
  }

  public void testDontReplaceStaticOccurrences() {
    doTest(false, true, FIELD_DECLARATION, true);
  }

  public void testQualifyUsages() {
    doTest(false, true, FIELD_DECLARATION, true);
  }

  public void testReplaceLocalVar() {
    doTest(true, false, CUR_METHOD, true);
  }

  public void testIntroduceLocalVarByDeclaration() {
    doTest(true, false, FIELD_DECLARATION, true);
  }

  public void testReplaceExpressionWithAssignment() {
    doTest(false, false, CUR_METHOD, false);
  }

  public void testAnonymousClass() {
    doTest(false, false, CUR_METHOD, false);
  }

  public void testAnonymous2() {
    doTest(false, false, CONSTRUCTOR, false);
  }

  public void testAnonymous3() {
    doTest(false, false, CONSTRUCTOR, false);
  }

  public void testInitializeInCurrentMethod() {
    doTest(true, true, CUR_METHOD, false);
  }

  public void testScriptBody() {
    addGroovyTransformField();
    doTest("print <selection>'abc'</selection>\n", """
             import groovy.transform.Field
             
             @Field f = 'abc'
             print f<caret>
             """, false,
           false, FIELD_DECLARATION);
  }

  public void testScriptMethod() {
    addGroovyTransformField();
    doTest("""
             def foo() {
               print <selection>'abc'</selection>
             }
             """,
           """
             import groovy.transform.Field
             
             @Field final f = 'abc'
             
             def foo() {
               print f<caret>
             }
             """, false, true,
           FIELD_DECLARATION);
  }

  public void testStaticScriptMethod() {
    addGroovyTransformField();
    doTest("""
             static def foo() {
               print <selection>'abc'</selection>
             }
             """,
           """
             import groovy.transform.Field
             
             @Field static f = 'abc'
             
             static def foo() {
               print f<caret>
             }
             """, true, false,
           FIELD_DECLARATION);
  }

  public void testScriptMethod2() {
    addGroovyTransformField();
    doTest("""
             def foo() {
               print <selection>'abc'</selection>
             }
             """,
           """
             import groovy.transform.Field
             
             @Field f
             
             def foo() {
                 f = 'abc'
                 print f<caret>
             }
             """, false, false,
           CUR_METHOD);
  }

  public void testSetUp1() {
    addTestCase();
    doTest("""
             class MyTest extends GroovyTestCase {
                 void foo() {
                     print <selection>'ac'</selection>
                 }
             }
             """,
           """
             class MyTest extends GroovyTestCase {
                 def f
             
                 void foo() {
                     print f<caret>
                 }
             
                 void setUp() {
                     super.setUp()
                     f = 'ac'
                 }
             }
             """,
           false, false, SETUP_METHOD);
  }

  public void testSetUp2() {
    addTestCase();

    doTest(
      """
        class MyTest extends GroovyTestCase {
            void setUp() {
                super.setUp()
                def x = 'abc'
            }
        
            void foo() {
                print <selection>'ac'</selection>
            }
        }
        """,
      """
        class MyTest extends GroovyTestCase {
            def f
        
            void setUp() {
                super.setUp()
                def x = 'abc'
                f = 'ac'
            }
        
            void foo() {
                print f<caret>
            }
        }
        """,
      false, false, SETUP_METHOD);
  }

  public void testStringPart0() {
    doTest("""
             class A {
                 def foo() {
                     print 'a<selection>b</selection>c'
                 }
             }""",
           """
             class A {
                 def f = 'b'
             
                 def foo() {
                     print 'a' + f<caret> + 'c'
                 }
             }""", false, false, false,
           FIELD_DECLARATION, false);
  }

  public void testStringPart1() {
    doTest("""
             class A {
                 def foo() {
                     print 'a<selection>b</selection>c'
                 }
             }""",
           """
             class A {
                 def f
             
                 def foo() {
                     f = 'b'
                     print 'a' + f<caret> + 'c'
                 }
             }""", false, false, false,
           CUR_METHOD, false);
  }

  public void testStringPart2() {
    doTest("""
             class A {
                 def foo() {
                     def f = 5
                     print 'a<selection>b</selection>c'
                 }
             }""",
           """
             class A {
                 def f
             
                 def foo() {
                     def f = 5
                     this.f = 'b'
                     print 'a' + this.f<caret> + 'c'
                 }
             }""",
           false, false, false, CUR_METHOD, false);
  }

  public void testGStringInjection() {
    doTest(
      """
        class GroovyLightProjectDescriptor  {
            public void configureModule() {
                print ("$<selection>mockGroovy2_1LibraryName</selection>!/");
            }
        
            def getMockGroovy2_1LibraryName() {''}
        }
        """,
      """
        class GroovyLightProjectDescriptor  {
            def f
        
            public void configureModule() {
                f = mockGroovy2_1LibraryName
                print ("${f}!/");
            }
        
            def getMockGroovy2_1LibraryName() {''}
        }
        """,
      false, false, CUR_METHOD);
  }

  public void testGStringInjection2() {
    doTest(
      """
        class GroovyLightProjectDescriptor  {
            public void configureModule() {
                print ("$<selection>mockGroovy2_1LibraryName</selection>.bytes!/");
            }
        
            def getMockGroovy2_1LibraryName() {''}
        }
        """,
      """
        class GroovyLightProjectDescriptor  {
            def f
        
            public void configureModule() {
                f = mockGroovy2_1LibraryName
                print ("${f.bytes}!/");
            }
        
            def getMockGroovy2_1LibraryName() {''}
        }
        """,
      false, false, CUR_METHOD);
  }

  public void test_GString_closure_injection_and_initialize_in_current_method() {
    doTest(
      """
        class GroovyLightProjectDescriptor  {
            public void configureModule() {
                print ("${<selection>mockGroovy2_1LibraryName</selection>}!/");
            }
        
            def getMockGroovy2_1LibraryName() {''}
        }
        """,
      """
        class GroovyLightProjectDescriptor  {
            def f
        
            public void configureModule() {
                f = mockGroovy2_1LibraryName
                print ("${f}!/");
            }
        
            def getMockGroovy2_1LibraryName() {''}
        }
        """,
      false, false, CUR_METHOD);
  }

  public void testInitializeInMethodInThenBranch() {
    doTest("""
             class A {
                 def foo() {
                     if (abc) print <selection>2</selection>
                 }
             }
             """,
           """
             class A {
                 def f
             
                 def foo() {
                     if (abc) {
                         f = 2
                         print f
                     }
                 }
             }
             """,
           false, false, false, CUR_METHOD, false);
  }

  public void testFromVar() {
    doTest("""
             class A {
                 def foo() {
                     def <selection>a = 5</selection>
                     print a
                 }
             }""",
           """
             class A {
                 def f = 5
             
                 def foo() {
                     print f
                 }
             }""", false, true, false, FIELD_DECLARATION, true);
  }

  public void test_replace_top_level_expression_within_constructor_and_initialize_in_current_method() {
    doTest("""
             class TestClass {
                 TestClass() {
                     new St<caret>ring()
                 }
             
                 TestClass(a) {
                 }
             }
             """,
           """
             class TestClass {
                 def f
             
                 TestClass() {
                     f = new String()
                 }
             
                 TestClass(a) {
                 }
             }
             """, false,
           false, CUR_METHOD);
  }

  public void test_replace_top_level_expression_within_constructor_and_initialize_field() {
    doTest("""
             class TestClass {
                 TestClass() {
                     new St<caret>ring()
                 }
             
                 TestClass(a) {
                 }
             }
             """,
           """
             class TestClass {
                 def f = new String()
             
                 TestClass() {
                     f
                 }
             
                 TestClass(a) {
                 }
             }
             """, false,
           false, FIELD_DECLARATION);
  }

  public void test_replace_top_level_expression_within_constructor_and_initialize_in_constructor() {
    doTest("""
             class TestClass {
                 TestClass() {
                     new St<caret>ring()
                 }
             
                 TestClass(a) {
                 }
             }
             """,
           """
             class TestClass {
                 def f
             
                 TestClass() {
                     f = new String()
                 }
             
                 TestClass(a) {
                     f = new String()
                 }
             }
             """,
           false, false, CONSTRUCTOR);
  }

  public void test_replace_non_top_level_expression_within_constructor_and_initialize_in_current_method() {
    doTest(
      """
        class TestClass {
            TestClass() {
                <selection>new String()</selection>.empty
            }
        
            TestClass(a) {
            }
        }
        """,
      """
        class TestClass {
            def f
        
            TestClass() {
                f = new String()
                f.empty
            }
        
            TestClass(a) {
            }
        }
        """,
      false, false, CUR_METHOD);
  }

  public void test_replace_non_top_level_expression_within_constructor_and_initialize_field() {
    doTest(
      """
        class TestClass {
            TestClass() {
                <selection>new String()</selection>.empty
            }
        
            TestClass(a) {
            }
        }
        """,
      """
        class TestClass {
            def f = new String()
        
            TestClass() {
                f.empty
            }
        
            TestClass(a) {
            }
        }
        """, false,
      false, FIELD_DECLARATION);
  }

  public void test_replace_non_top_level_expression_within_constructor_and_initialize_in_constructor() {
    doTest(
      """
        class TestClass {
            TestClass() {
                <selection>new String()</selection>.empty
            }
        
            TestClass(a) {
            }
        }
        """,
      """
        class TestClass {
            def f
        
            TestClass() {
                f = new String()
                f.empty
            }
        
            TestClass(a) {
                f = new String()
            }
        }
        """,
      false, false, CONSTRUCTOR);
  }

  public void test_replace_string_injection_and_initialize_in_constructor() {
    doTest(
      """
        class TestClass {
            TestClass() {
                "${<selection>new String()</selection>}"
            }
            TestClass(a) {
            }
        }
        """,
      """
        class TestClass {
            def f
        
            TestClass() {
                f = new String()
                "${f}"
            }
            TestClass(a) {
                f = new String()
            }
        }
        """,
      false, false, CONSTRUCTOR);
  }

  public void test_introduce_field_in_script_with_invalid_class_name() {
    myFixture.configureByText("abcd-efgh.groovy", """
      def aaa = "foo"
      def bbb = "bar"
      println(<selection>aaa + bbb</selection>)
      """);
    performRefactoring(false, false, false, CUR_METHOD, false);
    myFixture.checkResult("""
                            import groovy.transform.Field
                            
                            @Field f
                            def aaa = "foo"
                            def bbb = "bar"
                            f = aaa + bbb
                            println(f)
                            """);
  }

  public void test_cannot_initialize_in_current_method_when_introducing_from_field_initializer() {
    doTestInitInTarget("""
                         
                         class A {
                           def object = <selection>new Object()</selection>
                         }
                         """, EnumSet.of(CONSTRUCTOR, FIELD_DECLARATION));

    doTestInitInTarget("""
                         
                         class A {
                           def object = <selection>new Object()</selection>
                           def object2 = new Object()
                         }
                         """,
                       EnumSet.of(CONSTRUCTOR, FIELD_DECLARATION));

    doTestInitInTarget("""
                         
                         class A {
                           def object = <selection>new Object()</selection>
                           def object2 = new Object()
                         }
                         """,
                       EnumSet.of(CONSTRUCTOR, FIELD_DECLARATION), OccurrencesChooser.ReplaceChoice.NO);
  }

  public void test_can_not_initialize_in_current_method_with_some_occurence_outside() {
    doTestInitInTarget("""
                         
                         class A {
                           def field = new Object()
                           def foo() {
                             def a = <selection>new Object()</selection>
                           }
                         }
                         """,
                       EnumSet.of(CONSTRUCTOR, FIELD_DECLARATION));
  }

  public void test_can_initialize_in_current_method_from_within_method() {
    doTestInitInTarget("""
                         
                         class A {
                           def foo() {
                             def a = <selection>new Object()</selection>
                           }
                         }
                         """,
                       EnumSet.of(CONSTRUCTOR, FIELD_DECLARATION, CUR_METHOD));

    doTestInitInTarget("""
                         
                         class A {
                           def field = new Object()
                           def foo() {
                             def a = <selection>new Object()</selection>
                           }
                         }
                         """,
                       EnumSet.of(CONSTRUCTOR, FIELD_DECLARATION, CUR_METHOD), OccurrencesChooser.ReplaceChoice.NO);
  }

  public void test_can_initialize_script_field_in_current_method_only() {
    doTestInitInTarget("""
                         
                         def a = 1
                         def b = 2
                         println(<selection>a + b</selection>)
                         """, EnumSet.of(CUR_METHOD));

    doTestInitInTarget("""
                         
                         def a = 1
                         def b = 2
                         println(<selection>a + b</selection>)
                         """, EnumSet.of(CUR_METHOD),
                       OccurrencesChooser.ReplaceChoice.NO);

    doTestInitInTarget("""
                         
                         def a = 1
                         def b = 2
                         def c = a + b
                         println(<selection>a + b</selection>)
                         """, EnumSet.of(CUR_METHOD));

    doTestInitInTarget("""
                         
                         def a = 1
                         def b = 2
                         def c = a + b
                         println(<selection>a + b</selection>)
                         """, EnumSet.of(CUR_METHOD),
                       OccurrencesChooser.ReplaceChoice.NO);
  }

  public void test_introduce_field_from_this() {
    doTest("""
             class A {
                 def bar\s
                 def foo() {
                     th<caret>is.bar
                 }
             }
             """,
           """
             class A {
                 def bar
                 def f = this
             
                 def foo() {
                     f.bar
                 }
             }
             """, false, false,
           FIELD_DECLARATION);
  }

  private void doTest(final boolean removeLocal,
                      final boolean declareFinal,
                      @NotNull final GrIntroduceFieldSettings.Init initIn,
                      final boolean replaceAll) {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    performRefactoring(false, removeLocal, declareFinal, initIn, replaceAll);
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  private void doTest(@NotNull final String textBefore,
                      @NotNull String textAfter,
                      final boolean isStatic,
                      final boolean declareFinal,
                      @NotNull final GrIntroduceFieldSettings.Init initIn) {
    doTest(textBefore, textAfter, isStatic, false, declareFinal, initIn, false);
  }

  private void doTest(@NotNull final String textBefore,
                      @NotNull String textAfter,
                      final boolean isStatic,
                      final boolean removeLocal,
                      final boolean declareFinal,
                      @NotNull final GrIntroduceFieldSettings.Init initIn,
                      final boolean replaceAll) {
    myFixture.configureByText("_.groovy", textBefore);
    performRefactoring(isStatic, removeLocal, declareFinal, initIn, replaceAll);
    myFixture.checkResult(textAfter);
  }

  private void performRefactoring(boolean isStatic,
                                  boolean removeLocal,
                                  boolean declareFinal,
                                  GrIntroduceFieldSettings.Init initIn,
                                  boolean replaceAll) {
    final IntroduceFieldTestHandler handler = new IntroduceFieldTestHandler(isStatic, removeLocal, declareFinal, initIn, replaceAll, null);
    handler.invoke(getProject(), myFixture.getEditor(), myFixture.getFile(), null);
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
  }

  private void doTestInitInTarget(String text,
                                  EnumSet<GrIntroduceFieldSettings.Init> expected,
                                  OccurrencesChooser.ReplaceChoice replaceChoice) {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, text);
    GrIntroduceFieldHandler handler = new GrIntroduceFieldHandler();

    GrExpression expression = IntroduceConstantTest.findExpression(myFixture);
    GrVariable variable = IntroduceConstantTest.findVariable(myFixture);
    StringPartInfo stringPart = IntroduceConstantTest.findStringPart(myFixture);
    PsiClass[] scopes = handler.findPossibleScopes(expression, variable, stringPart, getEditor());
    assert scopes.length == 1;
    PsiClass scope = scopes[0];

    GrIntroduceContext context = handler.getContext(getProject(), myFixture.getEditor(), expression, variable, stringPart, scope);
    EnumSet<GrIntroduceFieldSettings.Init> initPlaces =
      GrInplaceFieldIntroducer.getApplicableInitPlaces(context, replaceChoice.equals(ALL));
    assert DefaultGroovyMethods.equals(initPlaces, expected);
  }

  private void doTestInitInTarget(String text, EnumSet<GrIntroduceFieldSettings.Init> expected) {
    doTestInitInTarget(text, expected, ALL);
  }
}
