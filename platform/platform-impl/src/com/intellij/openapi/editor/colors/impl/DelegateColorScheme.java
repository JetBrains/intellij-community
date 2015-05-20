/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.editor.colors.impl;

import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.FontSize;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * User: spLeaner
 */
public abstract class DelegateColorScheme implements EditorColorsScheme {

  private EditorColorsScheme myDelegate;

  public DelegateColorScheme(@NotNull final EditorColorsScheme delegate) {
    myDelegate = delegate;
  }

  public EditorColorsScheme getDelegate() {
    return myDelegate;
  }

  public void setDelegate(@NotNull EditorColorsScheme delegate) {
    myDelegate = delegate;
  }

  @Override
  public void setName(String name) {
    myDelegate.setName(name);
  }

  @Override
  public TextAttributes getAttributes(TextAttributesKey key) {
    return myDelegate.getAttributes(key);
  }

  @Override
  public void setAttributes(TextAttributesKey key, TextAttributes attributes) {
    myDelegate.setAttributes(key, attributes);
  }

  @NotNull
  @Override
  public Color getDefaultBackground() {
    return myDelegate.getDefaultBackground();
  }

  @NotNull
  @Override
  public Color getDefaultForeground() {
    return myDelegate.getDefaultForeground();
  }

  @Nullable
  @Override
  public Color getColor(ColorKey key) {
    return myDelegate.getColor(key);
  }

  @Override
  public void setColor(ColorKey key, @Nullable Color color) {
    myDelegate.setColor(key, color);
  }

  @NotNull
  @Override
  public FontPreferences getFontPreferences() {
    return myDelegate.getFontPreferences();
  }

  @Override
  public void setFontPreferences(@NotNull FontPreferences preferences) {
    myDelegate.setFontPreferences(preferences);
  }

  @Override
  public int getEditorFontSize() {
    return myDelegate.getEditorFontSize();
  }

  @Override
  public void setEditorFontSize(int fontSize) {
    myDelegate.setEditorFontSize(fontSize);
  }

  @Override
  public FontSize getQuickDocFontSize() {
    return myDelegate.getQuickDocFontSize();
  }

  @Override
  public void setQuickDocFontSize(@NotNull FontSize fontSize) {
    myDelegate.setQuickDocFontSize(fontSize);
  }

  @Override
  public String getEditorFontName() {
    return myDelegate.getEditorFontName();
  }

  @Override
  public void setEditorFontName(String fontName) {
    myDelegate.setEditorFontName(fontName);
  }

  @Override
  public Font getFont(EditorFontType key) {
    return myDelegate.getFont(key);
  }

  @Override
  public void setFont(EditorFontType key, Font font) {
    myDelegate.setFont(key, font);
  }

  @Override
  public float getLineSpacing() {
    return myDelegate.getLineSpacing();
  }

  @Override
  public void setLineSpacing(float lineSpacing) {
    myDelegate.setLineSpacing(lineSpacing);
  }

  @Override
  public void readExternal(Element element) {
  }

  @NotNull
  @Override
  public String getName() {
    return myDelegate.getName();
  }

  @Override
  public Object clone() {
    return myDelegate.clone();
  }

  @NotNull
  @Override
  public FontPreferences getConsoleFontPreferences() {
    return myDelegate.getConsoleFontPreferences();
  }

  @Override
  public void setConsoleFontPreferences(@NotNull FontPreferences preferences) {
    myDelegate.setConsoleFontPreferences(preferences);
  }

  @Override
  public String getConsoleFontName() {
    return myDelegate.getConsoleFontName();
  }

  @Override
  public void setConsoleFontName(String fontName) {
    myDelegate.setConsoleFontName(fontName);
  }

  @Override
  public int getConsoleFontSize() {
    return myDelegate.getConsoleFontSize();
  }

  @Override
  public void setConsoleFontSize(int fontSize) {
    myDelegate.setConsoleFontSize(fontSize);
  }

  @Override
  public float getConsoleLineSpacing() {
    return myDelegate.getConsoleLineSpacing();
  }

  @Override
  public void setConsoleLineSpacing(float lineSpacing) {
    myDelegate.setConsoleLineSpacing(lineSpacing);
  }
}
