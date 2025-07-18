// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.injected

import com.intellij.codeInsight.intention.impl.QuickEditAction
import com.intellij.lang.Language
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.lang.injection.MultiHostRegistrarPlaceholderHelper
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.injection.Injectable
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.InjectionTestFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.SmartList
import com.intellij.util.ui.UIUtil
import junit.framework.TestCase
import org.intellij.plugins.intelliLang.StoringFixPresenter
import org.intellij.plugins.intelliLang.inject.InjectLanguageAction
import org.intellij.plugins.intelliLang.inject.UnInjectLanguageAction
import org.jetbrains.uast.expressions.UStringConcatenationsFacade

class JavaInjectedFileChangesHandlerTest : JavaCodeInsightFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    ModuleRootModificationUtil.updateModel(module, DefaultLightProjectDescriptor::addJetBrainsAnnotations)
  }

  fun `test edit multiline in fragment-editor`() {
    with(myFixture) {

      configureByText("classA.java", """
          class A {
            void foo() {
              String a = "{\"bca\":<caret> \n" +
                      "1}";
            }
          }
      """.trimIndent())

      val fragmentFile = injectAndOpenInFragmentEditor("JSON")
      TestCase.assertEquals("{\"bca\": \n1}", fragmentFile.text)

      fragmentFile.edit { insertString(text.indexOf(":"), "\n") }
      checkResult("""
          import org.intellij.lang.annotations.Language;

          class A {
            void foo() {
              @Language("JSON") String a = "{\"bca\"\n" +
                      ": \n" +
                      "1}";
            }
          }
      """.trimIndent())
    }

  }

  fun `test temp injection survive on host death and no edit in uninjected`() {
    with(myFixture) {

      configureByText("classA.java", """
          class A {
            void foo() {
              String a = "{\"bca\":<caret> \n1}";
            }
          }
      """.trimIndent())

      InjectLanguageAction.invokeImpl(project,
                                      myFixture.editor,
                                      myFixture.file,
                                      Injectable.fromLanguage(Language.findLanguageByID("JSON")))

      val quickEditHandler = QuickEditAction().invokeImpl(project, editor, file)
      val fragmentFile = quickEditHandler.newFile

      TestCase.assertEquals("{\"bca\": \n1}", fragmentFile.text)
      injectionTestFixture.assertInjectedLangAtCaret("JSON")

      fragmentFile.edit { insertString(text.indexOf(":"), "\n") }
      checkResult("""
          class A {
            void foo() {
              String a = "{\"bca\"\n" +
                      ": \n" +
                      "1}";
            }
          }
      """.trimIndent())
      injectionTestFixture.assertInjectedLangAtCaret("JSON")
      UnInjectLanguageAction.invokeImpl(project, injectionTestFixture.topLevelEditor, injectionTestFixture.topLevelFile)
      injectionTestFixture.assertInjectedLangAtCaret(null)

      TestCase.assertFalse(quickEditHandler.isValid)
      fragmentFile.edit { insertString(text.indexOf(":"), "       ") }
      checkResult("""
          class A {
            void foo() {
              String a = "{\"bca\"\n" +
                      ": \n" +
                      "1}";
            }
          }
      """.trimIndent())
    }
  }

  fun `test delete in multiple hosts`() {
    with(myFixture) {

      configureByText("classA.java", """
          class A {
            void foo() {
              String a = "<html>line\n" +
                      "anotherline<caret>\n" +
                      "finalLine</html>";
            }
          }
      """.trimIndent())

      val fragmentFile = injectAndOpenInFragmentEditor("HTML")
      TestCase.assertEquals("<html>line\nanotherline\nfinalLine</html>", fragmentFile.text)

      fragmentFile.edit {
        findAndDelete("ne\nanot")
      }
      checkResult("""
          import org.intellij.lang.annotations.Language;

          class A {
            void foo() {
              @Language("HTML") String a = "<html>li" +
                      "herline\n" +
                      "finalLine</html>";
            }
          }
      """.trimIndent())
    }

  }

  fun `test edit multipart in 4 steps`() {
    with(myFixture) {

      configureByText("classA.java", """
          class A {
            void foo() {
              String a = "{\"a\"<caret>: 1}";
            }
          }
      """.trimIndent())

      val fragmentFile = injectAndOpenInFragmentEditor("JSON")
      TestCase.assertEquals("{\"a\": 1}", fragmentFile.text)

      fragmentFile.edit { insertString(text.indexOf("a"), "bc") }
      checkResult("""
          import org.intellij.lang.annotations.Language;

          class A {
            void foo() {
              @Language("JSON") String a = "{\"bca\": 1}";
            }
          }
      """.trimIndent())
      injectionTestFixture.assertInjectedLangAtCaret("JSON")
      fragmentFile.edit { insertString(text.indexOf("1"), "\n") }
      checkResult("""
          import org.intellij.lang.annotations.Language;

          class A {
            void foo() {
              @Language("JSON") String a = "{\"bca\": \n" +
                      "1}";
            }
          }
      """.trimIndent(), true)
      injectionTestFixture.assertInjectedLangAtCaret("JSON")
      fragmentFile.edit { insertString(text.indexOf(":"), "\n") }
      checkResult("""
          import org.intellij.lang.annotations.Language;

          class A {
            void foo() {
              @Language("JSON") String a = "{\"bca\"\n" +
                      ": \n" +
                      "1}";
            }
          }
      """.trimIndent(), true)
      injectionTestFixture.assertInjectedLangAtCaret("JSON")
      TestCase.assertEquals("{\"bca\"\n: \n1}", fragmentFile.text)
      fragmentFile.edit { deleteString(0, textLength) }
      checkResult("""
          import org.intellij.lang.annotations.Language;

          class A {
            void foo() {
              @Language("JSON") String a = "";
            }
          }
      """.trimIndent(), true)
      injectionTestFixture.assertInjectedLangAtCaret("JSON")
    }

  }

  fun `test delete-insert-delete`() {
    with(myFixture) {

      configureByText("classA.java", """
          import org.intellij.lang.annotations.Language;

          class A {
            void foo() {
              @Language("JSON") String a = "{\"bca\"<caret>\n" +
                      ": \n" +
                      "1}";
            }
          }
      """.trimIndent())

      val quickEditHandler = QuickEditAction().invokeImpl(project, injectionTestFixture.topLevelEditor, injectionTestFixture.topLevelFile)
      val fragmentFile = quickEditHandler.newFile
      TestCase.assertEquals("{\"bca\"\n: \n1}", fragmentFile.text)
      fragmentFile.edit { deleteString(0, textLength) }
      checkResult("""
          import org.intellij.lang.annotations.Language;

          class A {
            void foo() {
              @Language("JSON") String a = "";
            }
          }
      """.trimIndent(), true)
      TestCase.assertEquals("", fragmentFile.text)
      fragmentFile.edit { insertString(0, "{\"bca\"\n: \n1}") }
      checkResult("""
          import org.intellij.lang.annotations.Language;

          class A {
            void foo() {
              @Language("JSON") String a = "{\"bca\"\n" +
                      ": \n" +
                      "1}";
            }
          }
      """.trimIndent(), true)
      TestCase.assertEquals("{\"bca\"\n: \n1}", fragmentFile.text)
      fragmentFile.edit { deleteString(0, textLength) }
      checkResult("""
          import org.intellij.lang.annotations.Language;

          class A {
            void foo() {
              @Language("JSON") String a = "";
            }
          }
      """.trimIndent(), true)
      TestCase.assertEquals("", fragmentFile.text)
      fragmentFile.edit { insertString(0, "{\"bca\"\n: \n1}") }
      checkResult("""
          import org.intellij.lang.annotations.Language;

          class A {
            void foo() {
              @Language("JSON") String a = "{\"bca\"\n" +
                      ": \n" +
                      "1}";
            }
          }
      """.trimIndent(), true)
      TestCase.assertEquals("{\"bca\"\n: \n1}", fragmentFile.text)
      fragmentFile.edit { deleteString(0, textLength) }
      checkResult("""
          import org.intellij.lang.annotations.Language;

          class A {
            void foo() {
              @Language("JSON") String a = "";
            }
          }
      """.trimIndent(), true)
    }

  }

  fun `test complex insert-commit-broken-reformat`() {
    with(myFixture) {

      configureByText("classA.java", """
          import org.intellij.lang.annotations.Language;

          class A {
            void foo() {
              @Language("JSON")
              String a = "{\n" +
                  "  \"field1\": 1,\n" +
                  "  \"innerMap1" +
                  "\": {\n" +
                  "    " +
                  "\"field2\": 1,\n" +
                  "  " +
                  "  \"field3\": 3<caret>\n" +
                  "  " +
                  "}\n" +
                  "}";
            }
          }
      """.trimIndent())

      val quickEditHandler = QuickEditAction().invokeImpl(project, injectionTestFixture.topLevelEditor, injectionTestFixture.topLevelFile)
      val fragmentFile = quickEditHandler.newFile
      TestCase.assertEquals("{\n" +
                            "  \"field1\": 1,\n" +
                            "  \"innerMap1\": {\n" +
                            "    \"field2\": 1,\n" +
                            "    \"field3\": 3\n" +
                            "  }\n" +
                            "}",
                            fragmentFile.text)
      fragmentFile.edit("insert json") {
        val pos = text.indexAfter("\"field3\": 3")
        replaceString(pos - 1, pos, "\"brokenInnerMap\": {\n" +
                                    "    \"broken1\": 1,\n" +
                                    "    \"broken2\": 2,\n" +
                                    "    \"broken3\": 3\n" +
                                    "  }")
      }

      PsiDocumentManager.getInstance(project).commitAllDocuments()

      fragmentFile.edit("reformat") {
        CodeStyleManager.getInstance(psiManager).reformatRange(
          fragmentFile, 0, fragmentFile.textLength, false)
      }

      checkResult("""
          import org.intellij.lang.annotations.Language;

          class A {
            void foo() {
              @Language("JSON")
              String a = "{\n" +
                  "  \"field1\": 1,\n" +
                  "  \"innerMap1" +
                  "\": {\n" +
                  "    " +
                  "\"field2\": 1,\n" +
                  "  " +
                      "  \"field3\": \"brokenInnerMap\"\n" +
                      "    : {\n" +
                      "  \"broken1\": 1,\n" +
                      "  \"broken2\": 2,\n" +
                      "  \"broken3\": 3\n" +
                      "}\n" +
                      "}\n" +
                      "}";
            }
          }
      """.trimIndent(), true)
      TestCase.assertEquals("""
          {
            "field1": 1,
            "innerMap1": {
              "field2": 1,
              "field3": "brokenInnerMap"
              : {
            "broken1": 1,
            "broken2": 2,
            "broken3": 3
          }
          }
          }
      """.trimIndent(), fragmentFile.text)

    }

  }

  fun `test indented update`() {
    myFixture.configureByText("Test.java", """
      import org.intellij.lang.annotations.*;

      class Hello {
        void test() {
          createClass(""${'"'}
                      class Foo {
                        static void foo(int a) {}<caret>
                        static void foo(int a, int b) {}
                      }""${'"'});
       }
        
          private static void createClass(@Language("JAVA") String text){};
    }""".trimIndent())
    val originalEditor = injectionTestFixture.topLevelEditor

    val quickEditHandler = QuickEditAction().invokeImpl(project, injectionTestFixture.topLevelEditor, injectionTestFixture.topLevelFile)
    val fragmentFile = quickEditHandler.newFile
    TestCase.assertEquals("""
      class Foo {
        static void foo(int a) {}
        static void foo(int a, int b) {}
      }
      """.trimIndent(), fragmentFile.text)

    myFixture.openFileInEditor(fragmentFile.virtualFile)

    myFixture.editor.caretModel.moveToOffset(fragmentFile.text.indexAfter("foo(int a) {}"))
    myFixture.type("\n\n\n")

    TestCase.assertEquals("""
      class Foo {
        static void foo(int a) {}



        static void foo(int a, int b) {}
      }
      """.trimIndent(), myFixture.editor.document.text.replace(Regex("[ \t]+\n"), "\n"))

    TestCase.assertEquals("""
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
        }""".trimIndent(), originalEditor.document.text)
  }

  fun `test suffix-prefix-edit-reformat`() {
    with(myFixture) {

      configureByText("classA.java", """
          import org.intellij.lang.annotations.Language;

          class A {
            void foo() {
                @Language(value = "JSON", prefix = "{\n", suffix = "\n}\n}")
                String a =
                    "  \"field1\": 1,\n<caret>" +
                        "  \"container\": {\n" +
                        "    \"innerField\": 2";
            }
          }
      """.trimIndent())

      val quickEditHandler = QuickEditAction().invokeImpl(project, injectionTestFixture.topLevelEditor, injectionTestFixture.topLevelFile)
      val fragmentFile = quickEditHandler.newFile
      TestCase.assertEquals("""
        {
          "field1": 1,
          "container": {
            "innerField": 2
        }
        }
      """.trimIndent(), fragmentFile.text)

      openFileInEditor(fragmentFile.virtualFile)

      editor.caretModel.moveToOffset(fragmentFile.text.indexAfter("\"innerField\": 2"))
      type("\n\"anotherInnerField\": 3")
      PsiDocumentManager.getInstance(project).commitAllDocuments()

      TestCase.assertEquals("""
        {
          "field1": 1,
          "container": {
            "innerField": 2,
            "anotherInnerField": 3
        }
        }
      """.trimIndent(), fragmentFile.text)

      fragmentFile.edit("reformat") {
        CodeStyleManager.getInstance(psiManager).reformatRange(
          fragmentFile, 0, fragmentFile.textLength, false)
      }

      checkResult("classA.java", """
          import org.intellij.lang.annotations.Language;

          class A {
            void foo() {
                @Language(value = "JSON", prefix = "{\n", suffix = "\n}\n}")
                String a =
                    "  \"field1\": 1,\n" +
                        "  \"container\": {\n" +
                            "    \"innerField\": 2,\n" +
                            "    \"anotherInnerField\": 3";
            }
          }
      """.trimIndent(), true)
      TestCase.assertEquals("""
        {
          "field1": 1,
          "container": {
            "innerField": 2,
            "anotherInnerField": 3
          }
        }
      """.trimIndent(), fragmentFile.text)

    }

  }

  fun `test text block trim indent`() {
    with(myFixture) {
      configureByText("classA.java", """
        import org.intellij.lang.annotations.Language;
        
        class A {
          @Language("HTML")
          String s = ""${'"'}
          <html>
            <body>
              <h1>title</h1>
              <p>
                this is a test.<caret>
              </p>
            </body>
          </html>""${'"'};
        }
      """.trimIndent())

      val quickEditHandler = QuickEditAction().invokeImpl(project, injectionTestFixture.topLevelEditor, injectionTestFixture.topLevelFile)
      val fragmentFile = quickEditHandler.newFile
      TestCase.assertEquals(
        """|<html>
           |  <body>
           |    <h1>title</h1>
           |    <p>
           |      this is a test.
           |    </p>
           |  </body>
           |</html>""".trimMargin(), fragmentFile.text)
    }
  }

  fun `test text block tab in fragment editor`() {
    with(myFixture) {
      configureByText("classA.java", """
        import org.intellij.lang.annotations.Language;
        
        class A {
          @Language("HTML")
          String s = ""${'"'}
                  <html>
                  <caret><p></p>
                  </html>
                  ""${'"'};
        }
      """.trimIndent())

      val fe = injectionTestFixture.openInFragmentEditor()
      fe.performEditorAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET)
      fe.performEditorAction(IdeActions.ACTION_EDITOR_INDENT_SELECTION)
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      TestCase.assertEquals("""
           |<html>
           |    <p></p>
           |</html>
           |""".trimMargin(), fe.file.text)
      myFixture.checkResult("""
        |import org.intellij.lang.annotations.Language;
        |
        |class A {
        |  @Language("HTML")
        |  String s = ""${'"'}
        |          <html>
        |              <p></p>
        |          </html>
        |          ""${'"'};
        |}
      """.trimMargin())
    }
  }

  fun `test delete-commit-delete`() {
    with(myFixture) {

      configureByText("classA.java", """
          import org.intellij.lang.annotations.Language;

          class A {
            void foo() {
              @Language("JSON")
              String a = "{\n" +
                  "  \"field1\": 1,\n" +
                  "  \"container\"<caret>: {\n" +
                  "    \"innerField\": 2\n" +
                  "  }\n" +
                  "}";
            }
          }
      """.trimIndent())

      val quickEditHandler = QuickEditAction().invokeImpl(project, injectionTestFixture.topLevelEditor, injectionTestFixture.topLevelFile)
      val fragmentFile = quickEditHandler.newFile
      TestCase.assertEquals("""
          {
            "field1": 1,
            "container": {
              "innerField": 2
            }
          }
      """.trimIndent(), fragmentFile.text)
      fragmentFile.edit("delete json") {
        findAndDelete("  \"container\": {\n    \"innerField\": 2\n  }")
      }

      PsiDocumentManager.getInstance(project).commitAllDocuments()

      fragmentFile.edit("delete again") {
        val pos = text.indexAfter("1,\n")
        deleteString(pos - 1, pos)
      }

      checkResult("""
          import org.intellij.lang.annotations.Language;

          class A {
            void foo() {
              @Language("JSON")
              String a = "{\n" +
                      "  \"field1\": 1,\n" +
                      "}";
            }
          }
      """.trimIndent(), true)
      TestCase.assertEquals("""
          {
            "field1": 1,
          }
      """.trimIndent(), fragmentFile.text)

    }

  }

  fun `test delete empty line`() {
    with(myFixture) {

      configureByText("classA.java", """
          class A {
            void foo() {
              String a = "<html>line\n<caret>" +
                      "\n" +
                      "finalLine</html>";
            }
          }
      """.trimIndent())

      val fragmentFile = injectAndOpenInFragmentEditor("HTML")
      TestCase.assertEquals("<html>line\n\nfinalLine</html>", fragmentFile.text)

      fragmentFile.edit {
        findAndDelete("\n")
      }
      checkResult("""
          import org.intellij.lang.annotations.Language;

          class A {
            void foo() {
              @Language("HTML") String a = "<html>line\n" +
                      "finalLine</html>";
            }
          }
      """.trimIndent())
      fragmentFile.edit {
        findAndDelete("\n")
      }
      checkResult("""
          import org.intellij.lang.annotations.Language;

          class A {
            void foo() {
              @Language("HTML") String a = "<html>line" +
                      "finalLine</html>";
            }
          }
      """.trimIndent())
    }

  }

  fun `test type new lines in fe then delete`() {
    with(myFixture) {

      val hostFile = configureByText("classA.java", """
          class A {
            void foo() {
              String a = "<html></html><caret>";
            }
          }
      """.trimIndent())

      val fragmentFile = injectAndOpenInFragmentEditor("HTML")
      TestCase.assertEquals("<html></html>", fragmentFile.text)

      openFileInEditor(fragmentFile.virtualFile)
      assertHostIsReachable(hostFile, file)

      moveCaret(fragmentFile.text.length)
      type("\n")
      type("\n")

      checkResult("classA.java", """
        import org.intellij.lang.annotations.Language;
        
        class A {
          void foo() {
            @Language("HTML") String a = "<html></html>\n" +
                    "\n";
          }
        }
      """.trimIndent(), true)
      assertHostIsReachable(hostFile, file)

      type("\b")
      type("\b")

      checkResult("classA.java", """
        import org.intellij.lang.annotations.Language;
        
        class A {
          void foo() {
            @Language("HTML") String a = "<html></html>";
          }
        }
      """.trimIndent(), true)
      assertHostIsReachable(hostFile, file)
    }

  }

  private fun assertHostIsReachable(hostFile: PsiFile, injectedFile: PsiFile) {
    TestCase.assertEquals("host file should be reachable from the context",
                          hostFile.virtualFile,
                          injectedFile.context?.containingFile?.virtualFile)
  }

  fun `test edit with guarded blocks`() {
    with(myFixture) {

      myFixture.configureByText("classA.java", """
          class A {
            void foo(String bodyText) {
              String headText = "someText1";
              String concatenation = "<html><head>" + headText + "</head><caret><body>" + bodyText + "</body></html>";
            }
          }
      """.trimIndent())

      val fragmentFile = injectAndOpenInFragmentEditor("HTML")
      TestCase.assertEquals("<html><head>someText1</head><body>missingValue</body></html>", fragmentFile.text)

      fragmentFile.edit { insertString(text.indexOf("<body>"), "someInner") }
      checkResult("""
          import org.intellij.lang.annotations.Language;

          class A {
            void foo(String bodyText) {
              String headText = "someText1";
              @Language("HTML") String concatenation = "<html><head>" + headText + "</head>someInner<body>" + bodyText + "</body></html>";
            }
          }
      """.trimIndent(), true)

      fragmentFile.edit { insertString(text.indexOf("<body>") + "<body>".length, "\n") }
      checkResult("""
          import org.intellij.lang.annotations.Language;

          class A {
            void foo(String bodyText) {
              String headText = "someText1";
              @Language("HTML") String concatenation = "<html><head>" + headText + "</head>someInner<body>\n" + bodyText + "</body></html>";
            }
          }
      """.trimIndent())

    }

  }

  fun `test edit guarded blocks ending`() {
    with(myFixture) {

      myFixture.configureByText("classA.java", """
          class A {
            void foo(String bodyText) {
              String bodyText = "someTextInBody";
              String concatenation = "<html><head></head><caret><body>" + end + "</body></html>";
            }
          }
      """.trimIndent())

      val fragmentFile = injectAndOpenInFragmentEditor("HTML")
      TestCase.assertEquals("<html><head></head><body>missingValue</body></html>", fragmentFile.text)

      fragmentFile.edit { findAndDelete("</body></html>") }
      checkResult("""
        import org.intellij.lang.annotations.Language;

        class A {
          void foo(String bodyText) {
            String bodyText = "someTextInBody";
            @Language("HTML") String concatenation = "<html><head></head><body>" + end;
          }
        }
      """.trimIndent(), true)

      fragmentFile.edit { insertString(text.indexOf("<body>") + "<body>".length, "\n") }
      checkResult("""
          import org.intellij.lang.annotations.Language;

          class A {
            void foo(String bodyText) {
              String bodyText = "someTextInBody";
              @Language("HTML") String concatenation = "<html><head></head><body>\n" + end;
            }
          }
      """.trimIndent())

    }

  }

  fun `test sequential add in fragment editor undo-repeat`() {
    with(myFixture) {

      val fileName = "classA.java"

      val initialText = """
          import org.intellij.lang.annotations.Language;

          class A {
            void foo() {
               @Language("JSON")  String a = "{\n" +
                    "  \"begin\": true,\n" +
                    "  \"fieldstart\": -1,\n" +
                    "  \"end\": false\n" +
                    "}";
            }
          }
      """.trimIndent()

      val fiveFieldsFilled = """
          import org.intellij.lang.annotations.Language;

          class A {
            void foo() {
               @Language("JSON")  String a = "{\n" +
                    "  \"begin\": true,\n" +
                       "  \"fieldstart\": -1,\n" +
                       "  \"field0\": 0,\n" +
                       "  \"field1\": 1,\n" +
                       "  \"field2\": 2,\n" +
                       "  \"field3\": 3,\n" +
                       "  \"field4\": 4,\n" +
                       "  \"end\": false\n" +
                    "}";
            }
          }
      """.trimIndent()

      configureByText(fileName, initialText)

      myFixture.editor.caretModel.moveToOffset(file.text.indexAfter("-1"))

      val quickEditHandler = QuickEditAction().invokeImpl(project, injectionTestFixture.topLevelEditor, injectionTestFixture.topLevelFile)
      val fragmentFile = quickEditHandler.newFile
      injectionTestFixture.assertInjectedLangAtCaret("JSON")

      openFileInEditor(fragmentFile.virtualFile)

      fun fillFields(num: Int) {
        var shift = fragmentFile.text.indexAfter("-1,\n")
        repeat(num) {
          quickEditHandler.newFile.edit("add field$it") {
            val s = "  \"field$it\": $it,\n"
            insertString(shift, s)
            shift += s.length
          }
        }
      }

      fillFields(5)
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      checkResult(fileName, fiveFieldsFilled, true)

      repeat(5) {
        undo(editor)
      }
      checkResult(fileName, initialText, true)

      fillFields(5)
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      checkResult(fileName, fiveFieldsFilled, true)

    }

  }

  fun `test edit inner within multiple injections`() {
    with(myFixture) {

      InjectedLanguageManager.getInstance(project).registerMultiHostInjector(JsonMultiInjector(), testRootDisposable)

      configureByText("classA.java", """
          class A {
            void foo() {
               String injectjson = "{\n" +
                        "  \"html\": \"<html><caret></html>\"\n" +
                        "}";
            }
          }
      """.trimIndent())

      injectionTestFixture.getAllInjections().map { it.second }.distinct().let { allInjections ->
        UsefulTestCase.assertContainsElements(
          allInjections.map { it.text },
          "{\\n  \\\"html\\\": \\\"HTML\\\"\\n}",
          "<html></html>")
      }


      val quickEditHandler = QuickEditAction().invokeImpl(project, injectionTestFixture.topLevelEditor, injectionTestFixture.topLevelFile)
      val fragmentFile = quickEditHandler.newFile

      TestCase.assertEquals("<html></html>", fragmentFile.text)

      fragmentFile.edit { insertString(text.indexOf("</html>"), " ") }
      checkResult("""
          class A {
            void foo() {
               String injectjson = "{\n" +
                       "  \"html\": \"<html> </html>\"\n" +
                        "}";
            }
          }
      """.trimIndent())
      fragmentFile.edit {
        val htmlBodyEnd = text.indexOf("</html>")
        deleteString(htmlBodyEnd - 1, htmlBodyEnd)
      }
      checkResult("""
          class A {
            void foo() {
               String injectjson = "{\n" +
                       "  \"html\": \"<html></html>\"\n" +
                        "}";
            }
          }
      """.trimIndent())
    }

  }

  private fun injectAndOpenInFragmentEditor(language: String): PsiFile {
    with(myFixture) {
      StoringFixPresenter().apply {
        InjectLanguageAction.invokeImpl(project,
                                        myFixture.editor,
                                        myFixture.file,
                                        Injectable.fromLanguage(Language.findLanguageByID(language)),
                                        this
        )
      }.process()
      val quickEditHandler = QuickEditAction().invokeImpl(project, editor, file)
      return quickEditHandler.newFile
    }
  }

  private fun undo(editor: Editor) = runWithUndoManager(editor, UndoManager::undo)

  private fun redo(editor: Editor) = runWithUndoManager(editor, UndoManager::redo)

  private fun runWithUndoManager(editor: Editor, action: (UndoManager, TextEditor) -> Unit) {
    UIUtil.invokeAndWaitIfNeeded(Runnable {
      val oldTestDialog = TestDialogManager.setTestDialog(TestDialog.OK)
      try {
        val undoManager = UndoManager.getInstance(project)
        val textEditor = TextEditorProvider.getInstance().getTextEditor(editor)
        action(undoManager, textEditor)
      }
      finally {
        TestDialogManager.setTestDialog(oldTestDialog)
      }
    })
  }

  private fun PsiFile.edit(actionName: String = "change doc", groupId: Any = actionName, docFunction: Document.() -> Unit) {
    CommandProcessor.getInstance().executeCommand(project, {
      ApplicationManager.getApplication().runWriteAction {
        val manager = PsiDocumentManager.getInstance(project)
        val document = manager.getDocument(this) ?: throw AssertionError("document is null")
        document.docFunction()
      }
    }, actionName, groupId)
  }

  private fun commit() = PsiDocumentManager.getInstance(project).commitDocument(myFixture.editor.document)

  private fun moveCaret(shift: Int, columnShift: Int = 0) =
    myFixture.editor.caretModel.moveCaretRelatively(shift, columnShift, false, false, false)

  private fun Document.findAndDelete(substring: String) {
    val start = this.text.indexOf(substring)
    deleteString(start, start + substring.length)
  }

  private val injectionTestFixture: InjectionTestFixture get() = InjectionTestFixture(myFixture)

}

