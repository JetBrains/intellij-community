/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util.ui;

import javax.swing.*;
import java.awt.*;
import java.io.File;


public abstract class FilePathSplittingPolicy {
  public static final FilePathSplittingPolicy SPLIT_BY_LETTER = new SplitByLetterPolicy();
  public static final FilePathSplittingPolicy SPLIT_BY_SEPARATOR = new SplitBySeparatorPolicy();

  public abstract String getPresentableName(File file, int length);

  public String getOptimalTextForComponent(String staticPrefix, File file, JComponent component, int width){
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

  public String getOptimalTextForComponent(File file, JComponent component, int width) {
    return getOptimalTextForComponent("", file, component, width);
  }

  public String getOptimalTextForComponent(File file, JComponent component) {
    return getOptimalTextForComponent(file, component, component.getWidth());
  }


}
