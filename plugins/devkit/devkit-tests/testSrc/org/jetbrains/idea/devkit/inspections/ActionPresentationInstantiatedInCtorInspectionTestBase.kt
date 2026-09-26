// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

abstract class ActionPresentationInstantiatedInCtorInspectionTestBase : PluginModuleTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.addClass("package javax.swing; public interface Icon {}")
    myFixture.addClass("""
      package com.intellij.openapi.actionSystem;

      import javax.swing.Icon;
      import java.util.function.Supplier;

      public class AnAction {

        public AnAction() {}

        public AnAction(Icon icon) {}

        public AnAction(String text) {}

        public AnAction(Supplier<String> dynamicText) {}

        public AnAction(String text,
                        String description,
                        Icon icon) {}

        public AnAction(Supplier<String> dynamicText, Icon icon) {}

        public AnAction(Supplier<String> dynamicText,
                        Supplier<String> dynamicDescription,
                        Icon icon) {}
      }
      """)
    myFixture.enableInspections(ActionPresentationInstantiatedInCtorInspection::class.java)
  }

  protected abstract fun getFileExtension(): String

  protected fun doTest() {
    myFixture.testHighlighting(getTestName(false) + "." + getFileExtension())
  }
}