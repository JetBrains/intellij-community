// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

abstract class AnActionInitializesTemplatePresentationInCtorInspectionTestBase : PluginModuleTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.addClass("package javax.swing; public interface Icon {}")
    myFixture.addClass("package java.util.function; public interface Supplier<T> { T get();}")
    myFixture.addClass("""
      package com.intellij.openapi.actionSystem;

      import javax.swing.Icon;
      import java.util.function.Supplier;

      @SuppressWarnings("ALL") public class AnAction {
        static final Supplier<String> NULL_STRING = () -> null;

        public AnAction() {}

        public AnAction(Icon icon) {
          this(NULL_STRING, NULL_STRING, icon);
        }

        public AnAction(String text) {
          this(text, null, null);
        }

        public AnAction(Supplier<String> dynamicText) {
          this(dynamicText, NULL_STRING, null);
        }

        public AnAction(String text,
                        String description,
                        Icon icon) {
          this(NULL_STRING, NULL_STRING, icon);
        }

        public AnAction(Supplier<String> dynamicText, Icon icon) {
          this(dynamicText, NULL_STRING, icon);
        }

        public AnAction(Supplier<String> dynamicText,
                        Supplier<String> dynamicDescription,
                        Icon icon) { }
      }
      """)
    myFixture.enableInspections(AnActionInitializesTemplatePresentationInCtorInspection::class.java)
  }

  protected abstract fun getFileExtension(): String

  protected fun doTest() {
    myFixture.testHighlighting(getTestName(false) + "." + getFileExtension())
  }
}