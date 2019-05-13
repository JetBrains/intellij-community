/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.execution.junit;

import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class JUnitUnusedMethodsTest extends LightCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package org.junit; public @interface Test {}");
    myFixture.addClass("package org.junit.runners; public class Parameterized { public @interface Parameters {} public @interface Parameter {}}");
    myFixture.addClass("package org.junit.runner; public @interface RunWith {Class value();}");
    myFixture.enableInspections(new UnusedDeclarationInspection(true));
  }

  public void testRecognizeTestMethodInParameterizedClass() {
    myFixture.configureByText("A.java", "import org.junit.Test;\n" +
                                        "import org.junit.runner.RunWith;\n" +
                                        "import org.junit.runners.Parameterized;\n" +
                                        "import java.util.*;\n" +
                                        "@RunWith(Parameterized.class)\n" +
                                        "public class A {\n" +
                                        "  @Parameterized.Parameters\n" +
                                        "  public static Collection<Object[]> data() {\n" +
                                        "    return Arrays.asList(new Object[] {\"11\"}, new Object[] {\"12\"});\n" +
                                        "  }\n" +
                                        "  @Parameterized.Parameter\n" +
                                        "  public String myJUnitVersion;\n" +
                                        "  @Test\n" +
                                        "  public void ignoredTestMethod() throws Exception {}\n" +
                                        "}\n");
    myFixture.testHighlighting(true, false, false);
  }
}
