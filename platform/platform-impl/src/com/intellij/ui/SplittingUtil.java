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
package com.intellij.ui;

import com.intellij.util.ArrayUtil;

import java.awt.*;
import java.util.ArrayList;

public class SplittingUtil {
  public static String[] splitText(String text, FontMetrics fontMetrics, int widthLimit, char separator){
    ArrayList<String> lines = new ArrayList<String>();
    String currentLine = "";
    StringBuffer currentAtom = new StringBuffer();

    for (int i=0; i < text.length(); i++) {
      char ch = text.charAt(i);
      currentAtom.append(ch);

      if (ch == separator) {
        currentLine += currentAtom.toString();
        currentAtom.setLength(0);
      }

      String s = currentLine + currentAtom.toString();
      int width = fontMetrics.stringWidth(s);

      if (width >= widthLimit - fontMetrics.charWidth('w')) {
        if (currentLine.length() > 0) {
          lines.add(currentLine);
          currentLine = "";
        }
        else {
          lines.add(currentAtom.toString());
          currentAtom.setLength(0);
        }
      }
    }

    String s = currentLine + currentAtom.toString();
    if (s.length() > 0) {
      lines.add(s);
    }

    return ArrayUtil.toStringArray(lines);
  }
}
