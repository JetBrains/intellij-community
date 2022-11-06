// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public abstract class UseGrayInspectionTestBase extends JavaCodeInsightFixtureTestCase {

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
                           private Gray(int num) {
                             super(num, num, num);
                           }
                         }
                         """);
  }

  protected void doTest() {
    myFixture.testHighlighting(getTestName(false) + '.' + getFileExtension());
  }

  @NotNull
  protected abstract String getFileExtension();
}
