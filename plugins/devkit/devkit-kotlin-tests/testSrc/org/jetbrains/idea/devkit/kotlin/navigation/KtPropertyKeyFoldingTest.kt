// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.kotlin.navigation

import com.intellij.codeInsight.assertFolded
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.PathUtil

class KtPropertyKeyFoldingTest : JavaCodeInsightFixtureTestCase() {

  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    super.tuneFixture(moduleBuilder)
    moduleBuilder.addJdk(IdeaTestUtil.getMockJdk18Path().path)
    moduleBuilder.addLibrary("util-rt", PathUtil.getJarPathForClass(com.intellij.BundleBase::class.java))
  }

  fun testSingleProperty() {
    myFixture.addFileToProject("i18n.properties", "com.example.localization.welcomeMessage=Welcome to our App!")
    myFixture.addFileToProject("MyClass.kt", """
        import org.jetbrains.annotations.PropertyKey;
        import java.util.ResourceBundle;

        object MyClass {
          private const val BUNDLE_NAME = "i18n";
          private val BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);

          fun main(args: Array<String>) {
            System.out.print(getMessage("com.example.localization.welcomeMessage"));
          }

          fun getMessage(@PropertyKey(resourceBundle = BUNDLE_NAME) key: String):String {
            return BUNDLE.getString(key);
          }
        }

        fun main(args: Array<String>) {
            System.out.print(MyClass.getMessage("com.example.localization.welcomeMessage"));
        }
    """.trimIndent())

    myFixture.testHighlighting("MyClass.kt")

    with(myFixture.editor) {
      assertFolded("getMessage(\"com.example.localization.welcomeMessage\")", "\"Welcome to our App!\"")
      assertFolded("MyClass.getMessage(\"com.example.localization.welcomeMessage\")", "\"Welcome to our App!\"")
    }

  }

  fun testPropertyWithParameters() {
    myFixture.addFileToProject("i18n.properties", "com.example.localization.welcomeMessage=Welcome {0} to our App!")
    myFixture.addFileToProject("MyClass.kt", """
        import org.jetbrains.annotations.PropertyKey;
        import java.util.ResourceBundle;
        import com.intellij.BundleBase;

        object MyClass {
          private const val BUNDLE_NAME = "i18n";
          private val BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);

          fun main(args: Array<String>) {
            System.out.print(getMessage("com.example.localization.welcomeMessage", "My Friend"));
          }

          fun getMessage(@PropertyKey(resourceBundle = BUNDLE_NAME) key: String, vararg params: Any):String {
            return BundleBase.message(BUNDLE, key, *params);
          }
        }

        fun main(args: Array<String>) {
            val param = args[0];
            System.out.print(MyClass.getMessage("com.example.localization.welcomeMessage", param));
        }

    """.trimIndent())

    myFixture.testHighlighting("MyClass.kt")

    with(myFixture.editor) {
      assertFolded("getMessage(\"com.example.localization.welcomeMessage\", \"My Friend\")", "\"Welcome My Friend to our App!\"")
      assertFolded("MyClass.getMessage(\"com.example.localization.welcomeMessage\", param)", "\"Welcome {param} to our App!\"")
    }

  }

}