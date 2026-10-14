// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.ex.FoldingListener;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.TestLoggerKt.assertErrorLogged;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class FoldingTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  private EditorModelFixture myFixture;

  private FoldingModelEx myModel;

  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();
    myFixture.init("I don't know what you mean by `glory,'\" Alice said. " +
      "Humpty Dumpty smiled contemptuously. \"Of course you don't -- till I tell you. I meant `there's a nice knock-down argument for you!'" +
      "But glory doesn't mean `a nice knock-down argument,'\" Alice objected." +
      "When I use a word,\" Humpty Dumpty said, in a rather scornful tone, \"it means just what I choose it to mean -- neither more nor less." +
      "The question is,\" said Alice, \"whether you can make words mean so many different things." +
      "The question is,\" said Humpty Dumpty, \"which is to be master -- that's all.",
         PlainTextFileType.INSTANCE);
    myModel = (FoldingModelEx)myFixture.getEditor().getFoldingModel();
  }

  @Test
  public void testCleanupInvalidRegions() {
    myFixture.runTest(() -> {
      myModel.runBatchFoldingOperation(() -> {
        myModel.addFoldRegion(0, 4, "/*...*/");
        myModel.addFoldRegion(5, 9, "/*...*/");
      });
      assertThat(myModel.getAllFoldRegions()).hasSize(2);
      myFixture.runWriteCommand(() -> myFixture.getEditor().getDocument().deleteString(0, 5));
      assertThat(myModel.getAllFoldRegions()).hasSize(1);
    });
  }

  @Test
  public void testIntersects () {
    myFixture.runTest(() -> {
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
    });
  }

  @Test
  public void testIntersectsWithRegionFarInStorageOrder() {
    myFixture.runTest(() -> {
      myModel.runBatchFoldingOperation(() -> {
        FoldRegion region = myModel.addFoldRegion(0, 10, ".");
        assertNotNull(region);
        region = myModel.addFoldRegion(1, 5, ".");
        assertNotNull(region);
        region = myModel.addFoldRegion(6, 11, ".");
        assertNull(region);
      });
    });
  }

  @Test
  public void testAddEmptyRegion() {
    myFixture.runTest(() -> {
      DefaultLogger.disableStderrDumping(myFixture.getTestRootDisposable());
      assertErrorLogged(Throwable.class, () -> {
        FoldRegion region = null;
        try {
          region = myModel.addFoldRegion(5, 5, "...");
        }
        catch (AssertionError ignored) {
        }
        assertNull(region);
      });
    });
  }

  @Test
  public void testCollapsedRegionQueries() {
    myFixture.runTest(() -> {
      myFixture.addCollapsedFoldRegion(5, 7, "...");
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
    });
  }

  @Test
  public void testAdjacentRegions() {
    myFixture.runTest(() -> {
      myFixture.addCollapsedFoldRegion(5, 7, "AA");
      assertFalse(myModel.isOffsetCollapsed(4));
      assertTrue(myModel.isOffsetCollapsed(5));
      assertTrue(myModel.isOffsetCollapsed(6));
      assertFalse(myModel.isOffsetCollapsed(7));
      assertFalse(myModel.isOffsetCollapsed(8));
      assertFalse(myModel.isOffsetCollapsed(10));
      assertFalse(myModel.isOffsetCollapsed(11));

      myFixture.addCollapsedFoldRegion(7, 10, "BB");
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
    });
  }

  @Test
  public void testTopLevel() {
    myFixture.runTest(() -> {
      FoldRegion region = myFixture.addCollapsedFoldRegion(5, 15, "...");
      myFixture.addCollapsedFoldRegion(10, 12, "???");

      FoldRegion[] topLevelRegions = myModel.fetchTopLevel();
      assertArrayEquals(new FoldRegion[]{region}, topLevelRegions);
    });
  }

  @Test
  public void testLastCollapsedRegionBefore() {
    myFixture.runTest(() -> {
      myFixture.addCollapsedFoldRegion(1, 3, "...");
      myFixture.addCollapsedFoldRegion(5, 6, "...");

      assertEquals(-1, myModel.getLastCollapsedRegionBefore(0));
      assertEquals(-1, myModel.getLastCollapsedRegionBefore(1));
      assertEquals(-1, myModel.getLastCollapsedRegionBefore(2));
      assertEquals(0, myModel.getLastCollapsedRegionBefore(3));
      assertEquals(0, myModel.getLastCollapsedRegionBefore(4));
      assertEquals(0, myModel.getLastCollapsedRegionBefore(5));
      assertEquals(1, myModel.getLastCollapsedRegionBefore(6));
      assertEquals(1, myModel.getLastCollapsedRegionBefore(7));
    });
  }

  @Test
  public void testSelectionIsRemovedWhenInterruptedByFolding() {
    myFixture.runTest(() -> {
      myFixture.getEditor().getSelectionModel().setSelection(0, 5);
      myFixture.addCollapsedFoldRegion(3, 6, "...");

      assertFalse(myFixture.getEditor().getSelectionModel().hasSelection());
    });
  }

  @Test
  public void testModelRemainsConsistentOnTextRemoval() {
    myFixture.runTest(() -> {
      myFixture.addCollapsedFoldRegion(0, 10, "...");
      myFixture.addCollapsedFoldRegion(1, 9, "...");

      myFixture.runWriteCommand(() -> myFixture.getEditor().getDocument().deleteString(0, 1));

      myFixture.addFoldRegion(20, 21, "..."); // an arbitrary action to rebuild folding caches

      assertTrue(myModel.isOffsetCollapsed(5));
    });
  }

  @Test
  public void testAmongIdenticalRegionsExpandedOnesShouldBeKilledFirst() {
    myFixture.runTest(() -> {
      FoldRegion c = myFixture.addCollapsedFoldRegion(0, 10, "...");
      FoldRegion e = myFixture.addFoldRegion(1, 10, "...");

      myFixture.runWriteCommand(() -> myFixture.getEditor().getDocument().deleteString(0, 1));

      assertFalse(e.isValid());
      assertTrue(c.isValid());
      FoldRegion[] survivors = myModel.getAllFoldRegions();
      assertThat(survivors).hasSize(1);
      FoldRegion survivor = survivors[0];
      assertSame(c, survivor);
    });
  }

  @Test
  public void testAmongIdenticalRegionsExpandedOnesShouldBeKilledFirst2() {
    myFixture.runTest(() -> {
      FoldRegion e = myFixture.addFoldRegion(0, 10, "...");
      FoldRegion c = myFixture.addCollapsedFoldRegion(1, 10, "...");

      myFixture.runWriteCommand(() -> myFixture.getEditor().getDocument().deleteString(0, 1));

      assertFalse(e.isValid());
      assertTrue(c.isValid());
      FoldRegion[] survivors = myModel.getAllFoldRegions();
      assertThat(survivors).hasSize(1);
      FoldRegion survivor = survivors[0];
      assertSame(c, survivor);
    });
  }

  @Test
  public void testIdenticalRegionsAreRemoved() {
    myFixture.runTest(() -> {
      myFixture.addFoldRegion(0, 5, "...");
      myFixture.addFoldRegion(0, 4, "...");
      assertNumberOfValidFoldRegions(2);

      myFixture.runWriteCommand(() -> myFixture.getEditor().getDocument().deleteString(4, 5));


      assertNumberOfValidFoldRegions(1);
    });
  }

  @Test
  public void testNotificationIsSentOnRemovalOfNonExpandingRegion() {
    myFixture.runTest(() -> {
      Ref<FoldRegion> regionRef = new Ref<>();
      myFixture.runFoldingOperation(() -> {
        FoldRegion region = myModel.createFoldRegion(0, 1, "...", null, true);
        assertNotNull(region);
        assertTrue(region.isValid());
        assertFalse(region.isExpanded());
        regionRef.set(region);
      });
      List<FoldRegion> notifications = new ArrayList<>();
      myModel.addListener(new FoldingListener() {
        @Override
        public void onFoldRegionStateChange(@NotNull FoldRegion region) {
          notifications.add(region);
        }
      }, myFixture.getTestRootDisposable());
      myFixture.runFoldingOperation(() -> myModel.removeFoldRegion(regionRef.get()));
      assertThat(notifications).hasSize(1);
      assertEquals(regionRef.get(), notifications.getFirst());
    });
  }

  @Test
  public void testTopLevelRegionRemainsTopLevelAfterMergingIdenticalRegions() {
    myFixture.runTest(() -> {
      myFixture.addCollapsedFoldRegion(10, 15, "...");
      myFixture.addCollapsedFoldRegion(10, 14, "...");
      myFixture.runWriteCommand(() -> myFixture.getEditor().getDocument().deleteString(14, 15));


      FoldRegion region = myModel.getCollapsedRegionAtOffset(10);
      assertNotNull(region);
      assertTrue(region.isValid());
      assertEquals(10, region.getStartOffset());
      assertEquals(14, region.getEndOffset());

      myFixture.addFoldRegion(0, 1, "...");

      FoldRegion region2 = myModel.getCollapsedRegionAtOffset(10);
      assertNotNull(region2);
      assertTrue(region2.isValid());
      assertEquals(10, region2.getStartOffset());
      assertEquals(14, region2.getEndOffset());
      assertSame(region, region2);
    });
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

  @Test
  public void testCantCreateOverlappingRegions() {
    myFixture.runTest(() -> {
      FoldingModelImpl model = (FoldingModelImpl)myFixture.getEditor().getFoldingModel();
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
    });
  }

  @Test
  public void testCollapseInnerRegion() {
    myFixture.runTest(() -> {
      FoldRegion inner = myFixture.addFoldRegion(10, 15, "inner");
      FoldRegion outer = myFixture.addCollapsedFoldRegion(10, 20, "outer");
      myModel.runBatchFoldingOperation(() -> inner.setExpanded(false));
      assertSame(outer, myModel.getCollapsedRegionAtOffset(10));
    });
  }

  @Test
  public void testNoIdenticalRegionsSpecialCase() {
    myFixture.runTest(() -> {
      myFixture.addFoldRegion(10, 20, "1");
      myFixture.addFoldRegion(15, 20, "2");
      myFixture.addFoldRegion(16, 17, "3");
      WriteAction.run(() -> myFixture.getEditor().getDocument().deleteString(10, 15));
      FoldRegion[] regions = myFixture.getEditor().getFoldingModel().getAllFoldRegions();
      assertThat(regions).hasSize(2);
      assertEquals(TextRange.create(10, 15), regions[0].getTextRange());
      assertEquals(TextRange.create(11, 12), regions[1].getTextRange());
    });
  }

  @Test
  public void test1() {
    myFixture.runTest(() -> {
      String text = myFixture.getEditor().getDocument().getText().codePoints().mapToObj(c -> c + "\n").collect(Collectors.joining());
      myFixture.runWriteCommand(()-> myFixture.getEditor().getDocument().setText(text));
      myFixture.addFoldRegion(20, 151, "1");
      myFixture.addFoldRegion(289, 357, "2");
      myFixture.addCollapsedFoldRegion(282, 357, "3");
      FoldRegion[] regions = myFixture.getEditor().getFoldingModel().getAllFoldRegions();
      assertThat(regions).hasSize(3);
      assertEquals(21, ((FoldingModelImpl)myFixture.getEditor().getFoldingModel()).getTotalNumberOfFoldedLines());
    });
  }

  @Test
  public void testInnerRegionAtTheEndOfOuterRegion() {
    myFixture.runTest(() -> {
      myFixture.addFoldRegion(10, 20, "outer");
      FoldRegion inner = myFixture.addFoldRegion(15, 20, "inner");
      myModel.runBatchFoldingOperation(() -> inner.setExpanded(false));
      assertArrayEquals(new FoldRegion[]{inner}, myModel.fetchTopLevel());
    });
  }

  @Test
  public void testNestedRegions() {
    myFixture.runTest(() -> {
      myFixture.addFoldRegion(10, 20, "outer");
      FoldRegion inner = myFixture.addCollapsedFoldRegion(10, 15, "inner");
      myFixture.addCollapsedFoldRegion(11, 12, "innermost");
      assertArrayEquals(new FoldRegion[]{inner}, myModel.fetchTopLevel());
    });
  }

  @Test
  public void testIdenticalRegionsOtherCase() {
    myFixture.runTest(() -> {
      FoldRegion inner = myFixture.addCollapsedFoldRegion(10, 15, "inner");
      myFixture.addFoldRegion(10, 20, "outer");
      myFixture.runWriteCommand(() -> myFixture.getEditor().getDocument().deleteString(15, 20));
      assertArrayEquals(new FoldRegion[]{inner}, myModel.fetchTopLevel());
    });
  }

  @Test
  public void testClearingInvalidatesFoldRegions() {
    myFixture.runTest(() -> {
      FoldRegion region = myFixture.addCollapsedFoldRegion(5, 10, "...");
      myModel.runBatchFoldingOperation(() -> myModel.clearFoldRegions());
      assertFalse(region.isValid());
    });
  }

  @Test
  public void testGroupIsUpdatedOnRegionDisposal() {
    myFixture.runTest(() -> {
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
      myFixture.runWriteCommand(() -> myFixture.getEditor().getDocument().deleteString(0, 3));
      assertFalse(regions[0].isValid());
      assertTrue(regions[1].isValid());
      List<FoldRegion> newRegionsInGroup = myModel.getGroupedRegions(group);
      assertEquals(Collections.singletonList(regions[1]), newRegionsInGroup);
    });
  }

  @Test
  public void testAllRegionsFromInvalidNodeAreRemovedFromGroups() {
    myFixture.runTest(() -> {
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
      myFixture.runWriteCommand(() -> DocumentUtil.executeInBulk(myFixture.getEditor().getDocument(), () -> {
        myFixture.getEditor().getDocument().deleteString(1, 3); // make first two regions belong to the same interval tree node
        myFixture.getEditor().getDocument().deleteString(1, 2); // invalidate regions
      }));
      assertFalse(regions[0].isValid());
      assertFalse(regions[1].isValid());
      assertTrue(regions[2].isValid());
      List<FoldRegion> regionsInGroup = myModel.getGroupedRegions(group);
      assertEquals(Collections.singletonList(regions[2]), regionsInGroup);
    });
  }

  @Test
  public void testAddingEmptyRegion() {
    myFixture.runTest(() -> {
      FoldRegion region = myFixture.addFoldRegion(0, 0, ".");
      assertNull(region);
    });
  }

  @Test
  public void testRegionBecomingInvalidIsRemovedFromGroup() {
    myFixture.runTest(() -> {
      FoldingGroup group = FoldingGroup.newGroup("test");

      myFixture.getEditor().getFoldingModel().runBatchFoldingOperation(
        () -> myModel.createFoldRegion(0, 10, "...", group, false)
      );
      myFixture.addCollapsedFoldRegion(1, 10, "...");

      myFixture.runWriteCommand(() -> myFixture.getEditor().getDocument().deleteString(0, 1));

      List<FoldRegion> regions = myModel.getGroupedRegions(group);
      assertThat(regions).isEmpty();
    });
  }

  @Test
  public void testMultipleCaretsUpdateOnRegionCollapsing() {
    myFixture.runTest(() -> {
      CaretModel caretModel = myFixture.getEditor().getCaretModel();
      caretModel.moveToVisualPosition(new VisualPosition(0, 0));
      caretModel.addCaret(new VisualPosition(0, 1));
      caretModel.addCaret(new VisualPosition(0, 2));
      caretModel.addCaret(new VisualPosition(0, 3));

      myFixture.addCollapsedFoldRegion(0, 4, "...");

      List<Caret> carets = caretModel.getAllCarets();
      assertThat(carets).hasSize(1);
      assertEquals(0, carets.getFirst().getOffset());
    });
  }

  @Test
  public void testMousePositionAfterClickOnCollapsedFolding() {
    myFixture.runTest(() -> {
      myFixture.initText("\ntext");
      myFixture.addCollapsedFoldRegion(0, myFixture.getEditor().getDocument().getTextLength(), "...");
      myFixture.mouse().clickAt(0, 1);
      assertEquals(new VisualPosition(0, 0), myFixture.getEditor().getCaretModel().getVisualPosition());
    });
  }

  @Test
  public void testExpandRegionDoesNotImpactOutsideCaret() {
    myFixture.runTest(() -> {
      myFixture.initText("""
                 (
                   foo [
                     bar<caret>
                   ]
                 )""");
      myFixture.foldOccurrences("(?s)\\(.*\\)", "...");
      myFixture.foldOccurrences("(?s)\\[.*\\]", "...");
      myFixture.checkResultByText("""
                          <caret>(
                            foo [
                              bar
                            ]
                          )""");

      myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getDocument().getText().indexOf("foo"));
      myFixture.verifyFoldingState("[FoldRegion -(0:23), placeholder='...', FoldRegion +(8:21), placeholder='...']");

      myFixture.executeAction(IdeActions.ACTION_EXPAND_ALL_REGIONS);

      myFixture.checkResultByText("""
                          (
                            <caret>foo [
                              bar
                            ]
                          )""");
    });
  }

  @Test
  public void testDisposalListenerMethodCalledOnExplicitRemoval() {
    myFixture.runTest(() -> {
      AtomicInteger invocationCount = new AtomicInteger();
      myModel.addListener(new FoldingListener() {
        @Override
        public void beforeFoldRegionDisposed(@NotNull FoldRegion region) {
          invocationCount.incrementAndGet();
        }
      }, myFixture.getTestRootDisposable());
      Ref<FoldRegion> regionRef = new Ref<>();
      myModel.runBatchFoldingOperation(() -> regionRef.set(myModel.addFoldRegion(1, 2, ".")));
      assertNotNull(regionRef.get());
      assertTrue(regionRef.get().isValid());
      assertEquals(0, invocationCount.get());
      myModel.runBatchFoldingOperation(() -> myModel.removeFoldRegion(regionRef.get()));
      assertFalse(regionRef.get().isValid());
      assertEquals(1, invocationCount.get());
    });
  }

  @Test
  public void testDisposalListenerMethodCalledOnImplicitRemoval() {
    myFixture.runTest(() -> {
      AtomicInteger invocationCount = new AtomicInteger();
      myModel.addListener(new FoldingListener() {
        @Override
        public void beforeFoldRegionDisposed(@NotNull FoldRegion region) {
          invocationCount.incrementAndGet();
        }
      }, myFixture.getTestRootDisposable());
      Ref<FoldRegion> regionRef = new Ref<>();
      myModel.runBatchFoldingOperation(() -> regionRef.set(myModel.addFoldRegion(1, 2, ".")));
      assertNotNull(regionRef.get());
      assertTrue(regionRef.get().isValid());
      assertEquals(0, invocationCount.get());
      myFixture.runWriteCommand(() -> myFixture.getEditor().getDocument().deleteString(0, 3));
      assertFalse(regionRef.get().isValid());
      assertEquals(1, invocationCount.get());
    });
  }
}
