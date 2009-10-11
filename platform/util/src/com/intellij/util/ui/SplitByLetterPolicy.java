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

import java.io.File;


public class SplitByLetterPolicy extends FilePathSplittingPolicy{

  protected SplitByLetterPolicy() {
  }

  public String getPresentableName(File file, int count) {
    String filePath = file.getPath();
    if (count >= filePath.length()) return filePath;
    int nameLength = file.getName().length();
    if (count <= nameLength) return filePath.substring(filePath.length() - count);
    int dotsCount = Math.min(3, count - nameLength);
    int shownCount = count - dotsCount;
    int leftCount = (shownCount - nameLength) / 2 + (shownCount - nameLength) % 2;
    int rightCount = shownCount - leftCount;
    return filePath.substring(0, leftCount) + dots(dotsCount) + filePath.substring(filePath.length() - rightCount);
  }

  private static String dots(int count) {
    switch (count) {
      case 1:
        return ".";
      case 2:
        return "..";
      default:
        return "...";
    }
  }



}
