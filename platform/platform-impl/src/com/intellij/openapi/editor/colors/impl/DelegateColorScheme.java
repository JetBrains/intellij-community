// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Properties;

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
  public void setAttributes(@NotNull TextAttributesKey key, TextAttributes attributes) {
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
  public float getEditorFontSize2D() {
    return myDelegate.getEditorFontSize2D();
  }

  @Override
  public void setEditorFontSize(int fontSize) {
    myDelegate.setEditorFontSize(fontSize);
  }

  @Override
  public void setEditorFontSize(float fontSize) {
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

  @NotNull
  @Override
  public Font getFont(EditorFontType key) {
    return myDelegate.getFont(key);
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
  public boolean isUseLigatures() {
    return myDelegate.isUseLigatures();
  }

  @Override
  public void setUseLigatures(boolean useLigatures) {
    myDelegate.setUseLigatures(useLigatures);
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
  public float getConsoleFontSize2D() {
    return myDelegate.getConsoleFontSize2D();
  }

  @Override
  public void setConsoleFontSize(int fontSize) {
    myDelegate.setConsoleFontSize(fontSize);
  }

  @Override
  public void setConsoleFontSize(float fontSize) {
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

  @NotNull
  @Override
  public Properties getMetaProperties()  {
    return myDelegate.getMetaProperties();
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return myDelegate.getDisplayName();
  }
}
