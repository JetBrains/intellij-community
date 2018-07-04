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

import org.jetbrains.idea.devkit.inspections.PluginModuleTestCase;

public class UseVirtualFileEqualsInspectionTest extends PluginModuleTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFixture.addClass("package com.intellij.openapi.vfs; public class VirtualFile {}");
    myFixture.enableInspections(new UseVirtualFileEqualsInspection());
  }

  public void testNulls() {
    doTest("vf1 == null");
    doTest("null == vf1");
  }

  public void testThis() {
    doTest("this == this");
    doTest("getTesting() == this");
  }

  public void testVirtualFile() {
    doTest("<warning descr=\"VirtualFile objects should be compared by equals(), not ==\">vf1 != vf2</warning>");
    doTest("<warning descr=\"VirtualFile objects should be compared by equals(), not ==\">vf1 == vf2</warning>");
  }

  private void doTest(String expression) {
    myFixture.configureByText("Testing.java",
                              "import com.intellij.openapi.vfs.VirtualFile;" +
                              "public class Testing {" +
                              "  public void method() {" +
                              "    VirtualFile vf1 = null;" +
                              "    VirtualFile vf2 = null;" +
                              "    if (" + expression + ") {}" +
                              "  }" +
                              "  public Testing getTesting() { return null; }" +
                              "}");
    myFixture.testHighlighting();
  }
}