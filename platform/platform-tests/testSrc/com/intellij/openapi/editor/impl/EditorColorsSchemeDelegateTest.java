/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.editor.colors.ModifiableFontPreferences;
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions;
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl;
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.testFramework.TestFileType;
import org.jetbrains.annotations.NotNull;

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
    try {
      EditorColorsManager.getInstance().setGlobalScheme(mySavedScheme);
      ((EditorColorsManagerImpl)EditorColorsManager.getInstance()).getSchemeManager().removeScheme(myTestScheme);
    }
    finally {
      myTestScheme = null;
      mySavedScheme = null;
      super.tearDown();
    }
  }
  
  public void testSecondaryFontIsAvailable() throws Exception {
    FontPreferences globalPrefs = AppEditorFontOptions.getInstance().getFontPreferences();
    FontPreferences tempCopy = new FontPreferencesImpl();
    globalPrefs.copyTo(tempCopy);
    try {
      init("blah", TestFileType.TEXT);

      assertInstanceOf(globalPrefs, ModifiableFontPreferences.class);
      LOG.debug(dumpFontPreferences("globalPrefs", globalPrefs));
      ((ModifiableFontPreferences)globalPrefs).register("DummyFont", globalPrefs.getSize(globalPrefs.getFontFamily()));
      assertEquals(2, globalPrefs.getRealFontFamilies().size());
      ((EditorEx)myEditor).reinitSettings();

      FontPreferences editorPrefs = myEditor.getColorsScheme().getFontPreferences();
      LOG.debug(dumpFontPreferences("editorPrefs", editorPrefs));
      assertEquals(2, editorPrefs.getRealFontFamilies().size());
      assertEquals("DummyFont", editorPrefs.getRealFontFamilies().get(1));
    }
    finally {
      tempCopy.copyTo(globalPrefs);
    }
  }

  private static String dumpFontPreferences(@NotNull String message, @NotNull FontPreferences fontPreferences) {
    StringBuilder sb = new StringBuilder();
    sb.append(message).append(": ");
    sb.append("Real font families: ");
    boolean isFirst = true;
    for (String fontFamily : fontPreferences.getRealFontFamilies()) {
      if (isFirst) isFirst = false; else sb.append(", ");
      sb.append(fontFamily);
    }
    return sb.toString();
  }

}
