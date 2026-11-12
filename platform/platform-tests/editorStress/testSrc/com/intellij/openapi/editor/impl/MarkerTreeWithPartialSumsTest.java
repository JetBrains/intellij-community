// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.RangeMarkers;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.IntSupplier;

import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@TestApplication
public class MarkerTreeWithPartialSumsTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  private EditorModelFixture myFixture;

  private static final Logger LOG = Logger.getInstance(MarkerTreeWithPartialSumsTest.class);

  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();
    assumeFalse(RangeMarkers.Holder.USE_PMARKER_IMPLEMENTATION, "See SnapshotMarkerEngineImplTest instead. This test covers the legacy range markers but the snapshot marker engine is on. Turn off RangeMarkers.Holder.USE_PMARKER_IMPLEMENTATION temporarily if you really need to run this test");
    EdtTestUtil.runInEdtAndWait(() -> {
      myDocument = new DocumentImpl("abcdefghij");
      myTree = new TestMarkerTreeWithPartialSums(myDocument);
    });
  }

  @AfterEach
  void tearDown() {
    EdtTestUtil.runInEdtAndWait(() -> {
      if (myTree != null) myTree.disposeInTests(myDocument);
    });
  }

  private static final int RANDOM_ITERATIONS = 10_000;
  private static final int MAX_VALUE = 10;
  private static final int MAX_CHARS_PER_OPERATION = 10;
  private static final Long SEED_OVERRIDE = null; // set non-null value to run with a specific seed

  private final List<? extends Runnable> ourActions = Arrays.asList(new AddMarker(),
                                                                    new RemoveMarker(),
                                                                    new UpdateMarker(),
                                                                    new AddCharacters(),
                                                                    new RemoveCharacters(),
                                                                    new MoveCharacters());
  private final Random myRandom = new Random() {{
    //noinspection ConstantValue
    setSeed(mySeed = SEED_OVERRIDE == null ? nextLong() : SEED_OVERRIDE);
  }};
  private long mySeed;

  private DocumentEx myDocument;
  private TestMarkerTreeWithPartialSums myTree;

  private static class TestMarkerTreeWithPartialSums extends MarkerTreeWithPartialSums<MyRange> {
    TestMarkerTreeWithPartialSums(@NotNull Document document) {
      super(document);
    }

    // make this method accessible to the test, even when the test is loaded with different classloader
    @Override
    public @NotNull RMNode<MyRange> addInterval(@NotNull MarkerTreeWithPartialSumsTest.MyRange interval,
                                                   int start,
                                                   int end,
                                                   boolean greedyToLeft,
                                                   boolean greedyToRight,
                                                   boolean stickingToRight,
                                                   int layer) {
      return super.addInterval(interval, start, end, greedyToLeft, greedyToRight, stickingToRight, layer);
    }
  }

  @Test
  public void testOneMarker() {
    myFixture.runTest(() -> {
      MyRange marker = new MyRange(2, 1);
      checkSumForOffset(0, 0);
      checkSumForOffset(1, 0);
      checkSumForOffset(2, 1);
      checkSumForOffset(3, 1);
      checkSumForOffset(10, 1);
      marker.dispose();
      checkSumForOffset(2, 0);
      checkSumForOffset(3, 0);
    });
  }

  @Test
  public void testTwoMarkers() {
    myFixture.runTest(() -> {
      new MyRange(2, 1);
      new MyRange(5, 2);
      checkSumForOffset(1, 0);
      checkSumForOffset(2, 1);
      checkSumForOffset(3, 1);
      checkSumForOffset(5, 3);
      checkSumForOffset(6, 3);
    });
  }

  @Test
  public void testTreeRotation() {
    myFixture.runTest(() -> {
      new MyRange(1, 1);
      new MyRange(3, 2);
      new MyRange(5, 4);
      checkSumForOffset(10, 7);
    });
  }

  @Test
  public void testTwoMarkersAtSameOffset() {
    myFixture.runTest(() -> {
      MyRange r1 = new MyRange(1, 1);
      MyRange r2 = new MyRange(1, 2);
      checkSumForOffset(0, 0);
      checkSumForOffset(1, 3);
      checkSumForOffset(2, 3);
      r1.dispose();
      checkSumForOffset(0, 0);
      checkSumForOffset(1, 2);
      checkSumForOffset(2, 2);
      r2.dispose();
      checkSumForOffset(0, 0);
      checkSumForOffset(1, 0);
      checkSumForOffset(2, 0);
    });
  }

  @Test
  public void testMarkerMergingOnEditing() {
    myFixture.runTest(() -> {
      MyRange r1 = new MyRange(1, 1);
      MyRange r2 = new MyRange(2, 2);
      myFixture.runWriteCommand(() -> myDocument.deleteString(1, 2));
      assertTrue(r1.isValid());
      assertTrue(r2.isValid());
      checkSumForOffset(0, 0);
      checkSumForOffset(1, 3);
      checkSumForOffset(2, 3);
    });
  }

  @Test
  public void testMarkerSplitOnEditing() {
    myFixture.runTest(() -> {
      MyRange r1 = new MyRange(1, 1, false);
      MyRange r2 = new MyRange(1, 2, true);
      checkSumForOffset(0, 0);
      checkSumForOffset(1, 3);
      checkSumForOffset(2, 3);
      myFixture.runWriteCommand(() -> myDocument.insertString(1, " "));
      assertTrue(r1.isValid());
      assertEquals(1, r1.getStartOffset());
      assertTrue(r2.isValid());
      assertEquals(2, r2.getStartOffset());
      checkSumForOffset(0, 0);
      checkSumForOffset(1, 1);
      checkSumForOffset(2, 3);
      checkSumForOffset(3, 3);
    });
  }

  @Test
  public void testMarkerDisposalOnEditing() {
    myFixture.runTest(() -> {
      MyRange r1 = new MyRange(1, 1);
      myFixture.runWriteCommand(() -> myDocument.deleteString(0, 2));
      assertFalse(r1.isValid());
      checkSumForOffset(0, 0);
      checkSumForOffset(1, 0);
      checkSumForOffset(2, 0);
    });
  }

  @Test
  public void testValueUpdate() {
    myFixture.runTest(() -> {
      MyRange r = new MyRange(1, 1);
      checkSumForOffset(0, 0);
      checkSumForOffset(1, 1);
      checkSumForOffset(2, 1);
      r.setValue(2);
      checkSumForOffset(0, 0);
      checkSumForOffset(1, 2);
      checkSumForOffset(2, 2);
    });
  }

  @Test
  public void testStressByRandomOperations() {
    myFixture.runTest(() -> {
      LOG.debug("Seed is " + mySeed);
      int i = 0;
      try {
        for (i = 0; i < RANDOM_ITERATIONS; i++) {
          ourActions.get(myRandom.nextInt(ourActions.size())).run();
          validateSums();
        }
      }
      catch (Throwable t) {
        String message = "Failed when run with seed=" + mySeed + " in iteration " + i;
        System.err.println(message);
        throw new RuntimeException(message, t);
      }
    });
  }

  private void validateSums() {
    List<MyRange> markers = getAllMarkers();
    SortedMap<Integer, Integer> allValues = new TreeMap<>();
    for (MyRange marker : markers) {
      int offset = marker.getStartOffset();
      Integer sumForOffset = allValues.get(offset);
      int value = marker.myValue;
      allValues.put(offset, sumForOffset == null ? value : sumForOffset + value);
    }
    int runningSum = 0;
    int prevOffset = Integer.MIN_VALUE;
    for (Map.Entry<Integer, Integer> e : allValues.entrySet()) {
      int offset = e.getKey();
      if (prevOffset < (offset - 1)) checkSumForOffset(offset - 1, runningSum);
      runningSum += e.getValue();
      checkSumForOffset(offset, runningSum);
      prevOffset = offset;
    }
    checkSumForOffset(prevOffset + 1, runningSum);
  }

  private void checkSumForOffset(int offset, int expectedSum) {
    assertEquals(expectedSum, myTree.getSumOfValuesUpToOffset(offset));
  }

  private List<MyRange> getAllMarkers() {
    List<MyRange> result = new ArrayList<>();
    myTree.processAll(result::add);
    return result;
  }

  private class MyRange extends RangeMarkerImpl implements IntSupplier {
    private int myValue;

    MyRange(int offset, int value) {
      this(offset, value, false);
    }

    MyRange(int offset, int value, boolean stickToRight) {
      super(myDocument, offset, offset, false, true);
      myValue = value;
      myTree.addInterval(this, offset, offset, false, false, stickToRight, 0);
    }

    @Override
    public void dispose() {
      myTree.removeInterval(this);
    }

    @Override
    public int getAsInt() {
      return myValue;
    }

    private void setValue(int newValue) {
      myValue = newValue;
      myTree.valueUpdated(this);
    }
  }

  private class AddMarker implements Runnable {
    @Override
    public void run() {
      new MyRange(myRandom.nextInt(myDocument.getTextLength() + 1), myRandom.nextInt(MAX_VALUE), myRandom.nextBoolean());
    }
  }

  private class RemoveMarker implements Runnable {
    @Override
    public void run() {
      List<MyRange> allMarkers = getAllMarkers();
      if (!allMarkers.isEmpty()) {
        allMarkers.get(myRandom.nextInt(allMarkers.size())).dispose();
      }
    }
  }

  private class UpdateMarker implements Runnable {
    @Override
    public void run() {
      List<MyRange> allMarkers = getAllMarkers();
      if (!allMarkers.isEmpty()) {
        allMarkers.get(myRandom.nextInt(allMarkers.size())).setValue(myRandom.nextInt(MAX_VALUE));
      }
    }
  }

  private class AddCharacters implements Runnable {
    @Override
    public void run() {
      int offset = myRandom.nextInt(myDocument.getTextLength() + 1);
      myFixture.runWriteCommand(
        () -> myDocument.insertString(offset, StringUtil.repeat(" ", myRandom.nextInt(MAX_CHARS_PER_OPERATION) + 1)));
    }
  }

  private class RemoveCharacters implements Runnable {
    @Override
    public void run() {
      int textLength = myDocument.getTextLength();
      if (textLength <= 0) return;
      int offset = myRandom.nextInt(textLength);
      myFixture.runWriteCommand(
        () -> myDocument.deleteString(offset, Math.min(textLength, offset + 1 + myRandom.nextInt(MAX_CHARS_PER_OPERATION))));
    }
  }

  private class MoveCharacters implements Runnable {
    @Override
    public void run() {
      int textLength = myDocument.getTextLength();
      if (textLength <= 0) return;
      int startOffset = myRandom.nextInt(textLength);
      int endOffset = Math.min(textLength, startOffset + 1 + myRandom.nextInt(MAX_CHARS_PER_OPERATION));
      int targetOffset = myRandom.nextInt(textLength + 1);
      if (targetOffset < startOffset || targetOffset > endOffset) {
        myFixture.runWriteCommand(() -> myDocument.moveText(startOffset, endOffset, targetOffset));
      }
    }
  }
}
