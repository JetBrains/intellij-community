// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.navigation

import com.intellij.BundleBase
import com.intellij.codeInsight.assertFolded
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.PathUtil
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

class KtFirPropertyKeyFoldingTest : JavaCodeInsightFixtureTestCase(), ExpectedPluginModeProvider {

  override fun setUp() {
    setUpWithKotlinPlugin { super.setUp() }
    ModuleRootModificationUtil.updateModel(module, DefaultLightProjectDescriptor::addJetBrainsAnnotations)
  }

  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    super.tuneFixture(moduleBuilder)
    moduleBuilder.addJdk(IdeaTestUtil.getMockJdk18Path().path)
    moduleBuilder.addLibrary("platform-util", PathUtil.getJarPathForClass(BundleBase::class.java))
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

        fun main() {
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

  fun testUnresolvedPropertyWithParameters() {
    myFixture.addFileToProject("MyClass.kt", """
        import org.jetbrains.annotations.PropertyKey;
        import java.util.ResourceBundle;
        import com.intellij.BundleBase;

        object MyClass {
          private const val BUNDLE_NAME = "i18n";
          private val BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);

          fun main(args: Array<String>) {
            val msg = getMessage(2, "unresolved", "My Friend")
          }

          fun getMessage(p: Int, @PropertyKey(resourceBundle = BUNDLE_NAME) key: String, vararg params: Any):String {
            return BundleBase.message(BUNDLE, key, *params);
          }
        }

        fun main(args: Array<String>) {
            val msg = MyClass.getMessage(1, "unresolved", "abc")
        }

    """.trimIndent())

    myFixture.testHighlighting("MyClass.kt")

  }

  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

}