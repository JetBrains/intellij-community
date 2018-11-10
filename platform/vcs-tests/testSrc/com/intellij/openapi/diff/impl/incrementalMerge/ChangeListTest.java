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
package com.intellij.openapi.diff.impl.incrementalMerge;

import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.splitter.Interval;
import com.intellij.openapi.diff.impl.util.TextDiffType;
import com.intellij.openapi.diff.impl.util.TextDiffTypeEnum;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.Assertion;
import com.intellij.util.diff.FilesTooBigForDiffException;

public class ChangeListTest extends PlatformTestCase {
  private MergeTestUtils myUtils;
  private final Assertion CHECK = new Assertion();

  public void testInsMarkup() throws FilesTooBigForDiffException {
    Document base = MergeTestUtils.createDocument("a\nb\nc\nd");
    Document version = MergeTestUtils.createRODocument("a\nIns\nb\nd");
    ChangeList changeList = buildChangeList(base, version);
    Editor eBase = myUtils.createEditor(base);
    Editor eVersion = myUtils.createEditor(version);
    assertEquals(2, changeList.getCount());
    changeList.setMarkup(eBase, eVersion);

    MergeTestUtils.checkMarkup(eVersion, new MergeTestUtils.Range[]{MergeTestUtils.ins(2, 4), MergeTestUtils.del(8, 0)});
    MergeTestUtils.checkMarkup(eBase, new MergeTestUtils.Range[]{MergeTestUtils.ins(2, 0), MergeTestUtils.del(4, 2)});
    CHECK.compareAll(new TextDiffTypeEnum[]{TextDiffTypeEnum.INSERT, TextDiffTypeEnum.DELETED},
                     convertTypesToEnums(changeList.getLineBlocks().getTypes()));
    CHECK.compareAll(new Interval[]{new Interval(1, 0), new Interval(2, 1)},
                     changeList.getLineBlocks().getIntervals(FragmentSide.SIDE1));
    CHECK.compareAll(new Interval[]{new Interval(1, 1), new Interval(3, 0)},
                     changeList.getLineBlocks().getIntervals(FragmentSide.SIDE2));
  }

  private ChangeList buildChangeList(Document base, Document version) throws FilesTooBigForDiffException {
    return ChangeList.build(base, version, getProject());
  }

  public void testInsAtEnd() throws FilesTooBigForDiffException {
    Document base = MergeTestUtils.createDocument("a\n");
    Document version = MergeTestUtils.createRODocument("a\nIns");
    ChangeList changeList = buildChangeList(base, version);
    Editor eBase = myUtils.createEditor(base);
    Editor eVersion = myUtils.createEditor(version);
    changeList.setMarkup(eBase, eVersion);
    MergeTestUtils.checkMarkup(eVersion, new MergeTestUtils.Range[]{MergeTestUtils.ins(2, 3)});
    MergeTestUtils.checkMarkup(eBase, new MergeTestUtils.Range[]{MergeTestUtils.ins(2, 0)});
    CHECK.compareAll(new TextDiffTypeEnum[]{TextDiffTypeEnum.INSERT}, convertTypesToEnums(changeList.getLineBlocks().getTypes()));
    CHECK.compareAll(new Interval[]{new Interval(1, 0)},
                     changeList.getLineBlocks().getIntervals(FragmentSide.SIDE1));
    CHECK.compareAll(new Interval[]{new Interval(1, 1)},
                     changeList.getLineBlocks().getIntervals(FragmentSide.SIDE2));


    version = MergeTestUtils.createRODocument("Ins\na");
    base = MergeTestUtils.createDocument("a");
    changeList = buildChangeList(base, version);
    eBase = myUtils.createEditor(base);
    eVersion = myUtils.createEditor(version);
    changeList.setMarkup(eBase, eVersion);
    MergeTestUtils.checkMarkup(eVersion, new MergeTestUtils.Range[]{MergeTestUtils.ins(0, 4)});
    MergeTestUtils.checkMarkup(eBase, new MergeTestUtils.Range[]{MergeTestUtils.ins(0, 0)});
    CHECK.compareAll(new TextDiffTypeEnum[]{TextDiffTypeEnum.INSERT}, convertTypesToEnums(changeList.getLineBlocks().getTypes()));
    CHECK.compareAll(new Interval[]{new Interval(0, 0)},
                     changeList.getLineBlocks().getIntervals(FragmentSide.SIDE1));
    CHECK.compareAll(new Interval[]{new Interval(0, 1)},
                     changeList.getLineBlocks().getIntervals(FragmentSide.SIDE2));
  }

  private static TextDiffTypeEnum[] convertTypesToEnums(TextDiffType[] types) {
    TextDiffTypeEnum[] result = new TextDiffTypeEnum[types.length];
    for (int i = 0; i < types.length; i++) {
      result[i] = types[i].getType();
    }
    return result;
  }

  public void testEditChange() throws FilesTooBigForDiffException {
    Document base = MergeTestUtils.createDocument("a\nxx\nb\nyyy");
    Document version = MergeTestUtils.createRODocument("a\n1\n3\nb\nYYY");
    ChangeList changeList = buildChangeList(base, version);
    Editor eBase = myUtils.createEditor(base);
    Editor eVersion = myUtils.createEditor(version);
    changeList.setMarkup(eBase, eVersion);
    myUtils.insertString(base, 3, "\n");
    MergeTestUtils.checkMarkup(eBase, new MergeTestUtils.Range[]{MergeTestUtils.mod(2, 4), MergeTestUtils.mod(8, 3)});
    MergeTestUtils.checkMarkup(eVersion, new MergeTestUtils.Range[]{MergeTestUtils.mod(2, 4), MergeTestUtils.mod(8, 3)});
    Interval[] expected = {new Interval(1, 2), new Interval(4, 1)};
    CHECK.compareAll(new TextDiffTypeEnum[]{TextDiffTypeEnum.CHANGED, TextDiffTypeEnum.CHANGED},
                     convertTypesToEnums(changeList.getLineBlocks().getTypes()));
    CHECK.compareAll(expected, changeList.getLineBlocks().getIntervals(FragmentSide.SIDE1));
    CHECK.compareAll(expected, changeList.getLineBlocks().getIntervals(FragmentSide.SIDE2));
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myUtils = new MergeTestUtils(myProject);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myUtils.tearDown();
    }
    finally {
      myUtils = null;
      super.tearDown();
    }
  }
}
