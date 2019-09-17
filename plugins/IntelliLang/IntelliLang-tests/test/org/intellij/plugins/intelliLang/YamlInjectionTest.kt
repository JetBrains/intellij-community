// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.intelliLang

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.injection.Injectable
import org.intellij.plugins.intelliLang.inject.InjectLanguageAction
import org.jetbrains.yaml.YAMLLanguage

class YamlInjectionTest : AbstractLanguageInjectionTestCase() {

  fun testYamlSameLineAnnotatorInInjection() {
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

    InjectLanguageAction.invokeImpl(project,
                                    myFixture.editor,
                                    myFixture.file,
                                    Injectable.fromLanguage(YAMLLanguage.INSTANCE))

    assertInjectedLangAtCaret("yaml")

    val highlights = myFixture.doHighlighting(HighlightSeverity.ERROR)

    if (highlights.isNotEmpty()) {
      throw AssertionError("Found unexpected errors: $highlights")
    }
  }
}

