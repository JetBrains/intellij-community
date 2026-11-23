// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.EDT
import com.intellij.psi.injection.Injectable
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.InjectionTestFixture
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.intelliLang.inject.InjectLanguageAction
import org.jetbrains.yaml.YAMLLanguage
import org.junit.jupiter.api.Test

@TestApplication
class YamlInjectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun testYamlSameLineAnnotatorInInjection(): Unit = timeoutRunBlocking {
    myFixture.configureByText("Foo.java", """
      class Foo {
          void bar() {
              baz("key:\n" +
                  "  <caret>- item1\n" +
                  "  - item2");
          }

          void baz(String yaml){}
      }
    """)

    withContext(Dispatchers.EDT) {
      InjectLanguageAction.invokeImpl(myFixture.project,
                                      myFixture.editor,
                                      myFixture.file,
                                      Injectable.fromLanguage(YAMLLanguage.INSTANCE))

      InjectionTestFixture(myFixture).assertInjectedLangAtCaret("yaml")
    }

    val highlights = myFixture.doHighlighting(HighlightSeverity.ERROR)

    if (highlights.isNotEmpty()) {
      throw AssertionError("Found unexpected errors: $highlights")
    }
  }
}

