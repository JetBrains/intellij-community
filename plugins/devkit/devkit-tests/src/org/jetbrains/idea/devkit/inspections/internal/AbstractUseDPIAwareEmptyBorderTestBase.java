// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractUseDPIAwareEmptyBorderTestBase extends JavaCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UseDPIAwareEmptyBorderInspection());
    myFixture.addClass("""
                         package javax.swing.border;
                         import java.awt.Insets;
                         public class EmptyBorder implements Border {
                           public EmptyBorder(int top, int left, int bottom, int right) {}
                           public EmptyBorder(Insets borderInsets) {}
                         }
                         """);
    myFixture.addClass("""
                         package com.intellij.util.ui;
                         import javax.swing.border.EmptyBorder;
                         public class JBEmptyBorder extends EmptyBorder {}
                         """);
    myFixture.addClass("""
                         package java.awt;
                         public class Insets {
                             public Insets(int top, int left, int bottom, int right) {}
                         }
                         """);
    myFixture.addClass("""
                         package javax.swing.border;
                         public interface Border {}
                         """);
    // adding it via 'PsiTestUtil.addLibrary()' causes issues in Kotlin tests (it can't link JBUI with Border added with addClass()
    myFixture.addClass("""
                         package com.intellij.util.ui;
                         import java.awt.Insets;
                         import javax.swing.border.Border;
                         public final class JBUI {
                           public static final class Borders {
                             public static JBEmptyBorder empty() {return null;}
                             public static Border empty(int offsets) {return null;}
                             public static JBEmptyBorder empty(int topAndBottom, int leftAndRight) {return null;}
                             public static JBEmptyBorder empty(int top, int left, int bottom, int right) {return null;}
                             public static Border empty(Insets insets) {return null;}
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
