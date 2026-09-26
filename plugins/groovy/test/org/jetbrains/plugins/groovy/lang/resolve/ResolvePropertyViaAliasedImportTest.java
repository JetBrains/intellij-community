// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase;
import org.jetbrains.plugins.groovy.util.TestUtils;

public class ResolvePropertyViaAliasedImportTest extends GrHighlightingTestBase {

  @NotNull
  private final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST;

  @NotNull
  private final String basePath = TestUtils.getTestDataPath() + "resolve/imports";

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return projectDescriptor;
  }

  @Override
  public final @NotNull String getBasePath() {
    return basePath;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    getFixture().addFileToProject("com/foo/Bar.groovy", """
      package com.foo
      class Bar {
        static def myProperty = 'hello'
      }
      """);
  }

  private void doTest() {
    getFixture().enableInspections(GrUnresolvedAccessInspection.class, GroovyAssignabilityCheckInspection.class);
    getFixture().testHighlighting(getTestName() + ".groovy");
  }

  public void testGetterWithAlias() {
    doTest();
  }

  public void testGetterWithGetterAlias() {
    doTest();
  }

  public void testGetterWithSetterAlias() {
    doTest();
  }

  public void testPropertyWithAlias() {
    doTest();
  }

  public void testPropertyWithGetterAlias() {
    doTest();
  }

  public void testPropertyWithSetterAlias() {
    doTest();
  }

  public void testSetterWithAlias() {
    doTest();
  }

  public void testSetterWithGetterAlias() {
    doTest();
  }

  public void testSetterWithSetterAlias() {
    doTest();
  }
}
