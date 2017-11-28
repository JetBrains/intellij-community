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
package com.intellij.openapi.diff.impl.splitter;

import com.intellij.openapi.diff.impl.EditingSides;
import com.intellij.openapi.diff.impl.fragments.LineBlock;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.util.TextDiffTypeEnum;
import com.intellij.openapi.editor.Editor;

import java.util.ArrayList;

public class FoldedChangesTest extends FoldingTestCase {
  private Editor myEditor1;
  private Editor myEditor2;
  private LineBlocks DELETED;
  private static final int DIVIDER_POLYGON_OFFSET = 3;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    DELETED = LineBlocks.createLineBlocks(new LineBlock[]{new LineBlock(2, 2, 2, 0, TextDiffTypeEnum.DELETED)});

    myEditor1 = createEditor();
    myEditor2 = createEditor();
    myEditor1.getComponent().setSize(100, 500);
    myEditor2.getComponent().setSize(100, 500);
  }

  @Override
  protected void tearDown() throws Exception {
    myEditor1 = null;
    myEditor2 = null;
    DELETED = null;
    super.tearDown();
  }

  public void testInsertionToStart() {
    addFolding(myEditor2, 2, 5);
    checkPoligon(2, 4, 2, 2);
  }

  private void checkPoligon(int start1, double end1, int start2, double end2) {
    ArrayList<DividerPolygon> poligons = DividerPolygon.createVisiblePolygons(new MyEditingSides(FragmentSide.SIDE1),
                                                                              FragmentSide.SIDE1, DIVIDER_POLYGON_OFFSET);
    assertEquals(1, poligons.size());
    check(poligons.get(0), start1, end1, start2, end2);
    poligons = DividerPolygon.createVisiblePolygons(new MyEditingSides(FragmentSide.SIDE2), FragmentSide.SIDE2, DIVIDER_POLYGON_OFFSET);
    assertEquals(1, poligons.size());
    check(poligons.get(0), start2, end2, start1, end1);
  }

  public void testWholeInsertionToStart() {
    addFolding(myEditor2, 2, 4);
    checkPoligon(2, 4, 2, 2);
  }

  public void testInsertionFromStartToStart() {
    addFolding(myEditor1, 2, 6);
    addFolding(myEditor2, 2, 6);
    checkPoligon(2, 2.5, 2, 2);
  }

  private void check(DividerPolygon poligon, int start1, double end1, int start2, double end2) {
    int lineHeight = myEditor1.getLineHeight();
    assertEquals(new DividerPolygon(start1 * lineHeight - DIVIDER_POLYGON_OFFSET,
                                    start2 * lineHeight - DIVIDER_POLYGON_OFFSET,
                                    (int)(end1 * myEditor1.getLineHeight()) - DIVIDER_POLYGON_OFFSET,
                                    (int)(end2 * lineHeight) - DIVIDER_POLYGON_OFFSET,
                                    poligon.getColor(), false), poligon);
  }

  private class MyEditingSides implements EditingSides {
    private final FragmentSide myLeft;

    public MyEditingSides(FragmentSide left) {
      myLeft = left;
    }

    @Override
    public Editor getEditor(FragmentSide side) {
      if (myLeft == side) return myEditor1;
      if (myLeft.otherSide() == side) return myEditor2;
      throw side.invalidException();
    }

    @Override
    public LineBlocks getLineBlocks() {
      return DELETED;
    }
  }
}
