// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.editor.ex.FoldingListener;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.testFramework.TestFileType;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author max
 */
public class FoldingTest extends AbstractEditorTest {
  private FoldingModelEx myModel;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    init("I don't know what you mean by `glory,'\" Alice said. " +
      "Humpty Dumpty smiled contemptuously. \"Of course you don't -- till I tell you. I meant `there's a nice knock-down argument for you!'" +
      "But glory doesn't mean `a nice knock-down argument,'\" Alice objected." +
      "When I use a word,\" Humpty Dumpty said, in a rather scornful tone, \"it means just what I choose it to mean -- neither more nor less." +
      "The question is,\" said Alice, \"whether you can make words mean so many different things." +
      "The question is,\" said Humpty Dumpty, \"which is to be master -- that's all.",
         TestFileType.TEXT);
    myModel = (FoldingModelEx)myEditor.getFoldingModel();
  }

  @Override
  protected void tearDown() throws Exception {
    myModel = null;
    super.tearDown();
  }

  public void testCleanupInvalidRegions() {
    myModel.runBatchFoldingOperation(() -> {
      myModel.addFoldRegion(0, 4, "/*...*/");
      myModel.addFoldRegion(5, 9, "/*...*/");
    });
    assertSize(2, myModel.getAllFoldRegions());
    WriteCommandAction.runWriteCommandAction(getProject(), () -> myEditor.getDocument().deleteString(0, 5));
    assertSize(1, myModel.getAllFoldRegions());
  }

  public void testIntersects () {
    myModel.runBatchFoldingOperation(() -> {
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
    });
  }

  public void testIntersectsWithRegionFarInStorageOrder() {
    myModel.runBatchFoldingOperation(() -> {
      FoldRegion region = myModel.addFoldRegion(0, 10, ".");
      assertNotNull(region);
      region = myModel.addFoldRegion(1, 5, ".");
      assertNotNull(region);
      region = myModel.addFoldRegion(6, 11, ".");
      assertNull(region);
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
    assertFalse(myModel.isOffsetCollapsed(4));
    assertTrue(myModel.isOffsetCollapsed(5));
    assertTrue(myModel.isOffsetCollapsed(6));
    assertFalse(myModel.isOffsetCollapsed(7));
    assertFalse(myModel.isOffsetCollapsed(8));
    assertFalse(myModel.isOffsetCollapsed(10));
    assertFalse(myModel.isOffsetCollapsed(11));

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

    WriteCommandAction.runWriteCommandAction(getProject(), () -> myEditor.getDocument().deleteString(0, 1));

    addFoldRegion(20, 21, "..."); // an arbitrary action to rebuild folding caches
    
    assertTrue(myModel.isOffsetCollapsed(5));
  }
  
  public void testAmongIdenticalRegionsExpandedOnesShouldBeKilledFirst() {
    FoldRegion c = addCollapsedFoldRegion(0, 10, "...");
    FoldRegion e = addFoldRegion(1, 10, "...");

    WriteCommandAction.runWriteCommandAction(getProject(), () -> myEditor.getDocument().deleteString(0, 1));

    assertFalse(e.isValid());
    assertTrue(c.isValid());
    FoldRegion survivor = assertOneElement(myModel.getAllFoldRegions());
    assertSame(c, survivor);
  }

  public void testAmongIdenticalRegionsExpandedOnesShouldBeKilledFirst2() {
    FoldRegion e = addFoldRegion(0, 10, "...");
    FoldRegion c = addCollapsedFoldRegion(1, 10, "...");

    WriteCommandAction.runWriteCommandAction(getProject(), () -> myEditor.getDocument().deleteString(0, 1));

    assertFalse(e.isValid());
    assertTrue(c.isValid());
    FoldRegion survivor = assertOneElement(myModel.getAllFoldRegions());
    assertSame(c, survivor);
  }

  public void testIdenticalRegionsAreRemoved() {
    addFoldRegion(0, 5, "...");
    addFoldRegion(0, 4, "...");
    assertNumberOfValidFoldRegions(2);

    WriteCommandAction.runWriteCommandAction(getProject(), () -> myEditor.getDocument().deleteString(4, 5));


    assertNumberOfValidFoldRegions(1);
  }

  public void testNotificationIsSentOnRemovalOfNonExpandingRegion() {
    Ref<FoldRegion> regionRef = new Ref<>();
    runFoldingOperation(() -> {
      FoldRegion region = myModel.createFoldRegion(0, 1, "...", null, true);
      assertNotNull(region);
      assertTrue(region.isValid());
      region.setExpanded(false);
      regionRef.set(region);
    });
    List<FoldRegion> notifications = new ArrayList<>();
    myModel.addListener(new FoldingListener() {
      @Override
      public void onFoldRegionStateChange(@NotNull FoldRegion region) {
        notifications.add(region);
      }

      @Override
      public void onFoldProcessingEnd() {}
    }, getTestRootDisposable());
    runFoldingOperation(() -> myModel.removeFoldRegion(regionRef.get()));
    assertSize(1, notifications);
    assertEquals(regionRef.get(), notifications.get(0));
  }

  public void testTopLevelRegionRemainsTopLevelAfterMergingIdenticalRegions() {
    addCollapsedFoldRegion(10, 15, "...");
    addCollapsedFoldRegion(10, 14, "...");
    WriteCommandAction.runWriteCommandAction(getProject(), () -> myEditor.getDocument().deleteString(14, 15));


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

  public void testCantCreateOverlappingRegions() {
    FoldingModelImpl model = (FoldingModelImpl)myEditor.getFoldingModel();
    model.runBatchFoldingOperation(
      () -> {
        FoldRegion f1 = model.addFoldRegion(1, 10, "...");
        assertNotNull(f1);
        FoldRegion f2 = model.addFoldRegion(5, 11, "...");
        assertNull(f2);


        FoldRegion c1 = model.createFoldRegion(2, 9, "...", null, false);
        assertNotNull(c1);
        FoldRegion c2 = model.createFoldRegion(4, 12, "...", null, false);
        assertNull(c2);
      });
  }

  public void testCollapseInnerRegion() {
    FoldRegion inner = addFoldRegion(10, 15, "inner");
    FoldRegion outer = addCollapsedFoldRegion(10, 20, "outer");
    myModel.runBatchFoldingOperation(() -> inner.setExpanded(false));
    assertSame(outer, myModel.getCollapsedRegionAtOffset(10));
  }
  
  public void testNoIdenticalRegionsSpecialCase() {
    addFoldRegion(10, 20, "1");
    addFoldRegion(15, 20, "2");
    addFoldRegion(16, 17, "3");
    WriteAction.run(() -> myEditor.getDocument().deleteString(10, 15));
    FoldRegion[] regions = myEditor.getFoldingModel().getAllFoldRegions();
    assertSize(2, regions);
    assertEquals(TextRange.create(10, 15), TextRange.create(regions[0]));
    assertEquals(TextRange.create(11, 12), TextRange.create(regions[1]));
  }

  public void test1() {
    String text = myEditor.getDocument().getText().codePoints().mapToObj(c -> c + "\n").collect(Collectors.joining());
    WriteCommandAction.runWriteCommandAction(getProject(), ()-> myEditor.getDocument().setText(text));
    addFoldRegion(20, 151, "1");
    addFoldRegion(289, 357, "2");
    addCollapsedFoldRegion(282, 357, "3");
    FoldRegion[] regions = myEditor.getFoldingModel().getAllFoldRegions();
    assertSize(3, regions);
    assertEquals(21, ((FoldingModelImpl)myEditor.getFoldingModel()).getTotalNumberOfFoldedLines());
  }

  public void testInnerRegionAtTheEndOfOuterRegion() {
    addFoldRegion(10, 20, "outer");
    FoldRegion inner = addFoldRegion(15, 20, "inner");
    myModel.runBatchFoldingOperation(() -> inner.setExpanded(false));
    Assert.assertArrayEquals(new FoldRegion[]{inner}, myModel.fetchTopLevel());
  }

  public void testNestedRegions() {
    addFoldRegion(10, 20, "outer");
    FoldRegion inner = addCollapsedFoldRegion(10, 15, "inner");
    addCollapsedFoldRegion(11, 12, "innermost");
    Assert.assertArrayEquals(new FoldRegion[]{inner}, myModel.fetchTopLevel());
  }

  public void testIdenticalRegionsOtherCase() {
    FoldRegion inner = addCollapsedFoldRegion(10, 15, "inner");
    addFoldRegion(10, 20, "outer");
    WriteCommandAction.runWriteCommandAction(ourProject, () -> myEditor.getDocument().deleteString(15, 20));
    Assert.assertArrayEquals(new FoldRegion[]{inner}, myModel.fetchTopLevel());
  }
  
  public void testClearingInvalidatesFoldRegions() {
    FoldRegion region = addCollapsedFoldRegion(5, 10, "...");
    myModel.runBatchFoldingOperation(() -> myModel.clearFoldRegions());
    assertFalse(region.isValid());
  }

  public void testGroupIsUpdatedOnRegionDisposal() {
    FoldingGroup group = FoldingGroup.newGroup("test");
    FoldRegion[] regions = new FoldRegion[2];
    myModel.runBatchFoldingOperation(() -> {
      regions[0] = myModel.createFoldRegion(1, 2, "a", group, false);
      regions[1] = myModel.createFoldRegion(3, 4, "b", group, false);
    });
    assertNotNull(regions[0]);
    assertNotNull(regions[1]);
    List<FoldRegion> regionsInGroup = myModel.getGroupedRegions(group);
    assertTrue(regionsInGroup.size() == 2 && regionsInGroup.containsAll(Arrays.asList(regions)));
    WriteCommandAction.runWriteCommandAction(ourProject, () -> myEditor.getDocument().deleteString(0, 3));
    assertFalse(regions[0].isValid());
    assertTrue(regions[1].isValid());
    List<FoldRegion> newRegionsInGroup = myModel.getGroupedRegions(group);
    assertEquals(Arrays.asList(regions[1]), newRegionsInGroup);
  }

  public void testAllRegionsFromInvalidNodeAreRemovedFromGroups() {
    FoldingGroup group = FoldingGroup.newGroup("test");
    FoldRegion[] regions = new FoldRegion[3];
    myModel.runBatchFoldingOperation(() -> {
      regions[0] = myModel.createFoldRegion(1, 4, "a", group, false);
      regions[1] = myModel.createFoldRegion(3, 4, "b", group, false);
      regions[2] = myModel.createFoldRegion(20, 30, "c", group, false);
    });
    assertNotNull(regions[0]);
    assertNotNull(regions[1]);
    assertNotNull(regions[2]);
    WriteCommandAction.runWriteCommandAction(ourProject, () -> DocumentUtil.executeInBulk(myEditor.getDocument(), true, () -> {
      myEditor.getDocument().deleteString(1, 3); // make first two regions belong to the same interval tree node
      myEditor.getDocument().deleteString(1, 2); // invalidate regions
    }));
    assertFalse(regions[0].isValid());
    assertFalse(regions[1].isValid());
    assertTrue(regions[2].isValid());
    List<FoldRegion> regionsInGroup = myModel.getGroupedRegions(regions[2].getGroup());
    assertEquals(Collections.singletonList(regions[2]), regionsInGroup);
  }

  public void testAddingEmptyRegion() {
    FoldRegion region = addFoldRegion(0, 0, ".");
    assertNull(region);
  }
}
