// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

public class MissingAccessibleContextInspectionTest extends PluginModuleTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package javax.swing;" +
                       "public class JList<E> extends JComponent implements Scrollable, Accessible {}");
    myFixture.addClass("package javax.accessibility;" +
                       "public abstract class AccessibleContext {}");
    myFixture.addClass("package javax.swing;" +
                       "public interface ListCellRenderer<E>{" +
                       "  java.awt.Component getListCellRendererComponent(\n" +
                       "        JList<? extends E> list,\n" +
                       "        E value,\n" +
                       "        int index,\n" +
                       "        boolean isSelected,\n" +
                       "        boolean cellHasFocus);" +
                       "}");
    myFixture.enableInspections(new MissingAccessibleContextInspection());
  }

  public void testMissingAccessibleContext() {
    doTest();
  }

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH;
  }

  private void doTest() {
    myFixture.configureByFile("inspections/missingAccessibleContext/" + getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }
}
