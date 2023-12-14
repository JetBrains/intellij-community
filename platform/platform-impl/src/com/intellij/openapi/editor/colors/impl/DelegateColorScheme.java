// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  public DelegateColorScheme(@NotNull EditorColorsScheme delegate) {
    myDelegate = delegate;
  }

  public @NotNull EditorColorsScheme getDelegate() {
    return myDelegate;
  }

  @Override
  public final boolean isReadOnly() {
    return myDelegate.isReadOnly();
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
  public TextAttributes getAttributes(TextAttributesKey key, boolean useDefaults) {
    return myDelegate.getAttributes(key, useDefaults);
  }

  @Override
  public void setAttributes(@NotNull TextAttributesKey key, TextAttributes attributes) {
    myDelegate.setAttributes(key, attributes);
  }

  @Override
  public @NotNull Color getDefaultBackground() {
    return myDelegate.getDefaultBackground();
  }

  @Override
  public @NotNull Color getDefaultForeground() {
    return myDelegate.getDefaultForeground();
  }

  @Override
  public @Nullable Color getColor(ColorKey key) {
    return myDelegate.getColor(key);
  }

  @Override
  public void setColor(ColorKey key, @Nullable Color color) {
    myDelegate.setColor(key, color);
  }

  @Override
  public @NotNull FontPreferences getFontPreferences() {
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

  @Override
  public @NotNull Font getFont(EditorFontType key) {
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

  @Override
  public @NotNull String getName() {
    return myDelegate.getName();
  }

  @Override
  public Object clone() {
    return myDelegate.clone();
  }

  @Override
  public @NotNull FontPreferences getConsoleFontPreferences() {
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

  @Override
  public @NotNull Properties getMetaProperties()  {
    return myDelegate.getMetaProperties();
  }

  @Override
  public @NotNull String getDisplayName() {
    return myDelegate.getDisplayName();
  }
}
