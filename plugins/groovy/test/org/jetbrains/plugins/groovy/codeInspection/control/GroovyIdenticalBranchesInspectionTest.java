// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.control;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.Arrays;
import java.util.stream.Collectors;

public class GroovyIdenticalBranchesInspectionTest extends GrHighlightingTestBase {
  @Override
  public @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }

  @Override
  public String getBasePath() {
    return TestUtils.getTestDataPath() + "inspections/identicalBranches/";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    getFixture().enableInspections(GroovyConditionalWithIdenticalBranchesInspection.class,
                                   GroovyIfStatementWithIdenticalBranchesInspection.class);
  }

  public void testTwoNewExpressions() { doTest(); }

  public void testEmptyListAndMap() { doTest(); }

  public void testIdenticalArrays() { doTest(); }

  @NotNull
  @Override
  protected String getTestName(boolean lowercaseFirstLetter) {
    String name = getName();
    if (name.contains(" ")) {
      name = Arrays.stream(name.split(" ")).map(it -> it.equals("test") ? it : StringUtil.capitalize(it)).collect(Collectors.joining(""));
    }

    return UsefulTestCase.getTestName(name, lowercaseFirstLetter);
  }
}
