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

public class FileEqualsUsageInspectionTest extends PluginModuleTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFixture.enableInspections(new FileEqualsUsageInspection());
  }

  public void testEquals() {
    doTest("equals(null)", true);
  }

  public void testCompareTo() {
    doTest("compareTo(null)", true);
  }

  public void testHashCode() {
    doTest("hashCode()", true);
  }

  public void testGetName() {
    doTest("getName()", false);
  }

  private void doTest(String methodExpressionText, boolean highlightError) {
    String expectedMethodExpression;
    if (highlightError) {
      String methodName = StringUtil.substringBefore(methodExpressionText, "(");
      String methodParams = StringUtil.substringAfter(methodExpressionText, methodName);
      expectedMethodExpression = "<warning descr=\"" + FileEqualsUsageInspection.MESSAGE + "\">" +
                                 methodName +
                                 "</warning>" +
                                 methodParams;
    }
    else {
      expectedMethodExpression = methodExpressionText;
    }

    myFixture.configureByText("Testing.java",
                              "public class Testing {" +
                              "  public void method() {" +
                              "     java.io.File file = null;" +
                              "     file." + expectedMethodExpression + ";" +
                              "  }" +
                              "}");
    myFixture.testHighlighting();
  }
}