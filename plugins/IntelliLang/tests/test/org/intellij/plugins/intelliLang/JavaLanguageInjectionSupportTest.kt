// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang

import com.intellij.lang.Language
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.injection.Injectable
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.util.ui.UIUtil
import junit.framework.TestCase
import org.intellij.plugins.intelliLang.inject.InjectLanguageAction
import org.intellij.plugins.intelliLang.inject.InjectorUtils
import org.intellij.plugins.intelliLang.inject.UnInjectLanguageAction
import org.intellij.plugins.intelliLang.inject.config.BaseInjection
import org.intellij.plugins.intelliLang.inject.config.InjectionPlace
import org.intellij.plugins.intelliLang.inject.java.JavaLanguageInjectionSupport

class JavaLanguageInjectionSupportTest : AbstractLanguageInjectionTestCase() {

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return JAVA_21
  }

  fun testAnnotationInjection() {
    myFixture.configureByText("Foo.java", """
      class Foo {
          void bar() {
              baz("{\"a\": <caret> 1 }");
          }

          void baz(String json){}
      }
    """)

    StoringFixPresenter().apply {
      InjectLanguageAction.invokeImpl(project,
                                      myFixture.editor,
                                      myFixture.file,
                                      Injectable.fromLanguage(Language.findLanguageByID("JSON")),
                                      this
      )
    }.process()

    assertInjectedLangAtCaret("JSON")

    myFixture.checkResult("""
    |import org.intellij.lang.annotations.Language;
    |
    |class Foo {
    |          void bar() {
    |              baz("{\"a\":  1 }");
    |          }
    |
    |          void baz(@Language("JSON") String json){}
    |      }
    |    """.trimMargin())

    assertNotNull(myFixture.getAvailableIntention("Uninject language or reference"))
    UnInjectLanguageAction.invokeImpl(project, topLevelEditor, topLevelFile)

    assertInjectedLangAtCaret(null)
  }

  fun testAnnotationInjectionStringTemplate() {
    myFixture.configureByText("Foo.java", """
      public class Hello {
          void test(String name, String message) {
              String s = STR.""${'"'}
                      <caret>class \{name} {
                          void main() {
                              System.out.println("\{message}");
                          }
                      }""${'"'};
          }
      }""".trimIndent())

    StoringFixPresenter().apply {
      InjectLanguageAction.invokeImpl(project,
                                      myFixture.editor,
                                      myFixture.file,
                                      Injectable.fromLanguage(Language.findLanguageByID("JAVA")),
                                      this
      )
    }.process()

    assertInjectedLangAtCaret("JAVA")

    myFixture.checkResult("""
      import org.intellij.lang.annotations.Language;

      public class Hello {
          void test(String name, String message) {
              @Language("JAVA") String s = STR.""${'"'}
                      class \{name} {
                          void main() {
                              System.out.println("\{message}");
                          }
                      }""${'"'};
          }
      }
      """.trimIndent())

    assertNotNull(myFixture.getAvailableIntention("Uninject language or reference"))
    UnInjectLanguageAction.invokeImpl(project, topLevelEditor, topLevelFile)

    assertInjectedLangAtCaret(null)
  }

  fun testTemplateLanguageInjection() {
    myFixture.configureByText("Foo.java", """
      class Foo {
          void bar() {
              baz("Text with **Mark<caret>down**");
          }

          void baz(String str){}
      }
    """)
    assertNotNull(myFixture.getAvailableIntention("Inject language or reference"))
    InjectLanguageAction.invokeImpl(project,
                                    myFixture.editor,
                                    myFixture.file,
                                    Injectable.fromLanguage(Language.findLanguageByID("Markdown"))
    )

    assertInjectedLangAtCaret("XML")

    assertNotNull(myFixture.getAvailableIntention("Uninject language or reference"))
    UnInjectLanguageAction.invokeImpl(project, topLevelEditor, topLevelFile)
    assertInjectedLangAtCaret(null)
  }


  fun testConfigInjection() {

    fun currentPrintlnInjection(): BaseInjection? {
      val psiMethod = topLevelFile.findElementAt(topLevelCaretPosition)!!.parentOfType<PsiMethodCallExpression>()!!.resolveMethod()!!
      val injection = JavaLanguageInjectionSupport.makeParameterInjection(psiMethod, 0, "JSON")
      return InjectorUtils.getEditableInstance(project).findExistingInjection(injection)
    }


    myFixture.configureByText("Foo.java", """
      class Foo {
          void bar() {
              System.out.println("{\"a\": <caret> 1 }");
          }
      }
    """)

    TestCase.assertNull(currentPrintlnInjection())

    InjectLanguageAction.invokeImpl(project,
                                    myFixture.editor,
                                    myFixture.file,
                                    Injectable.fromLanguage(Language.findLanguageByID("JSON")))

    assertInjectedLangAtCaret("JSON")

    TestCase.assertNotNull(currentPrintlnInjection())

    myFixture.configureByText("Another.java", """
      class Another {
          void bar() {
              System.out.println("{\"a\": <caret> 1 }");
          }
      }
    """)

    assertInjectedLangAtCaret("JSON")

    UnInjectLanguageAction.invokeImpl(project, topLevelEditor, topLevelFile)

    TestCase.assertNull(currentPrintlnInjection())
    assertInjectedLangAtCaret(null)
  }


  fun testConfigUnInjectionAndUndo() {
    Configuration.getInstance().withInjections(listOf(jsonToPrintlnInjection())) {
      myFixture.configureByText("Foo.java", """
      class Foo {
          void bar() {
              System.out.println("{\"a\": <caret> 1 }");
          }
      }
    """)

      assertInjectedLangAtCaret("JSON")
      UnInjectLanguageAction.invokeImpl(project, topLevelEditor, topLevelFile)
      assertInjectedLangAtCaret(null)
      InjectLanguageAction.invokeImpl(project, topLevelEditor, topLevelFile, Injectable.fromLanguage(Language.findLanguageByID("JSON")))
      assertInjectedLangAtCaret("JSON")
      undo(topLevelEditor)
      assertInjectedLangAtCaret(null)
    }

  }
  
  fun testPartialJson() {
    Configuration.getInstance().withInjections(listOf(jsonToPrintlnInjection())) {
      myFixture.configureByText("Foo.java", """
      class Foo {
          void bar() {
              System.out.println(
                        "{'id': '0'," +
                                "'uri': 'http://localhost/'}"
                                .replaceAll("'", "\""));
              System.out.println("{ bad_json: 123 }".replaceAll("'", "\""));
          }
      }
    """)
      injectionTestFixture.assertInjectedContent("'", "{'id': '0',missingValue")
    }
  }
  
  
  fun testRegexJson() {
    Configuration.getInstance().withInjections(listOf(jsonToPrintlnInjection().apply { 
      setValuePattern("""\((.*?)\)""")
      isSingleFile = true
    })) {
      myFixture.configureByText("Foo.java", """
      class Foo {
          void bar() {
              System.out.println("({'id':1,) bbb ('boo': 3})");
          }
      }
    """)
      injectionTestFixture.assertInjectedContent("{'id':1,'boo': 3}")
    }
  }
  
  fun testTemplatePlainString() {
    myFixture.configureByText("Test.java", """
      import org.intellij.lang.annotations.Language;

      class Hello {
        @Language("JAVA")
        private static final StringTemplate.Processor<String, RuntimeException> JAVA = STR;

        void test() {
          String plainString = JAVA."class Test {\n  void main() {\n    System.out.println(\"Hello world\");\n  }\n}";
        }
      }
    """.trimIndent())
    myFixture.checkHighlighting()
    injectionTestFixture.assertInjectedContent("""class Test {\n  void main() {\n    System.out.println(\"Hello world\");\n  }\n}""")
  }
  
  fun testTemplatePlainBlock() {
    myFixture.configureByText("Test.java", """
      import org.intellij.lang.annotations.Language;

      class Hello {
        @Language("JAVA")
        private static final StringTemplate.Processor<String, RuntimeException> JAVA = STR;

        void test() {
          String plainTextBlock = JAVA.""${'"'}
              class Test {
                void main() {
                  System.out.println("Hello world");
                }
              }""${'"'};
        }
      }
    """.trimIndent())
    myFixture.checkHighlighting()
    injectionTestFixture.assertInjectedContent("""
      class Test {
        void main() {
          System.out.println("Hello world");
        }
      }""".trimIndent())
  }
  
  fun testTemplateWithVars() {
    myFixture.configureByText("Test.java", """
      import org.intellij.lang.annotations.Language;

      class Hello {
        void test(String name, String message) {
          @Language("JAVA")
          String s = STR."class \{name} { void main() { System.out.println(\"\{message}\") }  }";
        }
      }
    """.trimIndent())
    myFixture.checkHighlighting()
    injectionTestFixture.assertInjectedContent("""class missingValue { void main() { System.out.println(\"missingValue\") }  }""")
  }
  
  fun testTemplateBlockWithVars() {
    myFixture.configureByText("Test.java", """
      import org.intellij.lang.annotations.*;

      class Hello {
        void test(@Subst("ClassName") String name, String message) {
          String s = javaProcessor().""${'"'}
                class \{name} {
                    void main() {
                      System.out.println("\{message}");
                    }
                }""${'"'};
        }
            
        @Language("JAVA")
        private static StringTemplate.Processor<String, RuntimeException> javaProcessor() {
          return STR;
        }
    }""".trimIndent())
    myFixture.checkHighlighting()
    injectionTestFixture.assertInjectedContent("""
      class ClassName {
          void main() {
            System.out.println("missingValue");
          }
      }""".trimIndent())
  }
  
  fun testTemplateBlockWithVarsAndTrailingSlash() {
    myFixture.configureByText("Test.java", """
      import org.intellij.lang.annotations.*;

      class Hello {
        void test(@Subst("ClassName") String name, String message) {
          String s = javaProcessor().""${'"'}
                class \{name} {
                    void main() {
                      System.out.\
                println("\{message}");
                    }
                }""${'"'};
        }
        
        interface MyProcessor extends StringTemplate.Processor<String, RuntimeException> {} 
            
        @Language("JAVA")
        private static native MyProcessor javaProcessor();
    }""".trimIndent())
    myFixture.checkHighlighting()
    injectionTestFixture.assertInjectedContent("""
      class ClassName {
          void main() {
            System.out.println("missingValue");
          }
      }""".trimIndent())
  }

  fun testJavaInjectionWithLeadingNewLines() {
    myFixture.configureByText("Test.java", """
      import org.intellij.lang.annotations.*;

      class Hello {
        void test() {
          createClass(""${'"'}




                      class Foo {
                        static void foo(int a) {}
                        static void foo(int a, int b) {}
                      }""${'"'});
       }
        
          private static void createClass(@Language("JAVA") String text){};
    }""".trimIndent())
    myFixture.checkHighlighting()
    injectionTestFixture.assertInjectedContent("""
      
      
      
      
      class Foo {
        static void foo(int a) {}
        static void foo(int a, int b) {}
      }""".trimIndent())
  }
  
  fun testRegexJsonNotSingle() {
    Configuration.getInstance().withInjections(listOf(jsonToPrintlnInjection().apply { 
      setValuePattern("""\((.*?)\)""")
      isSingleFile = false
    })) {
      myFixture.configureByText("Foo.java", """
      class Foo {
          void bar() {
              System.out.println("({'id':1}) bbb ({'boo': 3})");
          }
      }
    """)
      injectionTestFixture.assertInjectedContent("{'id':1}", "{'boo': 3}")
    }
  }

  private fun jsonToPrintlnInjection(): BaseInjection = BaseInjection("java").apply {
     injectedLanguageId = "JSON"
     setInjectionPlaces(
       InjectionPlace(compiler.createElementPattern(
         """psiParameter().ofMethod(0, psiMethod().withName("println").withParameters("java.lang.String").definedInClass("java.io.PrintStream"))""",
         "println JSOM"), true
       ),
       InjectionPlace(compiler.createElementPattern(
         """psiParameter().ofMethod(0, psiMethod().withName("print").withParameters("java.lang.String").definedInClass("java.io.PrintStream"))""",
         "print JSON"), true
       )
     )
   }

  private fun undo(editor: Editor) {
    UIUtil.invokeAndWaitIfNeeded(Runnable {
      val oldTestDialog = TestDialogManager.setTestDialog(TestDialog.OK)
      try {
        val undoManager = UndoManager.getInstance(project)
        val textEditor = TextEditorProvider.getInstance().getTextEditor(editor)
        undoManager.undo(textEditor)
      }
      finally {
        TestDialogManager.setTestDialog(oldTestDialog)
      }
    })
  }


}

