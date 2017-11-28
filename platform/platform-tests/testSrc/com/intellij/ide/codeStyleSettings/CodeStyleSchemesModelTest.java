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
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemeImpl;

import java.util.Arrays;
import java.util.List;

public class CodeStyleSchemesModelTest extends CodeStyleTestCase {
  private CodeStyleSchemesModel myModel;
  private CodeStyleScheme myDefaultScheme;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
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
          myModel.restoreDefaults(scheme);
        }
      }
      CodeStyleScheme projectScheme = myModel.getProjectScheme();
      ((CodeStyleSchemeImpl)projectScheme).setCodeStyleSettings(new CodeStyleSettings());
      myModel.selectScheme(myDefaultScheme, null);
      myModel.apply();
    }
    finally {
      super.tearDown();
    }
  }

  public void testDefaults() {
    CodeStyleScheme defaultScheme = myModel.getSelectedScheme();
    assertEquals(CodeStyleScheme.DEFAULT_SCHEME_NAME, defaultScheme.getName());
    assertEquals(new CodeStyleSettings(), defaultScheme.getCodeStyleSettings());
    assertFalse(myModel.isSchemeListModified());
    assertFalse(myModel.isUsePerProjectSettings());
  }

  public void testCopyToIde() {
    CodeStyleScheme projectScheme = myModel.getProjectScheme();
    myModel.selectScheme(projectScheme, null);
    assertEquals(CodeStyleScheme.PROJECT_SCHEME_NAME, myModel.getSelectedScheme().getName());
    CodeStyleSettings settings = myModel.getCloneSettings(projectScheme);
    settings.setDefaultRightMargin(66);
    CodeStyleScheme newScheme = myModel.exportProjectScheme("New Scheme");
    assertEquals(66, newScheme.getCodeStyleSettings().getDefaultRightMargin());
    assertNotSame(projectScheme.getCodeStyleSettings(), newScheme.getCodeStyleSettings());
  }

  public void testCopyToProject() {
    myModel.selectScheme(myModel.getProjectScheme(), null);
    assertTrue(myModel.isUsePerProjectSettings());
    myModel.apply();

    CodeStyleSettings defaultSettings = new CodeStyleSettings();
    CodeStyleScheme projectScheme = myModel.getProjectScheme();
    assertEquals(defaultSettings.getDefaultRightMargin(), projectScheme.getCodeStyleSettings().getDefaultRightMargin());
    CodeStyleScheme scheme = myModel.createNewScheme("New Scheme", myModel.getSelectedScheme());
    CodeStyleSettings settings = scheme.getCodeStyleSettings();
    settings.setDefaultRightMargin(66);
    myModel.copyToProject(scheme);
    CodeStyleSettings currentSettings = CodeStyleSettingsManager.getSettings(getProject());
    assertEquals(66, currentSettings.getDefaultRightMargin());
  }

  public void testDiffersFromDefault() {
    CodeStyleScheme scheme = myModel.getSelectedScheme();
    CodeStyleSettings settings = myModel.getCloneSettings(scheme);
    assertFalse(myModel.differsFromDefault(scheme));
    settings.setDefaultRightMargin(66);
    assertTrue(myModel.differsFromDefault(scheme));
    myModel.reset();
    assertFalse(myModel.differsFromDefault(scheme));
  }

  public void testVisualGuidesDifferFromDefault() {
    CodeStyleScheme scheme = myModel.getSelectedScheme();
    CodeStyleSettings settings = myModel.getCloneSettings(scheme);
    assertFalse(myModel.differsFromDefault(scheme));
    settings.setDefaultSoftMargins(Arrays.asList(100,150));
    assertTrue(myModel.differsFromDefault(scheme));
    myModel.reset();
    assertFalse(myModel.differsFromDefault(scheme));
  }

  public void testListOrder() {
    CodeStyleScheme scheme1 = myModel.createNewScheme("New Scheme", myModel.getSelectedScheme());
    CodeStyleScheme scheme2 = myModel.createNewScheme("Another Scheme", myModel.getSelectedScheme());
    myModel.addScheme(scheme1, false);
    myModel.addScheme(scheme2, false);
    List<CodeStyleScheme> schemes = myModel.getAllSortedSchemes();
    StringBuilder sb = new StringBuilder();
    for (CodeStyleScheme scheme : schemes) {
      if (sb.length() > 0) sb.append('\n');
      sb.append(scheme.getName());
    }
    assertEquals(
      "Project\n" +
      "Default\n" +
      "Another Scheme\n" +
      "New Scheme",

      sb.toString()
    );
  }

  public void testReset() {
    CodeStyleScheme newScheme = myModel.createNewScheme("New Scheme", myModel.getSelectedScheme());
    myModel.addScheme(newScheme, false);
    CodeStyleSettings newSettings = myModel.getCloneSettings(newScheme);
    assertNotNull(newSettings);
    CodeStyleSettings before = myModel.getCloneSettings(myDefaultScheme);
    before.setDefaultRightMargin(66);
    assertEquals(3, myModel.getSchemes().size());
    myModel.reset();
    CodeStyleSettings after = myModel.getCloneSettings(myDefaultScheme);
    assertSame(CodeStyleSettings.getDefaults().getDefaultRightMargin(), after.getDefaultRightMargin());
    assertSame(before, after);
    assertEquals(2, myModel.getSchemes().size());
  }
}
