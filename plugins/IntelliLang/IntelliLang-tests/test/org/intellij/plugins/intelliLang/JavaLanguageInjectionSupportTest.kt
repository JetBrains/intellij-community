// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.intelliLang

import com.intellij.lang.Language
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.injection.Injectable
import com.intellij.psi.util.parentOfType
import com.intellij.util.ui.UIUtil
import junit.framework.TestCase
import org.intellij.plugins.intelliLang.inject.InjectLanguageAction
import org.intellij.plugins.intelliLang.inject.InjectorUtils
import org.intellij.plugins.intelliLang.inject.UnInjectLanguageAction
import org.intellij.plugins.intelliLang.inject.config.BaseInjection
import org.intellij.plugins.intelliLang.inject.config.InjectionPlace
import org.intellij.plugins.intelliLang.inject.java.JavaLanguageInjectionSupport

class JavaLanguageInjectionSupportTest : AbstractLanguageInjectionTestCase() {

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

    UnInjectLanguageAction.invokeImpl(project, topLevelEditor, topLevelFile)

    assertInjectedLangAtCaret(null)
  }


  fun testConfigInjection() {

    fun currentPrintlnInjection(): BaseInjection? {
      val psiMethod = topLevelFile.findElementAt(topLevelCaretPosition)!!.parentOfType<PsiMethodCallExpression>()!!.resolveMethod()!!
      val injection = JavaLanguageInjectionSupport.makeParameterInjection(psiMethod, 0, "JSON");
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

    val customInjection = BaseInjection("java").apply {
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

    Configuration.getInstance().replaceInjections(listOf(customInjection), listOf(), true)

    try {
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
    finally {
      Configuration.getInstance().replaceInjections(listOf(), listOf(customInjection), true)
    }
  }

  private fun undo(editor: Editor) {
    UIUtil.invokeAndWaitIfNeeded(Runnable {
      val oldTestDialog = Messages.setTestDialog(TestDialog.OK)
      try {
        val undoManager = UndoManager.getInstance(project)
        val textEditor = TextEditorProvider.getInstance().getTextEditor(editor)
        undoManager.undo(textEditor)
      }
      finally {
        Messages.setTestDialog(oldTestDialog)
      }
    })
  }


}

