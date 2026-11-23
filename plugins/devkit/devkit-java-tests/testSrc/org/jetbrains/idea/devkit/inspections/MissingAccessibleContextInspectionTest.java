// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.jetbrains.idea.devkit.inspections.PluginModuleFixtureKt.pluginModuleFixture;
import static org.jetbrains.idea.devkit.inspections.PluginModuleFixtureKt.pluginModuleProjectFixture;

@TestApplication
public class MissingAccessibleContextInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = pluginModuleProjectFixture();
  private final TestFixture<PluginModuleFixture> pluginModule = pluginModuleFixture(projectFixture, DevkitJavaTestsUtil.TESTDATA_PATH);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = pluginModule.get().getFixture();
    myFixture.addClass("package javax.swing;" +
                       "public class JList<E> extends JComponent implements Scrollable, Accessible {}");
    myFixture.addClass("package javax.accessibility;" +
                       "public abstract class AccessibleContext {}");
    myFixture.addClass("""
                         package javax.swing;public interface ListCellRenderer<E>{  java.awt.Component getListCellRendererComponent(
                                 JList<? extends E> list,
                                 E value,
                                 int index,
                                 boolean isSelected,
                                 boolean cellHasFocus);}""");
    myFixture.enableInspections(new MissingAccessibleContextInspection());
  }

  @Test
  public void testMissingAccessibleContext() {
    doTest();
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = pluginModule.get().getFixture();
    myFixture.configureByFile("inspections/missingAccessibleContext/" + myFixture.getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }
}
