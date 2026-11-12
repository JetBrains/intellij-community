// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.introduce.field;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyFixtures;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.IntroduceConstantTest;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static com.intellij.refactoring.introduce.inplace.OccurrencesChooser.ReplaceChoice.ALL;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.jetbrains.plugins.groovy.refactoring.introduce.field.GrIntroduceFieldSettings.Init.CONSTRUCTOR;
import static org.jetbrains.plugins.groovy.refactoring.introduce.field.GrIntroduceFieldSettings.Init.CUR_METHOD;
import static org.jetbrains.plugins.groovy.refactoring.introduce.field.GrIntroduceFieldSettings.Init.FIELD_DECLARATION;
import static org.jetbrains.plugins.groovy.refactoring.introduce.field.GrIntroduceFieldSettings.Init.SETUP_METHOD;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Maxim.Medvedev
 */
@TestApplication
public class GrIntroduceFieldTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "refactoring/introduceField/");

  @Test
  public void testSimple() {
    doTest(false, false, CUR_METHOD, false);
  }

  @Test
  public void testDeclareFinal() {
    doTest(false, true, FIELD_DECLARATION, false);
  }

  @Test
  public void testCreateConstructor() {
    doTest(false, true, CONSTRUCTOR, true);
  }

  @Test
  public void testManyConstructors() {
    doTest(false, true, CONSTRUCTOR, true);
  }

  @Test
  public void testDontReplaceStaticOccurrences() {
    doTest(false, true, FIELD_DECLARATION, true);
  }

  @Test
  public void testQualifyUsages() {
    doTest(false, true, FIELD_DECLARATION, true);
  }

  @Test
  public void testReplaceLocalVar() {
    doTest(true, false, CUR_METHOD, true);
  }

  @Test
  public void testIntroduceLocalVarByDeclaration() {
    doTest(true, false, FIELD_DECLARATION, true);
  }

  @Test
  public void testReplaceExpressionWithAssignment() {
    doTest(false, false, CUR_METHOD, false);
  }

  @Test
  public void testAnonymousClass() {
    doTest(false, false, CUR_METHOD, false);
  }

  @Test
  public void testAnonymous2() {
    doTest(false, false, CONSTRUCTOR, false);
  }

  @Test
  public void testAnonymous3() {
    doTest(false, false, CONSTRUCTOR, false);
  }

  @Test
  public void testInitializeInCurrentMethod() {
    doTest(true, true, CUR_METHOD, false);
  }

  @Test
  public void testScriptBody() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    LightGroovyFixtures.addGroovyTransformField(myFixture);
    doTest("print <selection>'abc'</selection>\n", """
             import groovy.transform.Field
             
             @Field f = 'abc'
             print f<caret>
             """, false,
           false, FIELD_DECLARATION);
  }

  @Test
  public void testScriptMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    LightGroovyFixtures.addGroovyTransformField(myFixture);
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

  @Test
  public void testStaticScriptMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    LightGroovyFixtures.addGroovyTransformField(myFixture);
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

  @Test
  public void testScriptMethod2() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    LightGroovyFixtures.addGroovyTransformField(myFixture);
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

  @Test
  public void testSetUp1() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    LightGroovyFixtures.addTestCase(myFixture);
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

  @Test
  public void testSetUp2() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    LightGroovyFixtures.addTestCase(myFixture);

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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void test_introduce_field_in_script_with_invalid_class_name() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("abcd-efgh.groovy", """
      def aaa = "foo"
      def bbb = "bar"
      println(<selection>aaa + bbb</selection>)
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      performRefactoring(false, false, false, CUR_METHOD, false);
      myFixture.checkResult("""
                              import groovy.transform.Field
                              
                              @Field f
                              def aaa = "foo"
                              def bbb = "bar"
                              f = aaa + bbb
                              println(f)
                              """);
    });
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".groovy");
    EdtTestUtil.runInEdtAndWait(() -> {
      performRefactoring(false, removeLocal, declareFinal, initIn, replaceAll);
      myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.groovy");
    });
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
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("_.groovy", textBefore);
      performRefactoring(isStatic, removeLocal, declareFinal, initIn, replaceAll);
      myFixture.checkResult(textAfter);
    });
  }

  private void performRefactoring(boolean isStatic,
                                  boolean removeLocal,
                                  boolean declareFinal,
                                  GrIntroduceFieldSettings.Init initIn,
                                  boolean replaceAll) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      final IntroduceFieldTestHandler handler = new IntroduceFieldTestHandler(isStatic, removeLocal, declareFinal, initIn, replaceAll, null);
      handler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), null);
      PostprocessReformattingAspect.getInstance(myFixture.getProject()).doPostponedFormatting();
    });
  }

  private void doTestInitInTarget(String text,
                                  EnumSet<GrIntroduceFieldSettings.Init> expected,
                                  OccurrencesChooser.ReplaceChoice replaceChoice) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, text);
      ReadAction.runBlocking(() -> {
        GrIntroduceFieldHandler handler = new GrIntroduceFieldHandler();

        GrExpression expression = IntroduceConstantTest.findExpression(myFixture);
        GrVariable variable = IntroduceConstantTest.findVariable(myFixture);
        StringPartInfo stringPart = IntroduceConstantTest.findStringPart(myFixture);
        PsiClass[] scopes = handler.findPossibleScopes(expression, variable, stringPart, myFixture.getEditor());
        assertThat(scopes).hasSize(1);
        PsiClass scope = scopes[0];

        GrIntroduceContext context = handler.getContext(myFixture.getProject(), myFixture.getEditor(), expression, variable, stringPart, scope);
        EnumSet<GrIntroduceFieldSettings.Init> initPlaces =
          GrInplaceFieldIntroducer.getApplicableInitPlaces(context, replaceChoice.equals(ALL));
        assertEquals(expected, initPlaces);
      });
    });
  }

  private void doTestInitInTarget(String text, EnumSet<GrIntroduceFieldSettings.Init> expected) {
    doTestInitInTarget(text, expected, ALL);
  }
}
