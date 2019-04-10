// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.injected

import com.intellij.codeInsight.intention.impl.QuickEditAction
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.injection.Injectable
import com.intellij.testFramework.fixtures.InjectionTestFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
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


  private fun PsiFile.edit(docFunction: Document.() -> Unit) {
    CommandProcessor.getInstance().executeCommand(project, {
      ApplicationManager.getApplication().runWriteAction {
        val manager = PsiDocumentManager.getInstance(project)
        val document = manager.getDocument(this) ?: throw AssertionError("document is null")
        document.docFunction()
        manager.commitDocument(document)
        //        manager.commitAllDocuments()
      }
    }, "change doc", "")
  }

  private fun Document.findAndDelete(substring: String) {
    val start = this.text.indexOf(substring)
    deleteString(start, start + substring.length)
  }

  private val injectionTestFixture: InjectionTestFixture get() = InjectionTestFixture(myFixture)

}