// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * author: lesya
 */
public final class SplitBySeparatorPolicy extends FilePathSplittingPolicy {
  @Override
  public String getPresentableName(File file, int length) {
    String absolutePath = file.getPath();
    if (absolutePath.length() <= length) return absolutePath;
    String name = file.getName();
    if (length < name.length()) return "...";
    if (length == name.length()) return name;

    List<String> components = getComponents(file);

    int currentLength = 0;

    List<String> end = new ArrayList<>();
    List<String> begin = new ArrayList<>();

    int size = components.size();
    int mult = 1;
    int currentIndex = 0;
    for (int i = size - 1; i >= 0; i--) {
      String s = components.get(currentIndex);
      currentLength += s.length();
      if (currentLength > (length - 3)) break;
      if (mult > 0) {
        end.add(s);
      }
      else {
        begin.add(s);
      }
      currentIndex += i * mult;
      mult *= -1;
    }


    if (end.isEmpty()) {
      return name;
    }

    StringBuilder result = new StringBuilder();

    for (String line : begin) {
      result.append(line);
    }

    result.append("...");

    for (int i = end.size() - 1; i >=0; i--) {
      result.append(end.get(i));
    }

    return result.toString();
  }

  private static ArrayList<String> getComponents(File file) {
    ArrayList<String> result = new ArrayList<>();
    File current = file;
    while (current != null) {
      result.add(getFileName(current));
      current = current.getParentFile();
      if (current != null) result.add(File.separator);
    }
    return result;
  }

  private static String getFileName(File current) {
    String result = current.getName();
    if (!result.isEmpty()) return result;
    String path = current.getPath();
    return path.substring(0, path.length() - 1);
  }
}
