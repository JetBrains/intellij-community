// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal;

import org.jetbrains.idea.devkit.inspections.quickfix.DevKitInspectionFixTestBase;

public abstract class AbstractUseJBColorTestBase extends DevKitInspectionFixTestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UseJBColorInspection());
    myFixture.addClass("""
                         package java.awt;
                         public interface Paint {}
                         """);
    myFixture.addClass("""
                         package java.awt;
                         import java.io.Serializable;
                         public class Color implements Paint, Serializable {
                             public static final Color white = null;
                             public static final Color WHITE = null;
                             public static final Color lightGray = null;
                             public static final Color LIGHT_GRAY = null;
                             public static final Color darkGray = null;
                             public static final Color DARK_GRAY = null;
                             public static final Color black = null;
                             public static final Color BLACK = null;
                             public static final Color red = null;
                             public static final Color RED = null;
                             public static final Color yellow = null;
                             public static final Color YELLOW = null;
                             public static final Color green = null;
                             public static final Color GREEN = null;
                             public static final Color blue = null;
                             public static final Color BLUE = null;
                             public Color(int r, int g, int b) {}
                         }
                         """);
    // adding it via 'PsiTestUtil.addLibrary()' causes issues in Kotlin tests (it can't link JBColor with Color added with addClass()
    myFixture.addClass("""
                         package com.intellij.ui;
                         import java.awt.Color;
                         public class JBColor extends Color {
                             public static final JBColor white = null;
                             public static final JBColor WHITE = null;
                             public static final JBColor lightGray = null;
                             public static final JBColor LIGHT_GRAY = null;
                             public static final JBColor darkGray = null;
                             public static final JBColor DARK_GRAY = null;
                             public static final JBColor black = null;
                             public static final JBColor BLACK = null;
                             public static final JBColor red = null;
                             public static final JBColor RED = null;
                             public static final JBColor yellow = null;
                             public static final JBColor YELLOW = null;
                             public static final JBColor green = null;
                             public static final JBColor GREEN = null;
                             public static final JBColor blue = null;
                             public static final JBColor BLUE = null;
                             public JBColor(Color regular, Color dark) {}
                         }
                         """);
  }
}
