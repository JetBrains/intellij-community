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
      for (CodeStyleScheme scheme : myModel.getSchemes()) {
        if (myModel.canDeleteScheme(scheme)) {
          myModel.removeScheme(scheme);
        }
        else if (myModel.canResetScheme(scheme)) {
          scheme.resetToDefaults();
        }
      }
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
}
