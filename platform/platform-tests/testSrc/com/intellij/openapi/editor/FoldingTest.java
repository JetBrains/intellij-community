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
package com.intellij.openapi.editor;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.testFramework.TestFileType;
import org.junit.Assert;

/**
 * @author max
 */
public class FoldingTest extends AbstractEditorTest {
  private FoldingModelEx myModel;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    init("I don't know what you mean by `glory,'\" Alice said" +
      "Humpty Dumpty smiled contemptuously. \"Of course you don't -- till I tell you. I meant `there's a nice knock-down argument for you!'" +
      "But glory doesn't mean `a nice knock-down argument,'\" Alice objected." +
      "When I use a word,\" Humpty Dumpty said, in a rather scornful tone, \"it means just what I choose it to mean -- neither more nor less." +
      "The question is,\" said Alice, \"whether you can make words mean so many different things." +
      "The question is,\" said Humpty Dumpty, \"which is to be master -- that's all.",
         TestFileType.TEXT);
    myModel = (FoldingModelEx)myEditor.getFoldingModel();
  }

  public void testCleanupInvalidRegions() {
    myModel.runBatchFoldingOperation(new Runnable() {
      @Override
      public void run() {
        myModel.addFoldRegion(0, 4, "/*...*/");
        myModel.addFoldRegion(5, 9, "/*...*/");
      }
    });
    assertSize(2, myModel.getAllFoldRegions());
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        myEditor.getDocument().deleteString(0, 5);
      }
    });
    assertSize(1, myModel.getAllFoldRegions());
  }

  public void testIntersects () throws Exception {
    myModel.runBatchFoldingOperation(new Runnable() {
      @Override
      public void run() {
        FoldRegion region = myModel.addFoldRegion(5, 10, ".");
        assertNotNull(region);
        region = myModel.addFoldRegion(7, 11, ".");
        assertNull(region);
        region = myModel.addFoldRegion(20, 30, ".");
        assertNotNull(region);
        region = myModel.addFoldRegion(9, 12, ".");
        assertNull(region);
        region = myModel.addFoldRegion(7, 10, ".");
        assertNotNull(region);
        region = myModel.addFoldRegion(7, 10, ".");
        assertNull(region);
        region = myModel.addFoldRegion(5, 30, ".");
        assertNotNull(region);
      }
    });
  }

  public void testIntersectsWithRegionFarInStorageOrder() {
    myModel.runBatchFoldingOperation(new Runnable() {
      @Override
      public void run() {
        FoldRegion region = myModel.addFoldRegion(0, 10, ".");
        assertNotNull(region);
        region = myModel.addFoldRegion(1, 5, ".");
        assertNotNull(region);
        region = myModel.addFoldRegion(6, 11, ".");
        assertNull(region);
      }
    });
  }

  public void testAddEmptyRegion() {
    FoldRegion region = null;
    try {
      region = myModel.addFoldRegion(5, 5, "...");
    }
    catch (AssertionError ignored) {
    }
    assertNull(region);
  }

  public void testCollapsedRegionQueries() {
    addCollapsedFoldRegion(5, 7, "...");
    FoldRegion[] regions = myModel.getAllFoldRegions();
    assertEquals(1, regions.length);
    FoldRegion region = regions[0];
    assertNotNull(region);

    assertFalse(myModel.isOffsetCollapsed(4));
    assertTrue(myModel.isOffsetCollapsed(5));
    assertTrue(myModel.isOffsetCollapsed(6));
    assertFalse(myModel.isOffsetCollapsed(7));
    assertFalse(myModel.isOffsetCollapsed(8));

    assertNull(myModel.getCollapsedRegionAtOffset(4));
    assertSame(region, myModel.getCollapsedRegionAtOffset(5));
    assertSame(region, myModel.getCollapsedRegionAtOffset(6));
    assertNull(myModel.getCollapsedRegionAtOffset(7));
    assertNull(myModel.getCollapsedRegionAtOffset(8));
  }

  public void testAdjacentRegions() {
    addCollapsedFoldRegion(5, 7, "AA");
    addCollapsedFoldRegion(7, 10, "BB");
    FoldRegion[] regions = myModel.getAllFoldRegions();
    assertEquals(2, regions.length);
    FoldRegion region1 = regions[0];
    assertNotNull(region1);
    FoldRegion region2 = regions[1];
    assertNotNull(region2);

    assertFalse(myModel.isOffsetCollapsed(4));
    assertTrue(myModel.isOffsetCollapsed(5));
    assertTrue(myModel.isOffsetCollapsed(6));
    assertTrue(myModel.isOffsetCollapsed(7));
    assertTrue(myModel.isOffsetCollapsed(8));
    assertFalse(myModel.isOffsetCollapsed(10));
    assertFalse(myModel.isOffsetCollapsed(11));

    assertNull(myModel.getCollapsedRegionAtOffset(4));
    assertSame(region1, myModel.getCollapsedRegionAtOffset(5));
    assertSame(region1, myModel.getCollapsedRegionAtOffset(6));
    assertSame(region2, myModel.getCollapsedRegionAtOffset(7));
    assertSame(region2, myModel.getCollapsedRegionAtOffset(8));
    assertNull(myModel.getCollapsedRegionAtOffset(10));
    assertNull(myModel.getCollapsedRegionAtOffset(11));
  }

  public void testTopLevel() {
    FoldRegion region = addCollapsedFoldRegion(5, 15, "...");
    addCollapsedFoldRegion(10, 12, "???");

    FoldRegion[] topLevelRegions = myModel.fetchTopLevel();
    Assert.assertArrayEquals(new FoldRegion[]{region}, topLevelRegions);
  }

  public void testLastCollapsedRegionBefore() {
    addCollapsedFoldRegion(1, 3, "...");
    addCollapsedFoldRegion(5, 6, "...");

    assertEquals(-1, myModel.getLastCollapsedRegionBefore(0));
    assertEquals(-1, myModel.getLastCollapsedRegionBefore(1));
    assertEquals(-1, myModel.getLastCollapsedRegionBefore(2));
    assertEquals(0, myModel.getLastCollapsedRegionBefore(3));
    assertEquals(0, myModel.getLastCollapsedRegionBefore(4));
    assertEquals(0, myModel.getLastCollapsedRegionBefore(5));
    assertEquals(1, myModel.getLastCollapsedRegionBefore(6));
    assertEquals(1, myModel.getLastCollapsedRegionBefore(7));
  }
  
  public void testSelectionIsRemovedWhenInterruptedByFolding() {
    myEditor.getSelectionModel().setSelection(0, 5);
    addCollapsedFoldRegion(3, 6, "...");
    
    assertFalse(myEditor.getSelectionModel().hasSelection());
  }
  
  public void testModelRemainsConsistentOnTextRemoval() {
    addCollapsedFoldRegion(0, 10, "...");
    addCollapsedFoldRegion(1, 9, "...");

    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        myEditor.getDocument().deleteString(0, 1);
      }
    });

    addFoldRegion(20, 21, "..."); // an arbitrary action to rebuild folding caches
    
    assertTrue(myModel.isOffsetCollapsed(5));
  }
  
  public void testIdenticalRegionsAreRemoved() {
    addFoldRegion(0, 5, "...");
    addFoldRegion(0, 4, "...");
    assertNumberOfValidFoldRegions(2);

    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        myEditor.getDocument().deleteString(4, 5);
      }
    });


    assertNumberOfValidFoldRegions(1);
  }

  public void testTopLevelRegionRemainsTopLevelAfterMergingIdenticalRegions() {
    addCollapsedFoldRegion(10, 15, "...");
    addCollapsedFoldRegion(10, 14, "...");
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        myEditor.getDocument().deleteString(14, 15);
      }
    });


    FoldRegion region = myModel.getCollapsedRegionAtOffset(10);
    assertNotNull(region);
    assertTrue(region.isValid());
    assertEquals(10, region.getStartOffset());
    assertEquals(14, region.getEndOffset());

    addFoldRegion(0, 1, "...");

    FoldRegion region2 = myModel.getCollapsedRegionAtOffset(10);
    assertNotNull(region2);
    assertTrue(region2.isValid());
    assertEquals(10, region2.getStartOffset());
    assertEquals(14, region2.getEndOffset());
    assertSame(region, region2);
  }

  private void assertNumberOfValidFoldRegions(int expectedValue) {
    int actualValue = 0;
    for (FoldRegion region : myModel.getAllFoldRegions()) {
      if (region.isValid()) {
        actualValue++;
      }
    }
    assertEquals(expectedValue, actualValue);
  }
}
