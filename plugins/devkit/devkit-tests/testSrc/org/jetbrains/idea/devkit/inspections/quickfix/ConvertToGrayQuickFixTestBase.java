// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import org.jetbrains.idea.devkit.inspections.UseGrayInspection;

public abstract class ConvertToGrayQuickFixTestBase extends LightDevKitInspectionFixTestBase {

  protected static final String CONVERT_TO_GRAY_FIX_NAME_PATTERN = "Convert to 'Gray._%d'";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UseGrayInspection());
    myFixture.addClass("""
                         package java.awt;
                         public interface Paint {}
                         """);
    myFixture.addClass("""
                         package java.awt;
                         import java.io.Serializable;
                         public class Color implements Paint, Serializable {
                             public static final Color LIGHT_GRAY = null;
                             public static final Color GRAY = null;
                             public Color(int r, int g, int b) {}
                             public Color(int r, int g, int b, int a) {}
                             public Color(int rgb) {}
                         }
                         """);
    // adding it via 'PsiTestUtil.addLibrary()' causes issues in Kotlin tests (it can't link JBColor/Gray with Color added with addClass()
    myFixture.addClass("""
                         package com.intellij.ui;
                         import java.awt.Color;
                         public class JBColor extends Color {
                             public JBColor(Color regular, Color dark) {}
                         }
                         """);
    myFixture.addClass("""
                         package com.intellij.ui;
                         import java.awt.Color;
                         public final class Gray extends Color {
                           public static final Gray _25 = null;
                           public static final Gray _125 = null;
                           private Gray(int num) {
                             super(num, num, num);
                           }
                         }
                         """);
  }
}
