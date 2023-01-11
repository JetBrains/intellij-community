// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractUseDPIAwareInsetsTestBase extends JavaCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UseDPIAwareInsetsInspection());
    myFixture.addClass("""
                         package java.awt;
                         public class Insets {
                           public Insets(int top, int left, int bottom, int right) {}
                         }
                         """);
    // adding it via 'PsiTestUtil.addLibrary()' causes issues in Kotlin tests (it can't link JBInsets/JBUI with Border added with addClass()
    myFixture.addClass("""
                         package com.intellij.util.ui;
                         import java.awt.Insets;
                         public class JBInsets extends Insets {
                           public static JBInsets create(Insets insets) {return null;}
                         }
                         """);
    myFixture.addClass("""
                         package com.intellij.util.ui;
                         import java.awt.Insets;
                         public final class JBUI {
                           public static JBInsets insets(int top, int left, int bottom, int right) {return null;}
                           public static JBInsets insets(int all) {return null;}
                           public static JBInsets insets(String propName, JBInsets defaultValue) {return null;}
                           public static JBInsets insets(int topBottom, int leftRight) {return null;}
                           public static JBInsets emptyInsets() {return null;}
                           public static JBInsets insetsTop(int t) {return null;}
                           public static JBInsets insetsLeft(int l) {return null;}
                           public static JBInsets insetsBottom(int b) {return null;}
                           public static JBInsets insetsRight(int r) {return new JBInsets(0, 0, 0, r);}
                           public static JBInsets insets(Insets insets) {return null;}
                         }
                         """);
  }

  @NotNull
  protected abstract String getFileExtension();
}
