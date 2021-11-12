// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.intelliLang

class LanguageCommentTest : AbstractLanguageInjectionTestCase() {

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
}

