/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * User: spLeaner
 */
public abstract class DelegateColorScheme implements EditorColorsScheme {

  private EditorColorsScheme myDelegate;

  public DelegateColorScheme(@NotNull final EditorColorsScheme delegate) {
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

  @Override
  public Color getDefaultBackground() {
    return myDelegate.getDefaultBackground();
  }

  @Override
  public Color getDefaultForeground() {
    return myDelegate.getDefaultForeground();
  }

  @Override
  public Color getColor(ColorKey key) {
    return myDelegate.getColor(key);
  }

  @Override
  public void setColor(ColorKey key, Color color) {
    myDelegate.setColor(key, color);
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
  public void readExternal(Element element) throws InvalidDataException {
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
  }

  @Override
  public String getName() {
    return myDelegate.getName();
  }

  public Object clone() {
    return myDelegate.clone();
  }
}
