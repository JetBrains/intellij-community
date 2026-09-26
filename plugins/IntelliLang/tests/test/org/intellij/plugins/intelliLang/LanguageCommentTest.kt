// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang

import com.intellij.codeInsight.assertFolded
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.util.registry.Registry

class LanguageCommentTest : AbstractLanguageInjectionTestCase() {

  override fun setUp() {
    super.setUp()
    Registry.get("org.intellij.intelliLang.comment.completion").setValue(true, testRootDisposable)
  }

  fun testJavaCommentCompletion() {
    myFixture.configureByText("Foo.java", """
      class Foo {
          void bar() {
              // language=<caret>
              String text = "";
          }
      }
    """)

    val completionVariants = myFixture.getCompletionVariants("Foo.java")!!
    assertContainsElements(completionVariants, "java", "xml", "yaml")
  }
  
  fun testJavaScriptLowerCaseInjected() {
    myFixture.configureByText("Foo.java", """
      class Foo {
          void bar() {
              // language=jshelllanguage
              String text = "function foo(){}";
          }
      }
    """)

    injectionTestFixture.assertInjectedContent("function foo(){}")
    myFixture.doHighlighting()
    myFixture.editor.assertFolded("// language=jshelllanguage", "JShellLanguage")
  }
  
  fun testJavaLanguageWordCompletion() {
    myFixture.configureByText("Foo.java", """
      class Foo {
          void bar() {
              // <caret>
              String text = "";
          }
      }
    """)
    assertContainsElements(myFixture.complete(CompletionType.BASIC, 2).map { it.lookupString }, "language=")
  }
  
  fun testJavaLanguageWordCompletionWithLetters() {
    myFixture.configureByText("Foo.java", """
      class Foo {
          void bar() {
              // la<caret>
              String text = "";
          }
      }
    """)
    assertContainsElements(myFixture.complete(CompletionType.BASIC, 2).map { it.lookupString }, "language=")
  }
  
  fun testJavaLanguageWordCompletionInNotEmptyComments() {
    myFixture.configureByText("Foo.java", """
      class Foo {
          void bar() {
              // <caret> text follows
              String text = "";
          }
      }
    """)
    assertDoesntContain(myFixture.complete(CompletionType.BASIC, 2).map { it.lookupString }, "language=")
  }

  fun testJavaLanguageWordCompletionInAlreadyWritten() {
    myFixture.configureByText("Foo.java", """
      class Foo {
          void bar() {
              // language=<caret>
              String text = "";
          }
      }
    """)
    assertDoesntContain(myFixture.complete(CompletionType.BASIC, 2).map { it.lookupString }, "language=")
  }
  
  fun testJavaNoLanguageCompletionInJavaDoc() {
    myFixture.configureByText("Foo.java", """
      class Foo {
          /** <caret> */
          void bar() {
          }
      }
    """)
    assertDoesntContain(myFixture.complete(CompletionType.BASIC, 2).map { it.lookupString }, "language=")
  }

}

