// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement;

import com.intellij.testFramework.InspectionFixtureTestCase;
import org.editorconfig.Utils;
import org.editorconfig.language.codeinsight.inspections.EditorConfigValueCorrectnessInspection;

public class EditorConfigValueInspectionTest extends InspectionFixtureTestCase {

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
    myFixture.enableInspections(EditorConfigValueCorrectnessInspection.class);
    myFixture.testHighlighting(true, false, true);
  }

  @Override
  protected String getBasePath() {
    return "/plugins/editorconfig/testData/org/editorconfig/configmanagement/inspections/" + getTestName(true);
  }

  @Override
  protected boolean isCommunity() {
    return true;
  }
}
