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
package com.intellij.openapi.vcs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.ex.Range;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.BitSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public class LineStatusTrackerRevertAutoTest extends BaseLineStatusTrackerTestCase {
  private static final Logger LOG = Logger.getInstance(LineStatusTrackerRevertAutoTest.class);
  private Random myRng;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  public void testSimple() throws Throwable {
    doTest(System.currentTimeMillis(), 100, 10, 30, 10, -1, false);
  }

  public void testComplex() throws Throwable {
    doTest(System.currentTimeMillis(), 100, 10, 30, 10, 5, false);
  }

  public void testInitial() throws Throwable {
    doTestInitial(System.currentTimeMillis(), 100, 10, false);
  }

  public void testSimpleSmart() throws Throwable {
    doTest(System.currentTimeMillis(), 100, 10, 30, 10, -1, true);
  }

  public void testComplexSmart() throws Throwable {
    doTest(System.currentTimeMillis(), 100, 10, 30, 10, 5, true);
  }

  public void testInitialSmart() throws Throwable {
    doTestInitial(System.currentTimeMillis(), 100, 10, true);
  }

  public void doTest(long seed, int testRuns, int modifications, int testLength, final int changeLength, int iterations, boolean smart)
    throws Throwable {
    myRng = new Random(seed);
    for (int i = 0; i < testRuns; i++) {
      long currentSeed = getCurrentSeed();
      if (i % 1000 == 0) LOG.debug(String.valueOf(i));
      try {
        String initial = generateText(testLength);
        createDocument(initial, initial, smart);
        //System.out.println("Initial: " + initial.replace("\n", "\\n"));

        int count = myRng.nextInt(modifications);
        for (int j = 0; j < count; j++) {
          final int writeChanges = myRng.nextInt(4) + 1;
          runCommand(() -> {
            for (int k = 0; k < writeChanges; k++) {
              applyRandomChange(changeLength);
            }
          });

          checkCantTrim();
          checkCantMerge();
          checkInnerRanges();
        }

        if (iterations > 0) {
          checkRevertComplex(iterations);
        }
        else {
          checkRevert(myTracker.getRanges().size() * 2);
        }

        releaseTracker();
        UIUtil.dispatchAllInvocationEvents();
      }
      catch (Throwable e) {
        System.out.println("Seed: " + seed);
        System.out.println("TestRuns: " + testRuns);
        System.out.println("Modifications: " + modifications);
        System.out.println("TestLength: " + testLength);
        System.out.println("ChangeLength: " + changeLength);
        System.out.println("I: " + i);
        System.out.println("Current seed: " + currentSeed);
        throw e;
      }
    }
  }

  public void doTestInitial(long seed, int testRuns, int testLength, boolean smart) throws Throwable {
    myRng = new Random(seed);
    for (int i = 0; i < testRuns; i++) {
      if (i % 1000 == 0) LOG.debug(String.valueOf(i));
      long currentSeed = getCurrentSeed();
      try {
        String initial = generateText(testLength);
        String initialVcs = generateText(testLength);
        createDocument(initial, initialVcs, smart);

        checkCantTrim();
        checkCantMerge();
        checkInnerRanges();

        checkRevert(myTracker.getRanges().size() * 2);

        releaseTracker();
        UIUtil.dispatchAllInvocationEvents();
      }
      catch (Throwable e) {
        System.out.println("Seed: " + seed);
        System.out.println("TestRuns: " + testRuns);
        System.out.println("TestLength: " + testLength);
        System.out.println("I: " + i);
        System.out.println("Current seed: " + currentSeed);
        throw e;
      }
    }
  }

  private void checkRevert(int maxIterations) throws Exception {
    int count = 0;
    while (true) {
      if (count > maxIterations) throw new Exception("Revert loop detected");
      List<Range> ranges = myTracker.getRanges();
      if (ranges.isEmpty()) break;
      int index = myRng.nextInt(ranges.size());
      Range range = ranges.get(index);

      rollback(range);
      count++;
    }
    assertEquals(myDocument.getText(), myUpToDateDocument.getText());
  }

  private void checkRevertComplex(int iterations) {
    BitSet lines = new BitSet();

    for (int i = 0; i < iterations; i++) {
      lines.clear();

      for (int j = 0; j < myDocument.getLineCount() + 2; j++) {
        if (myRng.nextInt(10) < 3) {
          lines.set(j);
        }
      }

      rollback(lines);
    }

    lines.set(0, myDocument.getLineCount() + 2);
    rollback(lines);

    assertEquals(myDocument.getText(), myUpToDateDocument.getText());
  }

  private void applyRandomChange(int changeLength) {
    int textLength = myDocument.getTextLength();
    int type = myRng.nextInt(3);
    int offset = textLength != 0 ? myRng.nextInt(textLength) : 0;
    int length = textLength - offset != 0 ? myRng.nextInt(textLength - offset) : offset;
    String data = generateText(changeLength);
    //System.out.println("Change: " + type + " - " + offset + " - " + length + " - " + data.replace("\n", "\\n"));
    switch (type) {
      case 0: // insert
        myDocument.insertString(offset, data);
        break;
      case 1: // delete
        myDocument.deleteString(offset, offset + length);
        break;
      case 2: // modify
        myDocument.replaceString(offset, offset + length, data);
        break;
    }
  }

  @NotNull
  private String generateText(int textLength) {
    int length = myRng.nextInt(textLength);
    StringBuilder builder = new StringBuilder(length);

    for (int i = 0; i < length; i++) {
      int rnd = myRng.nextInt(10);
      if (rnd == 0) {
        builder.append(' ');
      }
      else if (rnd < 7) {
        builder.append(String.valueOf(rnd));
      }
      else {
        builder.append('\n');
      }
    }

    return builder.toString();
  }

  private long getCurrentSeed() throws Exception {
    Field seedField = myRng.getClass().getDeclaredField("seed");
    seedField.setAccessible(true);
    AtomicLong seedFieldValue = (AtomicLong) seedField.get(myRng);
    return seedFieldValue.get() ^ 0x5DEECE66DL;
  }
}
