/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.idea.devkit.inspections.PluginModuleTestCase;

public class UndesirableClassUsageInspectionTest extends PluginModuleTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFixture.enableInspections(new UndesirableClassUsageInspection());
  }

  public void testUsages() {
    doTest("javax.swing.JList", "com.intellij.ui.components.JBList");
    doTest("javax.swing.JTable", "com.intellij.ui.table.JBTable");
    doTest("javax.swing.JTree", "com.intellij.ui.treeStructure.Tree");
    doTest("javax.swing.JScrollPane", "com.intellij.ui.components.JBScrollPane");
    doTest("javax.swing.JTabbedPane", "com.intellij.ui.components.JBTabbedPane");
    doTest("javax.swing.JComboBox", "com.intellij.openapi.ui.ComboBox");
    doTest("com.intellij.util.QueryExecutor", "com.intellij.openapi.application.QueryExecutorBase");
    doTest("java.awt.image.BufferedImage", "UIUtil.createImage()");
  }

  private void doTest(String classFqn, String replacementText) {
    myFixture.addClass("package " + StringUtil.getPackageName(classFqn) + ";" +
                       "public class " + StringUtil.getShortName(classFqn) + " {}");

    myFixture.configureByText("Testing.java",
                              "public class Testing {" +
                              " " + classFqn + " name = " +
                              "<warning descr=\"Please use '" + replacementText + "' instead\">" +
                              "new " + classFqn + "()" +
                              "</warning>;" +
                              "  public void method() {" +
                              "<warning descr=\"Please use '" + replacementText + "' instead\">" +
                              "new " + classFqn + "()" +
                              "</warning>;" +
                              "  }" +
                              "}");
    myFixture.testHighlighting();
  }
}