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
package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

import java.util.*;

import static com.intellij.openapi.editor.impl.DisplayedFoldingAnchor.Type;

public class FoldingAnchorsOverlayStrategyTest extends LightPlatformCodeInsightFixtureTestCase {
  public void testExpanded() {
    prepareEditor("<body><div>\n" +
                  "</div><div>\n" +
                  "some text\n" +
                  "some text\n" +
                  "some text\n" +
                  "</div><div>\n" +
                  "</div></body>");
    verifyAnchors(null,
                  0, Type.EXPANDED_TOP,
                  1, Type.EXPANDED_BOTTOM,
                  5, Type.EXPANDED_TOP,
                  6, Type.EXPANDED_BOTTOM);
  }

  public void testCollapsed() {
    prepareEditor("<body><div>\n" +
                  "</div><div>\n" +
                  "some text\n" +
                  "some text\n" +
                  "some text\n" +
                  "</div><div>\n" +
                  "</div></body>");
    collapseFoldingRegion(2);
    verifyAnchors(null,
                  0, Type.EXPANDED_TOP,
                  1, Type.COLLAPSED,
                  2, Type.EXPANDED_BOTTOM);
  }

  public void testWithActiveRegion() {
    prepareEditor("<body><div>\n" +
                  "</div><div>\n" +
                  "some text\n" +
                  "some text\n" +
                  "some text\n" +
                  "</div><div>\n" +
                  "</div></body>");
    collapseFoldingRegion(2);
    verifyAnchors(myFixture.getEditor().getFoldingModel().getAllFoldRegions()[1],
                  0, Type.EXPANDED_TOP,
                  1, Type.EXPANDED_BOTTOM,
                  2, Type.EXPANDED_BOTTOM);
  }

  public void testWithEmptyLastLine() {
    myFixture.configureByText(FileTypes.PLAIN_TEXT, "some text\n");
    final FoldingModel foldingModel = myFixture.getEditor().getFoldingModel();
    foldingModel.runBatchFoldingOperation(() -> foldingModel.addFoldRegion(0, 10, "..."));
    verifyAnchors(null,
                  0, Type.EXPANDED_TOP,
                  1, Type.EXPANDED_BOTTOM);
  }

  private void prepareEditor(String text) {
    myFixture.configureByText(StdFileTypes.XML, text);
    CodeFoldingManager.getInstance(myFixture.getProject()).updateFoldRegions(myFixture.getEditor());
  }

  private void collapseFoldingRegion(int n) {
    FoldingModel foldingModel = myFixture.getEditor().getFoldingModel();
    final FoldRegion foldRegion = foldingModel.getAllFoldRegions()[n];
    foldingModel.runBatchFoldingOperation(() -> foldRegion.setExpanded(false));
  }

  private void verifyAnchors(FoldRegion activeFoldRegion, Object... expectedAnchorParameters) {
    Collection<DisplayedFoldingAnchor> actualAnchors = new FoldingAnchorsOverlayStrategy((EditorImpl)myFixture.getEditor())
      .getAnchorsToDisplay(0, myFixture.getEditor().getDocument().getTextLength(), activeFoldRegion);
    List<DisplayedFoldingAnchor> sortedActualAnchors = new ArrayList<>(actualAnchors);
    Collections.sort(sortedActualAnchors, (o1, o2) -> o1.visualLine - o2.visualLine);

    assertEquals("Wrong number of anchors", expectedAnchorParameters.length / 2, sortedActualAnchors.size());
    int i = 0;
    for (DisplayedFoldingAnchor anchor :  sortedActualAnchors) {
      int expectedVisualLine = (Integer) expectedAnchorParameters[i++];
      assertEquals("Folding anchor at wrong line found", expectedVisualLine, anchor.visualLine);
      Type expectedType = (Type)expectedAnchorParameters[i++];
      assertEquals("Folding anchor of wrong type found at line " + expectedVisualLine, expectedType, anchor.type);
    }
  }
}
