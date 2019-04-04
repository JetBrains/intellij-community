// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.siyeh.ig.LightInspectionTestCase;
import org.editorconfig.Utils;
import org.editorconfig.language.codeinsight.inspections.EditorConfigValueCorrectnessInspection;
import org.jetbrains.annotations.Nullable;

public class EditorConfigValueInspectionTest extends LightInspectionTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Utils.setFullIntellijSettingsSupportEnabledInTest(true);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Utils.setFullIntellijSettingsSupportEnabledInTest(false);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testValues() {
    myFixture.configureByFile(".editorconfig");
    myFixture.testHighlighting(true, false, true);
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new EditorConfigValueCorrectnessInspection();
  }

  @Override
  protected String getBasePath() {
    return "/plugins/editorconfig/testData/org/editorconfig/configmanagement/inspections/";
  }
}
