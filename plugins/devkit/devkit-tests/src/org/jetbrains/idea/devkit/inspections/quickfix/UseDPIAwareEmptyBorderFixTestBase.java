// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.internal.UseDPIAwareEmptyBorderInspection;

public abstract class UseDPIAwareEmptyBorderFixTestBase extends JavaCodeInsightFixtureTestCase {

  @SuppressWarnings("SSBasedInspection")
  protected static final String SIMPLIFY_FIX_NAME =
    DevKitBundle.message("inspections.use.dpi.aware.empty.border.family.name");
  @SuppressWarnings("SSBasedInspection")
  protected static final String CONVERT_TO_JB_UI_BORDERS_EMPTY_FIX_NAME =
    DevKitBundle.message("inspections.use.dpi.aware.empty.border.convert.fix.family.name");

  @NotNull
  protected abstract String getFileExtension();

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

  protected void doTest(String fixName, String before, String after) {
    myFixture.configureByText("TestClass." + getFileExtension(), before);
    myFixture.checkHighlighting();
    IntentionAction intention = myFixture.findSingleIntention(fixName);
    myFixture.checkPreviewAndLaunchAction(intention);
    myFixture.checkResult(after, true);
  }
}
