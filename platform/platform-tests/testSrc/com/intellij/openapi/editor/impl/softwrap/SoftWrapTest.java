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
package com.intellij.openapi.editor.impl.softwrap;

import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.SoftWrap;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.TestFileType;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SoftWrapTest extends AbstractEditorTest {

  public void testCollapsedRegionWithLongPlaceholderAtLineStart1() {
    doTestSoftWraps(10, "<fold text='veryVeryVeryLongPlaceholder'>foo</fold>");
  }

  public void testCollapsedRegionWithLongPlaceholderAtLineStart2() {
    doTestSoftWraps(10, "<fold text='veryVeryVeryLongPlaceholder'>foo</fold><wrap>bar");
  }

  public void testCollapsedRegionWithLongPlaceholderAtLineStart3() {
    doTestSoftWraps(10, "<fold text='veryVeryVeryLongPlaceholder'>foo</fold>\nvery long <wrap>text");
  }

  private static final String TAGS_PATTERN = "(<fold(\\stext=\'([^\']*)\')?>)|(</fold>)|<wrap>";

  private void doTestSoftWraps(int wrapWidth, String text) {
    List<MyFoldRegion> foldRegions = new ArrayList<>();
    List<Integer> wrapPositions = new ArrayList<>();
    int foldInsertPosition = 0;
    int pos = 0;
    int docPos = 0;
    Matcher matcher = Pattern.compile(TAGS_PATTERN).matcher(text);
    StringBuilder cleanedText = new StringBuilder();
    while(matcher.find()) {
      cleanedText.append(text.substring(pos, matcher.start()));
      docPos += matcher.start() - pos;
      pos = matcher.end();
      if (matcher.group(1) != null) {       // <fold>
        foldRegions.add(foldInsertPosition++, new MyFoldRegion(docPos, matcher.group(3), matcher.group(2) != null));
      }
      else if (matcher.group(4) != null) {  // </fold>
        assertTrue("Misplaced closing fold marker tag: " + text, foldInsertPosition > 0);
        foldRegions.get(--foldInsertPosition).endPos = docPos;
      }
      else {                                // <wrap>
        wrapPositions.add(docPos);
      }
    }
    assertTrue("Missing closing fold marker tag: " + text, foldInsertPosition == 0);
    cleanedText.append(text.substring(pos));

    init(cleanedText.toString(), TestFileType.TEXT);

    for (MyFoldRegion region : foldRegions) {
      FoldRegion r = addFoldRegion(region.startPos, region.endPos, region.placeholder);
      if (region.collapse) {
        toggleFoldRegionState(r, false);
      }
    }

    EditorTestUtil.configureSoftWraps(myEditor, wrapWidth);

    List<Integer> actualWrapPositions = new ArrayList<>();
    for (SoftWrap wrap : myEditor.getSoftWrapModel().getSoftWrapsForRange(0, myEditor.getDocument().getTextLength())) {
      actualWrapPositions.add(wrap.getStart());
    }
    assertEquals("Wrong wrap positions", wrapPositions, actualWrapPositions);
  }

  private static class MyFoldRegion {
    private final int startPos;
    private int endPos;
    private final String placeholder;
    private final boolean collapse;

    private MyFoldRegion(int pos, String placeholder, boolean collapse) {
      this.startPos = pos;
      this.placeholder = placeholder;
      this.collapse = collapse;
    }
  }
}
