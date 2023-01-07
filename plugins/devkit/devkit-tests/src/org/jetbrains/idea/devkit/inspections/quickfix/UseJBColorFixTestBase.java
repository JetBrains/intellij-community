// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.ui.JBColor;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.internal.UseJBColorInspection;

public abstract class UseJBColorFixTestBase extends JavaCodeInsightFixtureTestCase {

  protected static final String CONVERT_TO_JB_COLOR_FIX_NAME = "Convert to 'JBColor'";
  protected static final String CONVERT_TO_JB_COLOR_CONSTANT_FIX_NAME = "Convert to 'JBColor' constant";

  @NotNull
  protected abstract String getFileExtension();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UseJBColorInspection());
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
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) throws Exception {
    moduleBuilder.addLibrary("platform-util-ui", PathUtil.getJarPathForClass(JBColor.class));
  }

  protected void doTest(String fixName) {
    String testName = getTestName(false);
    String fileNameBefore = testName + '.' + getFileExtension();
    String fileNameAfter = testName + "_after." + getFileExtension();
    myFixture.testHighlighting(fileNameBefore);
    IntentionAction intention = myFixture.findSingleIntention(fixName);
    myFixture.checkPreviewAndLaunchAction(intention);
    myFixture.checkResultByFile(fileNameBefore, fileNameAfter, true);
  }
}
