package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.vcs.FileStatus;

import java.awt.*;

/**
 * author: lesya
 */
public class FileStatusImpl implements FileStatus {
  private final String myStatus;
  private final ColorKey myColorKey;
  private final String myText;

  public FileStatusImpl(String status, ColorKey key, String text) {
    myStatus = status;
    myColorKey = key;
    myText = text;
  }

  public String toString() {
    return myStatus;
  }

  public String getText() {
    return myText;
  }

  public Color getColor() {
    return EditorColorsManager.getInstance().getGlobalScheme().getColor(getColorKey());
  }

  public ColorKey getColorKey() {
    return myColorKey;
  }
}
