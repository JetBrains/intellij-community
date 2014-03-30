/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.processing;

import com.intellij.openapi.diff.impl.fragments.LineFragment;
import com.intellij.openapi.diff.impl.util.TextDiffTypeEnum;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;

import java.util.ArrayList;

public class DummyDiffFragmentsProcessor {
  public ArrayList<LineFragment> process(String text1, String text2) {
    ArrayList<LineFragment> lineFragments = new ArrayList<LineFragment>();

    if (text1.isEmpty() && text2.isEmpty()) {
      return lineFragments;
    }

    TextDiffTypeEnum type;
    if (text1.isEmpty()) {
      type = TextDiffTypeEnum.INSERT;
    }
    else if (text2.isEmpty()) {
      type = TextDiffTypeEnum.DELETED;
    }
    else {
      type = TextDiffTypeEnum.CHANGED;
    }
    lineFragments.add(new LineFragment(0, countLines(text1), 0, countLines(text2), type, new TextRange(0, text1.length()),
                                       new TextRange(0, text2.length())));

    return lineFragments;
  }

  private static int countLines(String text) {
    if (text == null || text.isEmpty()) return 0;
    int count = StringUtil.countNewLines(text);
    if (text.charAt(text.length() - 1) != '\n') count++;
    return count;
  }

}
