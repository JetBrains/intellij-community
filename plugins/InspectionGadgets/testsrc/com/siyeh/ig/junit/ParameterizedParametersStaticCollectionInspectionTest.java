// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.junit;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class ParameterizedParametersStaticCollectionInspectionTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return LightInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/junit/parameterized";
  }


  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package org.junit.runner;\n" +
                       "public @interface RunWith {\n" +
                       "    Class value();\n" +
                       "}\n");
    myFixture.addClass("package org.junit.runners;\n" +
                       "public class Parameterized {" +
                       "    public @interface Parameters {\n" +
                       "        String name() default \"{index}\";\n" +
                       "    }" +
                       "} ");
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  private void doTest() {
    myFixture.enableInspections(new ParameterizedParametersStaticCollectionInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testCreatemethod() {
    doTest();
  }

  public void testWrongsignature() { doTest(); }
  public void testWrongsignature1() { doTest(); }
  public void testWrongsignature2() { doTest(); }

}