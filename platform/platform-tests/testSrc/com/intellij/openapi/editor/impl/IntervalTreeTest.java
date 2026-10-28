// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.MarkupIterator;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.editor.ex.RangeMarkers;
import com.intellij.openapi.util.TextRange;
import com.intellij.testFramework.JUnit38AssumeSupportRunner;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.CommonProcessors;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assume.assumeFalse;

@RunWith(JUnit38AssumeSupportRunner.class)
public class IntervalTreeTest extends LightPlatformTestCase {
  private final DocumentImpl document = new DocumentImpl(" ".repeat(1000));
  private final RangeMarkerTreeForTests tree = new RangeMarkerTreeForTests();

  @Override
  protected void setUp() throws Exception {
    assumeFalse("See SnapshotMarkerEngineImplTest instead. This test covers the legacy interval tree but the snapshot marker engine is on. Turn off RangeMarkers.Holder.USE_PMARKER_IMPLEMENTATION temporarily if you really need to run this test", RangeMarkers.Holder.USE_PMARKER_IMPLEMENTATION);
    super.setUp();
  }

  @Override
  protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    if (isStressTest()) {
      super.runTestRunnable(testRunnable);
    }
    else {
      RedBlackTree.runAssertingInternalInvariants(() -> super.runTestRunnable(testRunnable));
    }
  }

  public void testDeliciousOrder() {
    RangeMarkerImpl r01 = create(0, 1);
    create(1, 2);
    RangeMarkerImpl r23 = create(2, 3);
    create(3, 4);

    try (MarkupIterator<RangeMarkerEx> iterator = tree.overlappingDeliciousIterator(new TextRange(0, 4), MY_FLAVOR_FLAG)) {
      assertOrderedEquals(()->iterator, r01, r23);
    }
  }

  private RangeMarkerImpl create(int start, int end) {
    RangeMarkerImpl marker = new RangeMarkerImpl(document, start, end, false, false) {
      @Override
      public byte getFlavorFlags() {
        return isStickingToRight() ? MY_FLAVOR_FLAG : 0;
      }
    };
    tree.addInterval(marker, start, end, false, false, false, 0);
    marker.setStickingToRight(start%2==0);
    tree.verifyProperties();
    return marker;
  }
  public void test1() {
    create(2, 3);
    create(2, 3);
  }
  public void test2() {
    create(2, 3);
    create(0, 1);
    create(2, 3);
  }
  public void test3() {
    create(33, 34);
    create(39, 40);
  }
  public void test4() {
    create(29, 30);
    create(90, 91);
  }

  public void testReplacingMiddleOfDocumentWithManyMarkers() {
    // so many markers change that the tree corrects maxEnd once after removing them, see RangeMarkerTree.updateAffectedNodes
    DocumentImpl document = new DocumentImpl("abcdefghij\n".repeat(1_000));
    int length = document.getTextLength();
    int changeStart = length / 4;
    int changeEnd = 3 * length / 4;
    String replacement = "replacement\n";
    int shift = replacement.length() - (changeEnd - changeStart);
    List<RangeMarker> markers = new ArrayList<>();
    for (int offset = 0; offset + 5 <= length; offset += 3) {
      RangeMarker marker = document.createRangeMarker(offset, offset + 5);
      marker.setGreedyToLeft(offset % 2 == 0);
      marker.setGreedyToRight(offset % 5 == 0);
      markers.add(marker);
    }
    for (int offset = 0; offset < length; offset += 1_000) {
      markers.add(document.createRangeMarker(offset, offset));
      markers.add(document.createRangeMarker(offset, Math.min(length, offset + 4_000)));
    }
    RangeMarker enclosing = document.createRangeMarker(changeStart - 1, changeEnd + 1);
    markers.add(enclosing);
    List<TextRange> before = ContainerUtil.map(markers, RangeMarker::getTextRange);

    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.replaceString(changeStart, changeEnd, replacement));

    for (int i = 0; i < markers.size(); i++) {
      RangeMarker marker = markers.get(i);
      TextRange range = before.get(i);
      if (range.getEndOffset() < changeStart) {
        assertEquals(range, marker.getTextRange());
      }
      else if (range.getStartOffset() > changeEnd) {
        assertEquals(range.shiftRight(shift), marker.getTextRange());
      }
      else if (range.getStartOffset() > changeStart && range.getEndOffset() < changeEnd) {
        assertFalse(marker.isValid());
      }
    }
    assertEquals(new TextRange(changeStart - 1, changeEnd + 1 + shift), enclosing.getTextRange());

    // overlap queries skip subtrees by maxEnd, so they find every valid marker only if each maxEnd is right
    List<RangeMarker> valid = ContainerUtil.filter(markers, RangeMarker::isValid);
    for (int start = 0; start <= document.getTextLength(); start += 97) {
      int end = Math.min(document.getTextLength(), start + 500);
      Set<RangeMarker> expected = new HashSet<>();
      for (RangeMarker marker : valid) {
        if (Math.max(marker.getStartOffset(), start) <= Math.min(marker.getEndOffset(), end)) {
          expected.add(marker);
        }
      }
      Set<RangeMarker> found = new HashSet<>();
      document.processRangeMarkersOverlappingWith(start, end, new CommonProcessors.CollectProcessor<>(found));
      assertEquals(expected, found);
    }
  }

  public void testDeliciousRandomOrder() {
    int N = 100;
    int delicis=0;
    Random r = new Random();
    for (int i=0;i<N;i++) {
      int start = r.nextInt(N);
      int end = start + 1;
      //System.out.println("m = " + start);
      RangeMarkerImpl m = create(start, end);
      if (m.getFlavorFlags() == MY_FLAVOR_FLAG) {
        delicis++;
      }
    }

    try (MarkupIterator<RangeMarkerEx> iterator = tree.overlappingDeliciousIterator(new TextRange(0, N), MY_FLAVOR_FLAG)) {
      int c = 0;
      while (iterator.hasNext()) {
        RangeMarkerEx next = iterator.next();
        assertEquals(MY_FLAVOR_FLAG, next.getFlavorFlags());
        c++;
      }
      assertEquals(delicis, c);
    }
  }
  /// see [IntervalTreeImpl#nextAvailableFlavorFlag()]; do not waste precious bits for tests
  private static final byte MY_FLAVOR_FLAG = 1;
  public void testDeliciousIterationMustBeFastStress() {
    int N = 5000000;
    int delicis=0;
    Random r = new Random();
    for (int i=0;i<N;i++) {
      int start = r.nextInt(document.getTextLength());
      if (i%100000 == 0) {
        // every 100th is delicious
        start = start / 2 * 2;
      }
      else {
        start = start / 2 * 2 + 1;
      }
      int end = start + 1;
      RangeMarkerImpl m = create(start, end);
      if (m.getFlavorFlags() == MY_FLAVOR_FLAG) {
        delicis++;
      }
    }
    assertEquals(N, tree.size());

    //System.out.println("delicis = " + delicis);

    for (int i=0;i<10;i++) {
      int finalDelicis = delicis;
      long dt = TimeoutUtil.measureExecutionTime(() -> {
        try (MarkupIterator<RangeMarkerEx> iterator = tree.overlappingDeliciousIterator(new TextRange(0, document.getTextLength()),
                                                                                        MY_FLAVOR_FLAG)) {
          int c = 0;
          while (iterator.hasNext()) {
            RangeMarkerEx next = iterator.next();
            assertEquals(MY_FLAVOR_FLAG, next.getFlavorFlags());
            c++;
          }
          assertEquals(finalDelicis, c);
        }
      });
      AtomicInteger all = new AtomicInteger();
      long t = TimeoutUtil.measureExecutionTime(() -> {
        try (MarkupIterator<RangeMarkerEx> iterator = FilteringMarkupIterator.create(tree.overlappingIterator(new TextRange(0, document.getTextLength())),
                                                                                    h -> all.incrementAndGet() >= 0 && h.getFlavorFlags() ==
                                                                                                                       MY_FLAVOR_FLAG)) {
          int c = 0;
          while (iterator.hasNext()) {
            RangeMarkerEx next = iterator.next();
            assertEquals(MY_FLAVOR_FLAG, next.getFlavorFlags());
            c++;
          }
          assertEquals(finalDelicis, c);
        }
        assertEquals(N, all.get());
      });
      System.out.println("dt = " + dt+"; t = " + t);
      assertTrue("dt = " + dt+"; t = " + t, dt*10 < t);
    }
  }

  public void testSeveralTastes() {
    final DocumentImpl document = new DocumentImpl(" ".repeat(1000));
    byte MY_OTHER_FLAG = 2;
    final RangeMarkerTreeForTests tree = new RangeMarkerTreeForTests();

    int N = 100;
    for (int i=0; i<N; i++) {
      RangeMarkerImpl marker = new RangeMarkerImpl(document, i, i+1, false, false) {
        @Override
        public byte getFlavorFlags() {
          return isGreedyToRight() ? MY_FLAVOR_FLAG : isGreedyToLeft() ? MY_OTHER_FLAG : 0;
        }
      };
      tree.addInterval(marker, i, i+1, i%2==0, i%2==1, false, 0);
      tree.verifyProperties();
    }

    try (MarkupIterator<RangeMarkerEx> iterator = tree.overlappingDeliciousIterator(new TextRange(0, document.getTextLength()),
                                                                                    MY_FLAVOR_FLAG)) {
      int c = 0;
      while (iterator.hasNext()) {
        RangeMarkerEx next = iterator.next();
        assertEquals(MY_FLAVOR_FLAG, next.getFlavorFlags());
        c++;
      }
      assertEquals(N/2, c);
    }
    try (MarkupIterator<RangeMarkerEx> iterator = tree.overlappingDeliciousIterator(new TextRange(0, document.getTextLength()), MY_OTHER_FLAG)) {
      int c = 0;
      while (iterator.hasNext()) {
        RangeMarkerEx next = iterator.next();
        assertEquals(MY_OTHER_FLAG, next.getFlavorFlags());
        c++;
      }
      assertEquals(N/2, c);
    }
  }

  private static class RangeMarkerTreeForTests extends RangeMarkerTree<RangeMarkerEx> {
    @Override
    protected boolean keepIntervalOnWeakReference(@NotNull RangeMarkerEx interval) {
      return false;
    }

    @Override
    public @NotNull RMNode<RangeMarkerEx> addInterval(@NotNull RangeMarkerEx interval, int start, int end, boolean greedyToLeft, boolean greedyToRight, boolean stickingToRight, int layer) {
      return super.addInterval(interval, start, end, greedyToLeft, greedyToRight, stickingToRight, layer);
    }

    @Override
    public void verifyProperties() {
      super.verifyProperties();
    }

    @Override
    public int size() {
      return super.size();
    }

    @Override
    public MarkupIterator<RangeMarkerEx> overlappingDeliciousIterator(@NotNull TextRange range, byte tastePreference) {
      return super.overlappingDeliciousIterator(range, tastePreference);
    }

    @Override
    public @NotNull MarkupIterator<RangeMarkerEx> overlappingIterator(@NotNull TextRange rangeInterval) {
      return super.overlappingIterator(rangeInterval);
    }
  }
}
