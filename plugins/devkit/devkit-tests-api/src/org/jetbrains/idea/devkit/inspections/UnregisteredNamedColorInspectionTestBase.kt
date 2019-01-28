// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections

import org.jetbrains.idea.devkit.util.PsiUtil

abstract class UnregisteredNamedColorInspectionTestBase : PluginModuleTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(UnregisteredNamedColorInspection())
    PsiUtil.markAsIdeaProject(myFixture.project, true)

    //language=JAVA
    myFixture.addClass("""
      package com.intellij.ui;

      public class JBColor {
        public static void namedColor(String s, int i) {}
      }
    """.trimIndent())

    //language=JAVA
    myFixture.addClass("""
      package org.jetbrains.idea.devkit.completion;

      public class UiDefaultsHardcodedKeys {
        public static final Set<String> UI_DEFAULTS_KEYS = Sets.newHashSet();
        public static final Set<String> NAMED_COLORS = Sets.newHashSet(
          "RegisteredKey"
        );
        public static final Set<String> ALL_KEYS = Sets.union(UI_DEFAULTS_KEYS, NAMED_COLORS);
      }
    """.trimIndent())
  }

  override fun tearDown() {
    try {
      PsiUtil.markAsIdeaProject(myFixture.project, false)
    }
    finally {
      super.tearDown()
    }
  }
}
