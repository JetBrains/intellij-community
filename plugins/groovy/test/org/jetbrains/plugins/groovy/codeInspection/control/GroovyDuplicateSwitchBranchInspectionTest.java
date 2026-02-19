// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.control;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.validity.GroovyDuplicateSwitchBranchInspection;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase;
import org.jetbrains.plugins.groovy.util.TestUtils;

public class GroovyDuplicateSwitchBranchInspectionTest extends GrHighlightingTestBase {
  @Override
  public @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }

  @Override
  public String getBasePath() {
    return TestUtils.getTestDataPath() + "inspections/identicalSwitchBranches/";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    getFixture().enableInspections(GroovyDuplicateSwitchBranchInspection.class);
  }

  public void testSwitch() { doTest(); }
}
