// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;


import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.util.Key;
import com.intellij.util.ui.JBHtmlEditorKit;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.text.AttributeSet;
import javax.swing.text.html.CSS;
import java.awt.*;

/**
 * This class allows to use editor font in JEditorPane, by configuring its usage via CSS.
 * Instance of this class should be set as font resolver ({@link JBHtmlEditorKit#setFontResolver(JBHtmlEditorKit.FontResolver)},
 * and {@link #EDITOR_FONT_NAME_PLACEHOLDER} should be specified as {@code font-family} in CSS.
 * Font style and size are configured as usual.
 */
public class EditorCssFontResolver implements JBHtmlEditorKit.FontResolver {
  /**
   * Can be used as a {@code font-family} value in CSS to represent editor font.
   */
  public static final String EDITOR_FONT_NAME_PLACEHOLDER = "_EditorFont_";

  private static final EditorCssFontResolver GLOBAL_INSTANCE = new EditorCssFontResolver(null);
  private static final Key<EditorCssFontResolver> LOCAL_KEY = Key.create("EditorCssFontResolver");

  private final Editor myEditor;

  private EditorCssFontResolver(Editor editor) {
    myEditor = editor;
  }

  /**
   * Returns an instance that will resolve {@link #EDITOR_FONT_NAME_PLACEHOLDER} to editor font from current global settings.
   */
  public static @NotNull EditorCssFontResolver getGlobalInstance() {
    return GLOBAL_INSTANCE;
  }

  /**
   * Returns an instance that will resolve {@link #EDITOR_FONT_NAME_PLACEHOLDER} to editor font set in specific editor.
   */
  public static @NotNull EditorCssFontResolver getInstance(@NotNull Editor editor) {
    EditorCssFontResolver result = editor.getUserData(LOCAL_KEY);
    if (result == null) {
      // it's fine to have multiple instances of resolver per editor, so we don't care about races here
      editor.putUserData(LOCAL_KEY, result = new EditorCssFontResolver(editor));
    }
    return result;
  }

  @Override
  public @NotNull Font getFont(@NotNull Font defaultFont, @NotNull AttributeSet attributeSet) {
    Object fontFamily = attributeSet.getAttribute(CSS.Attribute.FONT_FAMILY);
    if (fontFamily == null || !EDITOR_FONT_NAME_PLACEHOLDER.equals(fontFamily.toString())) {
      return defaultFont;
    }
    EditorColorsScheme scheme = myEditor == null ? EditorColorsManager.getInstance().getGlobalScheme() : myEditor.getColorsScheme();
    Font font = scheme.getFont(EditorFontType.forJavaStyle(defaultFont.getStyle())).deriveFont(defaultFont.getSize2D());
    return UIUtil.getFontWithFallback(font);
  }
}
