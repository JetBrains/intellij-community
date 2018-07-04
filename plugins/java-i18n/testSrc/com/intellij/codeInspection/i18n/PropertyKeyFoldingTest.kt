// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.i18n

import com.intellij.codeInsight.assertFolded
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.PathUtil

class PropertyKeyFoldingTest : JavaCodeInsightFixtureTestCase() {

  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    super.tuneFixture(moduleBuilder)
    moduleBuilder.addJdk(IdeaTestUtil.getMockJdk18Path().path)
    moduleBuilder.addLibrary("util-rt", PathUtil.getJarPathForClass(com.intellij.BundleBase::class.java))
  }

  fun testSingleProperty() {
    myFixture.addFileToProject("i18n.properties", "com.example.localization.welcomeMessage=Welcome to our App!")
    myFixture.addClass("""
        import org.jetbrains.annotations.PropertyKey;
        import java.util.ResourceBundle;

        public class MyClass {
          private final static String BUNDLE_NAME = "i18n";
          private final static ResourceBundle BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);

          public static void main(String[] args) {
            System.out.print(getMessage("com.example.localization.welcomeMessage"));
            System.out.print(MyClass.getMessage("com.example.localization.welcomeMessage"));
          }


          public static String getMessage(@PropertyKey(resourceBundle = BUNDLE_NAME) String key) {
            return BUNDLE.getString(key);
          }
        }
    """.trimIndent())


    myFixture.testHighlighting("MyClass.java")

    with(myFixture.editor) {
      assertFolded("getMessage(\"com.example.localization.welcomeMessage\")", "\"Welcome to our App!\"")
      assertFolded("MyClass.getMessage(\"com.example.localization.welcomeMessage\")", "\"Welcome to our App!\"")
    }

  }

  fun testPropertyWithParameters() {
    myFixture.addFileToProject("i18n.properties", "com.example.localization.welcomeMessage=Welcome {0} to our App!")
    myFixture.addClass("""
        import org.jetbrains.annotations.PropertyKey;
        import java.util.ResourceBundle;
        import com.intellij.BundleBase;

        public class MyClass {
          private final static String BUNDLE_NAME = "i18n";
          private final static ResourceBundle BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);

          public static void main(String[] args) {
            System.out.print(getMessage("com.example.localization.welcomeMessage", "My Friend"));
            String param = args[0];
            System.out.print(MyClass.getMessage("com.example.localization.welcomeMessage", param));
          }

          public static String getMessage(@PropertyKey(resourceBundle = BUNDLE_NAME) String key, Object... params) {
            return BundleBase.message(BUNDLE, key, params);
          }
        }
    """.trimIndent())

    myFixture.testHighlighting("MyClass.java")

    with(myFixture.editor) {
      assertFolded("getMessage(\"com.example.localization.welcomeMessage\", \"My Friend\")", "\"Welcome My Friend to our App!\"")
      assertFolded("MyClass.getMessage(\"com.example.localization.welcomeMessage\", param)", "\"Welcome {param} to our App!\"")
    }

  }

}