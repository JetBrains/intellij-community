// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

abstract class InstanceIElementTypeFieldInspectionTestBase : LightDevKitInspectionFixTestBase() {

  override fun setUp() {
    super.setUp()

    // Add mock IElementType class
    myFixture.addClass("""
      package com.intellij.psi.tree;
      public class IElementType {
        public IElementType(String debugName) {}
        public IElementType(String debugName, Object language) {}
      }
    """.trimIndent())

    // Add IElementType subtype for testing
    myFixture.addClass("""
      package com.intellij.psi.tree;
      public class MyTokenType extends IElementType {
        public MyTokenType(String debugName) { super(debugName); }
      }
    """.trimIndent())

    // Add Language class for testing
    myFixture.addClass("""
      package com.intellij.lang;
      public abstract class Language {
        public static final Language ANY = new Language() {};
        protected Language() {}
      }
    """.trimIndent())

    myFixture.enableInspections(InstanceIElementTypeFieldInspection())
  }

  override fun getBasePath(): String = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/instanceIElementTypeField"
}
