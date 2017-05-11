/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ide.codeStyleSettings;

import com.intellij.application.options.codeStyle.CodeStyleSchemesModel;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemeImpl;
import com.intellij.testFramework.LightPlatformTestCase;

public class CodeStyleSchemesModelTest extends LightPlatformTestCase {
  private CodeStyleSchemesModel myModel;
  private CodeStyleScheme myDefaultScheme;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myModel = new CodeStyleSchemesModel(getProject());
    myDefaultScheme = myModel.getSelectedScheme();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      for (CodeStyleScheme scheme : myModel.getSchemes().toArray(new CodeStyleScheme[myModel.getSchemes().size()])) {
        if (myModel.canDeleteScheme(scheme)) {
          myModel.removeScheme(scheme);
        }
        else if (myModel.canResetScheme(scheme)) {
          scheme.resetToDefaults();
        }
      }
      CodeStyleScheme projectScheme = myModel.getProjectScheme();
      ((CodeStyleSchemeImpl)projectScheme).setCodeStyleSettings(new CodeStyleSettings());
      myModel.setUsePerProjectSettings(false);
      myModel.selectScheme(myDefaultScheme, null);
      myModel.apply();
    }
    finally {
      super.tearDown();
    }
  }

  public void testDefaults() throws Exception {
    CodeStyleScheme defaultScheme = myModel.getSelectedScheme();
    assertEquals("Default", defaultScheme.getName());
    assertEquals(new CodeStyleSettings(), defaultScheme.getCodeStyleSettings());
  }

  public void testCopyToIde() throws Exception {
    CodeStyleScheme projectScheme = myModel.getProjectScheme();
    myModel.setUsePerProjectSettings(true);
    assertEquals(CodeStyleSchemesModel.PROJECT_SCHEME_NAME, myModel.getSelectedScheme().getName());
    CodeStyleSettings settings = projectScheme.getCodeStyleSettings();
    settings.setRightMargin(null, 66);
    CodeStyleScheme newScheme = myModel.exportProjectScheme("New Scheme");
    assertEquals(66, newScheme.getCodeStyleSettings().getRightMargin(null));
    // Check that code style settings instance is not the same as in the original
    assertFalse(projectScheme.getCodeStyleSettings() == newScheme.getCodeStyleSettings());
  }

  public void testCopyToProject() {
    CodeStyleSettings defaultSettings = new CodeStyleSettings();
    CodeStyleScheme projectScheme = myModel.getProjectScheme();
    assertEquals(defaultSettings.getDefaultRightMargin(), projectScheme.getCodeStyleSettings().getDefaultRightMargin());
    CodeStyleScheme scheme = myModel.createNewScheme("New Scheme", myModel.getSelectedScheme());
    CodeStyleSettings settings = scheme.getCodeStyleSettings();
    settings.setDefaultRightMargin(66);
    myModel.copyToProject(scheme);
    assertEquals(66, projectScheme.getCodeStyleSettings().getDefaultRightMargin());
  }
}
