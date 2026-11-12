// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy;

import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class LightGroovyTestCase extends LightJavaCodeInsightFixtureTestCase {
  @NotNull
  public JavaCodeInsightTestFixture getFixture() {
    return myFixture;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    // avoid PSI/document/model changes are not allowed during highlighting
    EntryPointsManagerBase.DEAD_CODE_EP_NAME.getExtensionList();
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Override
  @NotNull
  protected LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_2_1;
  }

  /**
   * Returns the relative path to the test data.
   * The path is relative to {@link PathManager#getHomePath()}
   *
   * @return relative path to the test data.
   */
  @Override
  @NonNls
  protected String getBasePath() { return null; }

  @Override
  public @NotNull Project getProject() {
    return super.getProject();
  }
}
