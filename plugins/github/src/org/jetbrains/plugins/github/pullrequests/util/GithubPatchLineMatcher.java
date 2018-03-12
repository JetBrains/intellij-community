/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.github.pullrequests.util;

import com.intellij.diff.util.Side;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PairProcessor;
import gnu.trove.TIntIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GithubPatchLineMatcher {
  private static final Pattern HUNK_START_PATTERN = Pattern.compile("@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*");

  @NotNull private final TIntIntHashMap myPatchToBefore;
  @NotNull private final TIntIntHashMap myPatchToAfter;
  @NotNull private final TIntIntHashMap myBeforeToPatch;
  @NotNull private final TIntIntHashMap myAfterToPatch;

  private GithubPatchLineMatcher(@NotNull TIntIntHashMap patchToBefore, @NotNull TIntIntHashMap patchToAfter,
                                 @NotNull TIntIntHashMap beforeToPatch, @NotNull TIntIntHashMap afterToPatch) {
    myPatchToBefore = patchToBefore;
    myPatchToAfter = patchToAfter;
    myBeforeToPatch = beforeToPatch;
    myAfterToPatch = afterToPatch;
  }

  public int convertToPatch(int line, @NotNull Side side, boolean approximate) {
    return convert(line, side, false, approximate);
  }

  public int convertFromPatch(int line, @NotNull Side side, boolean approximate) {
    return convert(line, side, true, approximate);
  }

  private int convert(int line, @NotNull Side side, boolean fromPatch, boolean approximate) {
    //FIXME
    TIntIntHashMap map = fromPatch ? side.select(myPatchToBefore, myPatchToAfter) : side.select(myBeforeToPatch, myAfterToPatch);
    return map.containsKey(line) ? map.get(line) : -1;
  }

  @Nullable
  public static GithubPatchLineMatcher create(@NotNull CharSequence patch) {
    TIntIntHashMap patchToBefore = new TIntIntHashMap();
    TIntIntHashMap patchToAfter = new TIntIntHashMap();
    TIntIntHashMap beforeToPatch = new TIntIntHashMap();
    TIntIntHashMap afterToPatch = new TIntIntHashMap();

    boolean success = iterateLines(patch, new PairProcessor<CharSequence, Integer>() {
      int hunkStart1 = 0;
      int hunkStart2 = 0;
      int hunkSize1 = 0;
      int hunkSize2 = 0;

      int hunkParsed1 = -1;
      int hunkParsed2 = -1;

      @Override
      public boolean process(CharSequence line, Integer lineNumber) {
        if (line.length() == 0) return true;
        char c = line.charAt(0);
        boolean insideHunk = hunkParsed1 >= 0 && hunkParsed2 >= 0;

        if (c == '@') {
          if (insideHunk) return false;
          return parseHunkHeader(line);
        }
        else {
          if (!insideHunk) return true;
          return parseHunkLines(c, lineNumber);
        }
      }

      private boolean parseHunkHeader(@NotNull CharSequence line) {
        Matcher m = HUNK_START_PATTERN.matcher(line);
        if (!m.matches()) return false;

        try {
          hunkParsed1 = 0;
          hunkParsed2 = 0;

          hunkStart1 = Integer.parseInt(m.group(1));
          hunkStart2 = Integer.parseInt(m.group(4));
          String sizeText1 = m.group(3);
          String sizeText2 = m.group(6);
          hunkSize1 = sizeText1 != null ? Integer.parseInt(sizeText1) : 1;
          hunkSize2 = sizeText2 != null ? Integer.parseInt(sizeText2) : 1;

          return true;
        }
        catch (NumberFormatException e) {
          return false;
        }
      }

      private boolean parseHunkLines(char c, int lineNumber) {
        if (c == '-' || c == ' ') {
          int beforeLine = hunkStart1 + hunkParsed1;
          beforeToPatch.put(beforeLine, lineNumber);
          patchToBefore.put(lineNumber, beforeLine);
          hunkParsed1++;
        }

        if (c == '+' || c == ' ') {
          int afterLine = hunkStart2 + hunkParsed2;
          afterToPatch.put(afterLine, lineNumber);
          patchToAfter.put(lineNumber, afterLine);
          hunkParsed2++;
        }

        if (hunkParsed1 > hunkSize1) return false;
        if (hunkParsed2 > hunkSize2) return false;

        return true;
      }
    });

    if (!success) return null;
    return new GithubPatchLineMatcher(patchToBefore, patchToAfter, beforeToPatch, afterToPatch);
  }

  private static boolean iterateLines(@NotNull CharSequence text, @NotNull PairProcessor<CharSequence, Integer> processor) {
    int textLength = text.length();

    int lineNumber = 0;
    int index = 0;
    while (index < textLength) {
      int lineEnd = StringUtil.indexOf(text, '\n');
      if (lineEnd == -1) lineEnd = textLength;

      CharSequence line = text.subSequence(index, lineEnd);
      if (!processor.process(line, lineNumber)) return false;

      index = Math.min(lineEnd + 1, textLength);
      lineNumber++;
    }

    return true;
  }
}