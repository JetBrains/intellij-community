// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.NlsSafe;

import javax.swing.*;
import java.awt.*;
import java.io.File;


public abstract class FilePathSplittingPolicy {
  public static final FilePathSplittingPolicy SPLIT_BY_LETTER = new SplitByLetterPolicy();
  public static final FilePathSplittingPolicy SPLIT_BY_SEPARATOR = new SplitBySeparatorPolicy();

  public abstract @NlsSafe String getPresentableName(File file, int length);

  public @NlsSafe String getOptimalTextForComponent(String staticPrefix, File file, JComponent component, int width){
    FontMetrics fontMetrics = component.getFontMetrics(component.getFont());
    String path = file.getPath();

    for (int i = 1; i <= path.length(); i++) {
      String text = getPresentableName(file, i);
      if (fontMetrics.stringWidth(staticPrefix + text) > width) {
        if (i == 1) {
          return text;
        }
        else {
          return getPresentableName(file, i - 1);
        }
      }
    }
    return path;
  }

  public @NlsSafe String getOptimalTextForComponent(File file, JComponent component, int width) {
    return getOptimalTextForComponent("", file, component, width);
  }

  public @NlsSafe String getOptimalTextForComponent(File file, JComponent component) {
    return getOptimalTextForComponent(file, component, component.getWidth());
  }


}
