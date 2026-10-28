// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.diagnostic.Logger;
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
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static com.intellij.openapi.editor.colors.FontPreferencesTest.getExistingNonDefaultFontName;
import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class EditorColorsSchemeDelegateTest {
  private static final Logger LOG = Logger.getInstance(EditorColorsSchemeDelegateTest.class);

  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  private EditorModelFixture myFixture;

  private EditorColorsScheme mySavedScheme;
  private EditorColorsScheme myTestScheme;

  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      mySavedScheme = EditorColorsManager.getInstance().getGlobalScheme();
      myTestScheme = (EditorColorsScheme)mySavedScheme.clone();
      myTestScheme.setUseAppFontPreferencesInEditor();
      myTestScheme.setName("EditingTest.testScheme");
      EditorColorsManager.getInstance().addColorScheme(myTestScheme);
      EditorColorsManager.getInstance().setGlobalScheme(myTestScheme);
    });
  }

  @AfterEach
  void tearDown() {
    EdtTestUtil.runInEdtAndWait(() -> {
      EditorColorsManager.getInstance().setGlobalScheme(mySavedScheme);
      ((EditorColorsManagerImpl)EditorColorsManager.getInstance()).getSchemeManager().removeScheme(myTestScheme);
    });
  }
  
  @Test
  public void testSecondaryFontIsAvailable() {
    myFixture.runTest(() -> {
      String secondaryFont = getExistingNonDefaultFontName();
      FontPreferences globalPrefs = AppEditorFontOptions.getInstance().getFontPreferences();
      FontPreferences tempCopy = new FontPreferencesImpl();
      globalPrefs.copyTo(tempCopy);
      try {
        myFixture.init("blah", PlainTextFileType.INSTANCE);

        assertInstanceOf(ModifiableFontPreferences.class, globalPrefs);
        ((ModifiableFontPreferences)globalPrefs).register(secondaryFont, globalPrefs.getSize(globalPrefs.getFontFamily()));
        LOG.debug(dumpFontPreferences("globalPrefs", globalPrefs));
        assertEquals(2, globalPrefs.getRealFontFamilies().size());
        ((EditorEx)myFixture.getEditor()).reinitSettings();

        EditorColorsScheme editorScheme = myFixture.getEditor().getColorsScheme();
        assertInstanceOf(DelegateColorScheme.class, editorScheme);
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
    });
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

  @Test
  public void testSettingLineSpacingAffectsOnlyCurrentEditor() {
    myFixture.runTest(() -> {
      Document document = EditorFactory.getInstance().createDocument("");
      doInEditor(document, e1 -> doInEditor(document, e2 -> {
        float initialSpacing = e1.getColorsScheme().getLineSpacing();
        assertEquals(initialSpacing, e2.getColorsScheme().getLineSpacing());
        e1.getColorsScheme().setLineSpacing(initialSpacing + 0.1f);
        assertNotEquals(initialSpacing, e1.getColorsScheme().getLineSpacing());
        assertEquals(initialSpacing, e2.getColorsScheme().getLineSpacing());
      }));
    });
  }

  @Test
  public void testFontPreferencesThatDifferOnlyInEffectiveFamiliesReachEditor() {
    myFixture.runTest(() -> {
      Document document = EditorFactory.getInstance().createDocument("");
      doInEditor(document, editor -> {
        String fontFamily = getExistingNonDefaultFontName();
        var first = new FontPreferencesImpl();
        first.register(fontFamily, 14);
        editor.getColorsScheme().setFontPreferences(first);

        var second = new FontPreferencesImpl();
        first.copyTo(second);
        var effectiveFontFamilies = List.of(fontFamily, FontPreferences.DEFAULT_FONT_NAME);
        second.setEffectiveFontFamilies(effectiveFontFamilies);
        editor.getColorsScheme().setFontPreferences(second);

        assertEquals(effectiveFontFamilies, editor.getColorsScheme().getFontPreferences().getEffectiveFontFamilies());
      });
    });
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
