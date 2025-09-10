// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.ex.MarkupIterator;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.util.TextRange;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class IntervalTreeTest extends LightPlatformTestCase {
  private final DocumentImpl document = new DocumentImpl(" ".repeat(1000));
  private final RangeMarkerTreeForTests tree = new RangeMarkerTreeForTests() {
    @Override
    public byte getTasteFlags(@NotNull RangeMarkerEx interval) {
      return ((RangeMarkerImpl)interval).isStickingToRight() ? MY_TASTE_FLAG : 0;
    }
  };

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

    try (MarkupIterator<RangeMarkerEx> iterator = tree.overlappingDeliciousIterator(new TextRange(0, 4), MY_TASTE_FLAG)) {
      assertOrderedEquals(()->iterator, r01, r23);
    }
  }

  private RangeMarkerImpl create(int start, int end) {
    RangeMarkerImpl marker = new RangeMarkerImpl(document, start, end, false, false);
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

  public void testDeliciousRandomOrder() {
    int N = 100;
    int delicis=0;
    Random r = new Random();
    for (int i=0;i<N;i++) {
      int start = r.nextInt(N);
      int end = start + 1;
      //System.out.println("m = " + start);
      RangeMarkerImpl m = create(start, end);
      if (tree.getTasteFlags(m) == MY_TASTE_FLAG) {
        delicis++;
      }
    }

    try (MarkupIterator<RangeMarkerEx> iterator = tree.overlappingDeliciousIterator(new TextRange(0, N), MY_TASTE_FLAG)) {
      int c = 0;
      while (iterator.hasNext()) {
        RangeMarkerEx next = iterator.next();
        assertEquals(MY_TASTE_FLAG, tree.getTasteFlags(next));
        c++;
      }
      assertEquals(delicis, c);
    }
  }
  private static final byte MY_TASTE_FLAG = 1; // IntervalTreeImpl.nextAvailableTasteFlag(); do not waste precious bits for tests
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
      if (tree.getTasteFlags(m) == MY_TASTE_FLAG) {
        delicis++;
      }
    }
    assertEquals(N, tree.size());

    //System.out.println("delicis = " + delicis);

    for (int i=0;i<10;i++) {
      int finalDelicis = delicis;
      long dt = TimeoutUtil.measureExecutionTime(() -> {
        try (MarkupIterator<RangeMarkerEx> iterator = tree.overlappingDeliciousIterator(new TextRange(0, document.getTextLength()), MY_TASTE_FLAG)) {
          int c = 0;
          while (iterator.hasNext()) {
            RangeMarkerEx next = iterator.next();
            assertEquals(MY_TASTE_FLAG, tree.getTasteFlags(next));
            c++;
          }
          assertEquals(finalDelicis, c);
        }
      });
      AtomicInteger all = new AtomicInteger();
      long t = TimeoutUtil.measureExecutionTime(() -> {
        try (MarkupIterator<RangeMarkerEx> iterator = new FilteringMarkupIterator<>(tree.overlappingIterator(new TextRange(0, document.getTextLength())),
                                                                                    h -> all.incrementAndGet() >= 0 && tree.getTasteFlags(h) == MY_TASTE_FLAG)) {
          int c = 0;
          while (iterator.hasNext()) {
            RangeMarkerEx next = iterator.next();
            assertEquals(MY_TASTE_FLAG, tree.getTasteFlags(next));
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
    final RangeMarkerTreeForTests tree = new RangeMarkerTreeForTests() {
      @Override
      public byte getTasteFlags(@NotNull RangeMarkerEx interval) {
        return interval.isGreedyToRight() ? MY_TASTE_FLAG : interval.isGreedyToLeft() ? MY_OTHER_FLAG : 0;
      }
    };

    int N = 100;
    for (int i=0; i<N; i++) {
      RangeMarkerImpl marker = new RangeMarkerImpl(document, i, i+1, false, false);
      tree.addInterval(marker, i, i+1, i%2==0, i%2==1, false, 0);
      tree.verifyProperties();
    }

    try (MarkupIterator<RangeMarkerEx> iterator = tree.overlappingDeliciousIterator(new TextRange(0, document.getTextLength()), MY_TASTE_FLAG)) {
      int c = 0;
      while (iterator.hasNext()) {
        RangeMarkerEx next = iterator.next();
        assertEquals(MY_TASTE_FLAG, tree.getTasteFlags(next));
        c++;
      }
      assertEquals(N/2, c);
    }
    try (MarkupIterator<RangeMarkerEx> iterator = tree.overlappingDeliciousIterator(new TextRange(0, document.getTextLength()), MY_OTHER_FLAG)) {
      int c = 0;
      while (iterator.hasNext()) {
        RangeMarkerEx next = iterator.next();
        assertEquals(MY_OTHER_FLAG, tree.getTasteFlags(next));
        c++;
      }
      assertEquals(N/2, c);
    }
  }

  private static class RangeMarkerTreeForTests extends RangeMarkerTree<RangeMarkerEx> {
    @Override
    public byte getTasteFlags(@NotNull RangeMarkerEx interval) {
      return super.getTasteFlags(interval);
    }

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
