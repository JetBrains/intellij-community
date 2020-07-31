// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.util.text.StringUtil;

import java.util.*;
import java.util.function.IntSupplier;

public class MarkerTreeWithPartialSumsTest extends AbstractEditorTest {
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
    setSeed(mySeed = SEED_OVERRIDE == null ? nextLong() : SEED_OVERRIDE);
  }};
  private long mySeed;

  private DocumentEx myDocument;
  private MarkerTreeWithPartialSums<MyRange> myTree;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myDocument = new DocumentImpl("abcdefghij");
    myTree = new MarkerTreeWithPartialSums<>(myDocument);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      if (myTree != null) myTree.dispose(myDocument);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testOneMarker() {
    MyRange marker = new MyRange(2, 1);
    checkSumForOffset(0, 0);
    checkSumForOffset(1, 0);
    checkSumForOffset(2, 1);
    checkSumForOffset(3, 1);
    checkSumForOffset(10, 1);
    marker.dispose();
    checkSumForOffset(2, 0);
    checkSumForOffset(3, 0);
  }

  public void testTwoMarkers() {
    new MyRange(2, 1);
    new MyRange(5, 2);
    checkSumForOffset(1, 0);
    checkSumForOffset(2, 1);
    checkSumForOffset(3, 1);
    checkSumForOffset(5, 3);
    checkSumForOffset(6, 3);
  }

  public void testTreeRotation() {
    new MyRange(1, 1);
    new MyRange(3, 2);
    new MyRange(5, 4);
    checkSumForOffset(10, 7);
  }

  public void testTwoMarkersAtSameOffset() {
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
  }

  public void testMarkerMergingOnEditing() {
    MyRange r1 = new MyRange(1, 1);
    MyRange r2 = new MyRange(2, 2);
    runWriteCommand(() -> myDocument.deleteString(1, 2));
    assertTrue(r1.isValid());
    assertTrue(r2.isValid());
    checkSumForOffset(0, 0);
    checkSumForOffset(1, 3);
    checkSumForOffset(2, 3);
  }

  public void testMarkerSplitOnEditing() {
    MyRange r1 = new MyRange(1, 1, false);
    MyRange r2 = new MyRange(1, 2, true);
    checkSumForOffset(0, 0);
    checkSumForOffset(1, 3);
    checkSumForOffset(2, 3);
    runWriteCommand(() -> myDocument.insertString(1, " "));
    assertTrue(r1.isValid());
    assertEquals(1, r1.getStartOffset());
    assertTrue(r2.isValid());
    assertEquals(2, r2.getStartOffset());
    checkSumForOffset(0, 0);
    checkSumForOffset(1, 1);
    checkSumForOffset(2, 3);
    checkSumForOffset(3, 3);
  }

  public void testMarkerDisposalOnEditing() {
    MyRange r1 = new MyRange(1, 1);
    runWriteCommand(() -> myDocument.deleteString(0, 2));
    assertFalse(r1.isValid());
    checkSumForOffset(0, 0);
    checkSumForOffset(1, 0);
    checkSumForOffset(2, 0);
  }

  public void testValueUpdate() {
    MyRange r = new MyRange(1, 1);
    checkSumForOffset(0, 0);
    checkSumForOffset(1, 1);
    checkSumForOffset(2, 1);
    r.setValue(2);
    checkSumForOffset(0, 0);
    checkSumForOffset(1, 2);
    checkSumForOffset(2, 2);
  }

  public void testStressByRandomOperations() {
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
      runWriteCommand(() -> myDocument.insertString(offset, StringUtil.repeat(" ", myRandom.nextInt(MAX_CHARS_PER_OPERATION) + 1)));
    }
  }

  private class RemoveCharacters implements Runnable {
    @Override
    public void run() {
      int textLength = myDocument.getTextLength();
      if (textLength <= 0) return;
      int offset = myRandom.nextInt(textLength);
      runWriteCommand(() -> myDocument.deleteString(offset, Math.min(textLength, offset + 1 + myRandom.nextInt(MAX_CHARS_PER_OPERATION))));
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
        runWriteCommand(() -> myDocument.moveText(startOffset, endOffset, targetOffset));
      }
    }
  }
}
