// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang

import com.intellij.codeInsight.assertFolded
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.InjectionTestFixture
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
@RegistryKey(key = "org.intellij.intelliLang.comment.completion", value = "true")
class LanguageCommentTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  private val injectionTestFixture: InjectionTestFixture
    get() = InjectionTestFixture(myFixture)

  @Test
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
    assertThat(completionVariants).contains("java", "xml", "yaml")
  }
  
  @Test
  fun testJavaScriptLowerCaseInjected(): Unit = timeoutRunBlocking {
    myFixture.configureByText("Foo.java", """
      class Foo {
          void bar() {
              // language=jshelllanguage
              String text = "function foo(){}";
          }
      }
    """)

    readAction { injectionTestFixture.assertInjectedContent("function foo(){}") }
    myFixture.doHighlighting()
    withContext(Dispatchers.EDT) {
      myFixture.editor.assertFolded("// language=jshelllanguage", "JShellLanguage")
    }
  }
  
  @Test
  fun testJavaLanguageWordCompletion() {
    myFixture.configureByText("Foo.java", """
      class Foo {
          void bar() {
              // <caret>
              String text = "";
          }
      }
    """)
    assertThat(myFixture.complete(CompletionType.BASIC, 2).map { it.lookupString }).contains("language=")
  }
  
  @Test
  fun testJavaLanguageWordCompletionWithLetters() {
    myFixture.configureByText("Foo.java", """
      class Foo {
          void bar() {
              // la<caret>
              String text = "";
          }
      }
    """)
    assertThat(myFixture.complete(CompletionType.BASIC, 2).map { it.lookupString }).contains("language=")
  }
  
  @Test
  fun testJavaLanguageWordCompletionInNotEmptyComments() {
    myFixture.configureByText("Foo.java", """
      class Foo {
          void bar() {
              // <caret> text follows
              String text = "";
          }
      }
    """)
    assertThat(myFixture.complete(CompletionType.BASIC, 2).map { it.lookupString }).doesNotContain("language=")
  }

  @Test
  fun testJavaLanguageWordCompletionInAlreadyWritten() {
    myFixture.configureByText("Foo.java", """
      class Foo {
          void bar() {
              // language=<caret>
              String text = "";
          }
      }
    """)
    assertThat(myFixture.complete(CompletionType.BASIC, 2).map { it.lookupString }).doesNotContain("language=")
  }
  
  @Test
  fun testJavaNoLanguageCompletionInJavaDoc() {
    myFixture.configureByText("Foo.java", """
      class Foo {
          /** <caret> */
          void bar() {
          }
      }
    """)
    assertThat(myFixture.complete(CompletionType.BASIC, 2).map { it.lookupString }).doesNotContain("language=")
  }

}

