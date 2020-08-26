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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.colors.ModifiableFontPreferences;
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions;
import com.intellij.openapi.editor.colors.impl.DelegateColorScheme;
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl;
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.testFramework.TestFileType;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

import static com.intellij.openapi.editor.colors.FontPreferencesTest.getExistingNonDefaultFontName;

public class EditorColorsSchemeDelegateTest extends AbstractEditorTest {
  private EditorColorsScheme mySavedScheme;
  private EditorColorsScheme myTestScheme;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    mySavedScheme = EditorColorsManager.getInstance().getGlobalScheme();
    myTestScheme = (EditorColorsScheme)mySavedScheme.clone();
    myTestScheme.setUseAppFontPreferencesInEditor();
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
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myTestScheme = null;
      mySavedScheme = null;
      super.tearDown();
    }
  }
  
  public void testSecondaryFontIsAvailable() {
    String secondaryFont = getExistingNonDefaultFontName();
    FontPreferences globalPrefs = AppEditorFontOptions.getInstance().getFontPreferences();
    FontPreferences tempCopy = new FontPreferencesImpl();
    globalPrefs.copyTo(tempCopy);
    try {
      init("blah", TestFileType.TEXT);

      assertInstanceOf(globalPrefs, ModifiableFontPreferences.class);
      ((ModifiableFontPreferences)globalPrefs).register(secondaryFont, globalPrefs.getSize(globalPrefs.getFontFamily()));
      LOG.debug(dumpFontPreferences("globalPrefs", globalPrefs));
      assertEquals(2, globalPrefs.getRealFontFamilies().size());
      ((EditorEx)getEditor()).reinitSettings();

      EditorColorsScheme editorScheme = getEditor().getColorsScheme();
      assertInstanceOf(editorScheme, DelegateColorScheme.class);
      EditorColorsScheme delegate = ((DelegateColorScheme)editorScheme).getDelegate();
      assertTrue(delegate.isUseAppFontPreferencesInEditor());
      FontPreferences delegatePrefs = delegate.getFontPreferences();
      assertEquals(globalPrefs.getRealFontFamilies(), delegatePrefs.getRealFontFamilies());

      FontPreferences editorPrefs = editorScheme.getFontPreferences();
      LOG.debug(dumpFontPreferences("editorPrefs", editorPrefs));
      assertEquals(2, editorPrefs.getRealFontFamilies().size());
      assertEquals(secondaryFont, editorPrefs.getRealFontFamilies().get(1));
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

  public void testSettingLineSpacingAffectsOnlyCurrentEditor() {
    Document document = EditorFactory.getInstance().createDocument("");
    doInEditor(document, e1 -> doInEditor(document, e2 -> {
      float initialSpacing = e1.getColorsScheme().getLineSpacing();
      assertEquals(initialSpacing, e2.getColorsScheme().getLineSpacing());
      e1.getColorsScheme().setLineSpacing(initialSpacing + 0.1f);
      assertFalse(initialSpacing == e1.getColorsScheme().getLineSpacing());
      assertEquals(initialSpacing, e2.getColorsScheme().getLineSpacing());
    }));
  }

  private static void doInEditor(@NotNull Document document, Consumer<? super Editor> task) {
    EditorFactory factory = EditorFactory.getInstance();
    Editor editor = factory.createEditor(document);
    try {
      task.accept(editor);
    }
    finally {
      factory.releaseEditor(editor);
    }
  }
}
