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

public class IntervalTreeTest extends LightPlatformTestCase {
  private final DocumentImpl document = new DocumentImpl(" ".repeat(1000));
  private final RangeMarkerTree<RangeMarkerEx> tree = new RangeMarkerTree<>() {
    @Override
    protected boolean keepIntervalOnWeakReference(@NotNull RangeMarkerEx interval) {
      return false;
    }

    @Override
    protected boolean isDelicious(@NotNull RangeMarkerEx interval) {
      return ((RangeMarkerImpl)interval).isStickingToRight();
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

    try (MarkupIterator<RangeMarkerEx> iterator = tree.overlappingDeliciousIterator(new TextRange(0, 4))) {
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
      if (tree.isDelicious(m)) {
        delicis++;
      }
    }

    try (MarkupIterator<RangeMarkerEx> iterator = tree.overlappingDeliciousIterator(new TextRange(0, N))) {
      int c = 0;
      while (iterator.hasNext()) {
        RangeMarkerEx next = iterator.next();
        assertTrue(tree.isDelicious(next));
        c++;
      }
      assertEquals(delicis, c);
    }
  }

  public void testDeliciousIterationMustBeFastStress() {
    int N = 500_000;
    int delicis=0;
    Random r = new Random();
    for (int i=0;i<N;i++) {
      int start = r.nextInt(document.getTextLength());
      if (i%100 == 0) {
        // every 100th is delicious
        start = start / 2 * 2;
      }
      else {
        start = start / 2 * 2 + 1;
      }
      int end = start + 1;
      RangeMarkerImpl m = create(start, end);
      if (tree.isDelicious(m)) {
        delicis++;
      }
    }

    //System.out.println("delicis = " + delicis);

    for (int i=0;i<10;i++) {
      int finalDelicis = delicis;
      long dt = TimeoutUtil.measureExecutionTime(() -> {
        try (MarkupIterator<RangeMarkerEx> iterator = tree.overlappingDeliciousIterator(new TextRange(0, N))) {
          int c = 0;
          while (iterator.hasNext()) {
            RangeMarkerEx next = iterator.next();
            assertTrue(tree.isDelicious(next));
            c++;
          }
          assertEquals(finalDelicis, c);
        }
      });
      long t = TimeoutUtil.measureExecutionTime(() -> {
        try (MarkupIterator<RangeMarkerEx> iterator = new FilteringMarkupIterator<>(tree.overlappingIterator(new TextRange(0, N)),
                                                                                    h -> tree.isDelicious(h))) {
          int c = 0;
          while (iterator.hasNext()) {
            RangeMarkerEx next = iterator.next();
            assertTrue(tree.isDelicious(next));
            c++;
          }
          assertEquals(finalDelicis, c);
        }
      });
      //System.out.println("dt = " + dt+"; t = " + t);
      assertTrue("dt = " + dt+"; t = " + t, dt*10 < t);
    }
  }
}
