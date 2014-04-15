/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.util.text;

import com.intellij.util.text.CharArrayUtil;
import gnu.trove.TIntHashSet;

public class TrigramBuilder {
  private TrigramBuilder() {
  }

  public static TIntHashSet buildTrigram(CharSequence text) {
    TIntHashSet result = new TIntHashSet();

    int index = 0;
    final char[] fileTextArray = CharArrayUtil.fromSequenceWithoutCopying(text);

    ScanWordsLoop:
    while (true) {
      while (true) {
        if (index == text.length()) break ScanWordsLoop;
        final char c = fileTextArray != null ? fileTextArray[index]:text.charAt(index);
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') ||
            Character.isJavaIdentifierPart(c)) {
          break;
        }
        index++;
      }
      int index1 = index;
      while (true) {
        index++;
        if (index == text.length()) break;
        final char c = fileTextArray != null ? fileTextArray[index]:text.charAt(index);
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) continue;
        if (!Character.isUnicodeIdentifierPart(c)) break;
      }
      if (index - index1 > 100) continue; // Strange limit but we should have some!

      int tc1 = 0;
      int tc2 = 0;
      int tc3;
      for (int i = index1, iters = 0; i < index; ++i, ++iters) {
        char c = StringUtil.toLowerCase(fileTextArray != null ? fileTextArray[i]:text.charAt(i));
        tc3 = (tc2 << 8) + c;
        tc2 = (tc1 << 8) + c;
        tc1 = c;

        if (iters >= 2) {
          result.add(tc3);
        }
      }
    }

    return result;
  }
}

