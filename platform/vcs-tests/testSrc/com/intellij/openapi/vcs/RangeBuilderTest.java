/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.vcs.ex.Range;
import com.intellij.openapi.vcs.ex.RangesBuilder;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.diff.FilesTooBigForDiffException;

import java.util.Arrays;
import java.util.List;

/**
 * author: lesya
 */
public class RangeBuilderTest extends LightPlatformTestCase {

  public void testIdenticalContents() throws FilesTooBigForDiffException {
    String upToDateContent = "a\na\na\na\n";
    assertTrue(RangesBuilder.createRanges(EditorFactory.getInstance().createDocument(upToDateContent),
                                          EditorFactory.getInstance().createDocument(upToDateContent)).isEmpty());
  }

  public void testModified() throws FilesTooBigForDiffException {
    doTest(
      new String[]{"1", "2", "8", "3", "4"},
      new String[]{"1", "2", "9", "3", "4"},
      new Range[]{new Range(2, 3, 2, 3)}
    );


    doTest(
      new String[]{"1234", "2345", "3456"},
      new String[]{"1234", "23a45", "3456"},
      new Range[]{new Range(1, 2, 1, 2)}
    );

    doTest(
      new String[]{"1234", "2345", "3456"},
      new String[]{"12a34", "2345", "3456"},
      new Range[]{new Range(0, 1, 0, 1)}
    );

    doTest(
      new String[]{"abc"},
      new String[]{"anbnc"},
      new Range[]{new Range(0, 1, 0, 1)}
    );




  }

  public void testDeleted() throws FilesTooBigForDiffException {
    doTest(new String[]{"a", "a", "a", "b", "b", "b", "c", "c", "c"},
           new String[]{"a", "a", "a", "c", "c", "c"},
           new Range[]{new Range(3, 3, 3, 6)}
    );

    doTest(
      new String[]{"1", "2", "3"},
      new String[]{"1", "2"},
      new Range[]{new Range(2, 2, 2, 3)}
    );

    doTest(
      new String[]{"1", "2", "8", "3", "4"},
      new String[]{"1", "2",      "3", "4"},
      new Range[]{new Range(2, 2, 2, 3)}
    );


  }

  public void testInsert() throws FilesTooBigForDiffException {
    doTest(
      new String[]{"1", "3"},
      new String[]{"1", "2", "3"},
      new Range[]{new Range(1, 2, 1, 1)}
    );

    doTest(
      new String[]{     "1", "3"},
      new String[]{"2", "1", "3"},
      new Range[]{new Range(0, 1, 0, 0)}
    );
    doTest(
      new String[]{"1", "2",      "3", "4"},
      new String[]{"1", "2", "8", "3", "4"},
      new Range[]{new Range(2, 3, 2, 2)}
    );



  }


  public void testInsertAtEnd() throws FilesTooBigForDiffException {
    doTest("1",
           "1\n",
           new Range[]{new Range(1, 2, 1, 1)}
    );

    doTest(
      new String[]{"1"},
      new String[]{"1", ""},
      new Range[]{new Range(2, 3, 2, 2)}
    );

  }


  public void testDocument(){
    Document document = EditorFactory.getInstance().createDocument("1\n\n");
    int lineStartOffset = document.getLineStartOffset(document.getLineCount() - 1);
    assertEquals(3, lineStartOffset);
    document.getLineNumber(2);
  }

  private static void doTest(String[] upToDate, String[] current,
                             Range[] expected) throws FilesTooBigForDiffException {
    CharSequence upToDateContent = createContentOn(upToDate);
    CharSequence currentContent = createContentOn(current);
    doTest(upToDateContent, currentContent, expected);
  }

  private static void doTest(CharSequence upToDateContent, CharSequence currentContent, Range[] expected) throws FilesTooBigForDiffException {
    List<Range> result = RangesBuilder.createRanges(EditorFactory.getInstance().createDocument(currentContent),
                                                    EditorFactory.getInstance().createDocument(upToDateContent));
    assertEquals(Arrays.asList(expected), result);
  }

  private static String createContentOn(String[] content) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < content.length; i++) {
      result.append(content[i]);
      result.append('\n');
    }
    return result.toString();
  }

}
