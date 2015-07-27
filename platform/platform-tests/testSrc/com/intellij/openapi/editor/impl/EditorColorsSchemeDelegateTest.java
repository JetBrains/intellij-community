/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl;
import com.intellij.testFramework.TestFileType;

public class EditorColorsSchemeDelegateTest extends AbstractEditorTest {
  private EditorColorsScheme mySavedScheme;
  private EditorColorsScheme myTestScheme;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    mySavedScheme = EditorColorsManager.getInstance().getGlobalScheme();
    myTestScheme = (EditorColorsScheme)mySavedScheme.clone();
    myTestScheme.setName("EditingTest.testScheme");
    EditorColorsManager.getInstance().addColorsScheme(myTestScheme);
    EditorColorsManager.getInstance().setGlobalScheme(myTestScheme);
  }

  @Override
  protected void tearDown() throws Exception {
    EditorColorsManager.getInstance().setGlobalScheme(mySavedScheme);
    ((EditorColorsManagerImpl)EditorColorsManager.getInstance()).getSchemeManager().removeScheme(myTestScheme);
    super.tearDown();
  }
  
  public void testSecondaryFontIsAvailable() throws Exception {
    FontPreferences globalPrefs = myTestScheme.getFontPreferences();
    globalPrefs.register("DummyFont", globalPrefs.getSize(globalPrefs.getFontFamily()));
    assertEquals(2, globalPrefs.getRealFontFamilies().size());
    
    init("blah", TestFileType.TEXT);
    
    FontPreferences editorPrefs = myEditor.getColorsScheme().getFontPreferences();
    assertEquals(2, editorPrefs.getRealFontFamilies().size());
    assertEquals("DummyFont", editorPrefs.getRealFontFamilies().get(1));
  }
}
