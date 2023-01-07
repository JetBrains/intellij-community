// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.ui.JBColor;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public abstract class UseJBColorInspectionTestBase extends JavaCodeInsightFixtureTestCase {

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

  protected void doTest() {
    myFixture.testHighlighting(getTestName(false) + '.' + getFileExtension());
  }

  @NotNull
  protected abstract String getFileExtension();
}