private fun String.indexAfter(string: String): Int {
  val r = indexOf(string)
  return if (r == -1) -1 else r + string.length
}

private class JsonMultiInjector : MultiHostInjector {
  private val VAR_NAME = "injectjson"

  override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
    val concatenationsFacade = UStringConcatenationsFacade.create(context)?.takeIf { it.uastOperands.any() } ?: return
    context.parentOfType<PsiVariable>()?.takeIf { it.name == VAR_NAME } ?: return

    val cssReg = """"html"\s*:\s*"(.*)"""".toRegex()
    val mhRegistrar = MultiHostRegistrarPlaceholderHelper(registrar)

    mhRegistrar.startInjecting(Language.findLanguageByID("JSON")!!)
    val cssInjections = SmartList<Pair<PsiLanguageInjectionHost, TextRange>>()
    for (host in concatenationsFacade.psiLanguageInjectionHosts) {
      val manipulator = ElementManipulators.getManipulator(host)
      val fullRange = manipulator.getRangeInElement(host)
      val escaper = host.createLiteralTextEscaper()

      val decoded: CharSequence = StringBuilder().apply { escaper.decode(fullRange, this) }
      val cssRanges = cssReg.find(decoded)?.groups?.get(1)?.range
        ?.let { TextRange.create(it.first, it.last + 1) }
        ?.let { cssRangeInDecoded ->
          listOf(TextRange.create(
            escaper.getOffsetInHost(cssRangeInDecoded.startOffset, fullRange),
            escaper.getOffsetInHost(cssRangeInDecoded.endOffset, fullRange)
          ))
        }.orEmpty()

      mhRegistrar.addHostPlaces(host, cssRanges.map { it to "HTML" })
      cssInjections.addAll(cssRanges.map { host to it })
    }

    mhRegistrar.doneInjecting()

    for ((host, cssRange) in cssInjections) {
      registrar.startInjecting(Language.findLanguageByID("HTML")!!)
      registrar.addPlace(null, null, host, cssRange)
      registrar.doneInjecting()
    }

  }

  override fun elementsToInjectIn() = listOf(PsiElement::class.java)

}
