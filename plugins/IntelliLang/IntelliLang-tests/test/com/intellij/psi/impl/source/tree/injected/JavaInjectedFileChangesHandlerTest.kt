// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.injected

import com.intellij.codeInsight.intention.impl.QuickEditAction
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.injection.Injectable
import com.intellij.testFramework.fixtures.InjectionTestFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.ui.UIUtil
import junit.framework.TestCase
import org.intellij.plugins.intelliLang.StoringFixPresenter
import org.intellij.plugins.intelliLang.inject.InjectLanguageAction

class JavaInjectedFileChangesHandlerTest : JavaCodeInsightFixtureTestCase() {

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

      val injectedFile = injectAndOpenInFragmentEditor("JSON")
      TestCase.assertEquals("{\"bca\": \n1}", injectedFile.text)

      injectedFile.edit { insertString(text.indexOf(":"), "\n") }
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

      val injectedFile = injectAndOpenInFragmentEditor("HTML")
      TestCase.assertEquals("<html>line\nanotherline\nfinalLine</html>", injectedFile.text)

      injectedFile.edit {
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

      val injectedFile = injectAndOpenInFragmentEditor("JSON")
      TestCase.assertEquals("{\"a\": 1}", injectedFile.text)

      injectedFile.edit { insertString(text.indexOf("a"), "bc") }
      checkResult("""
          import org.intellij.lang.annotations.Language;

          class A {
            void foo() {
              @Language("JSON") String a = "{\"bca\": 1}";
            }
          }
      """.trimIndent())
      injectionTestFixture.assertInjectedLangAtCaret("JSON")
      injectedFile.edit { insertString(text.indexOf("1"), "\n") }
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
      injectedFile.edit { insertString(text.indexOf(":"), "\n") }
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
      TestCase.assertEquals("{\"bca\"\n: \n1}", injectedFile.text)
      injectedFile.edit { deleteString(0, textLength) }
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
      val injectedFile = quickEditHandler.newFile
      TestCase.assertEquals("{\"bca\"\n: \n1}", injectedFile.text)
      injectedFile.edit { deleteString(0, textLength) }
      checkResult("""
          import org.intellij.lang.annotations.Language;

          class A {
            void foo() {
              @Language("JSON") String a = "";
            }
          }
      """.trimIndent(), true)
      TestCase.assertEquals("", injectedFile.text)
      injectedFile.edit { insertString(0, "{\"bca\"\n: \n1}") }
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
      TestCase.assertEquals("{\"bca\"\n: \n1}", injectedFile.text)
      injectedFile.edit { deleteString(0, textLength) }
      checkResult("""
          import org.intellij.lang.annotations.Language;

          class A {
            void foo() {
              @Language("JSON") String a = "";
            }
          }
      """.trimIndent(), true)
      TestCase.assertEquals("", injectedFile.text)
      injectedFile.edit { insertString(0, "{\"bca\"\n: \n1}") }
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
      TestCase.assertEquals("{\"bca\"\n: \n1}", injectedFile.text)
      injectedFile.edit { deleteString(0, textLength) }
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

      val injectedFile = injectAndOpenInFragmentEditor("HTML")
      TestCase.assertEquals("<html>line\n\nfinalLine</html>", injectedFile.text)

      injectedFile.edit {
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
      injectedFile.edit {
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

      val injectedFile = injectAndOpenInFragmentEditor("HTML")
      TestCase.assertEquals("<html><head>someText1</head><body>missingValue</body></html>", injectedFile.text)

      injectedFile.edit { insertString(text.indexOf("<body>"), "someInner") }
      checkResult("""
          import org.intellij.lang.annotations.Language;

          class A {
            void foo(String bodyText) {
              String headText = "someText1";
              @Language("HTML") String concatenation = "<html><head>" + headText + "</head>someInner<body>" + bodyText + "</body></html>";
            }
          }
      """.trimIndent(), true)

      injectedFile.edit { insertString(text.indexOf("<body>") + "<body>".length, "\n") }
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
      val injectedFile = quickEditHandler.newFile
      injectionTestFixture.assertInjectedLangAtCaret("JSON")

      openFileInEditor(injectedFile.virtualFile)

      fun fillFields(num: Int) {
        var shift = injectedFile.text.indexAfter("-1,\n")
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
      val oldTestDialog = Messages.setTestDialog(TestDialog.OK)
      try {
        val undoManager = UndoManager.getInstance(project)
        val textEditor = TextEditorProvider.getInstance().getTextEditor(editor)
        action(undoManager, textEditor)
      }
      finally {
        Messages.setTestDialog(oldTestDialog)
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