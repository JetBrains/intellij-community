// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.diff;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class IntLCSTest {
  @Test
  public void testDiag() {
    Diff.Change change = buildChange(new int[]{1, 2, 3}, new int[]{1, 2, 3}, 0);
    assertNull(change);
  }

  @Test
  public void testOneAtBegging() {
    Diff.Change change = buildChange(new int[]{1, 2}, new int[]{1, 3}, 2);
    checkLastChange(change, 1, 1, 1, 1);
  }

  @Test
  public void testOneAntEnd() {
    Diff.Change change = buildChange(new int[]{1, 3}, new int[]{2, 3}, 2);
    checkLastChange(change, 0, 0, 1, 1);
  }

  @Test
  public void testOneOverAtEnd() {
    Diff.Change change = buildChange(new int[]{1, 2}, new int[]{1, 2, 3}, 1);
    checkLastChange(change, 2, 2, 1, 0);
  }

  @Test
  public void testOneOverAtBegging() {
    Diff.Change change = buildChange(new int[]{1, 2, 3}, new int[]{2, 3}, 1);
    checkLastChange(change, 0, 0, 0, 1);
  }

  @Test
  public void testOneTail() {
    assertEquals(1, countChanges(new int[]{1, 2}, new int[]{1, 2, 3}));
  }

  @Test
  public void testSingleMiddle() {
    Diff.Change change = buildChange(new int[]{1, 2, 3}, new int[]{4, 2, 5}, 4);
    checkChange(change, 0, 0, 1, 1);
    checkLastChange(change.link, 2, 2, 1, 1);
  }

  @Test
  public void testAbsolutelyDifferent() {
    assertEquals(4, countChanges(new int[]{1, 2}, new int[]{3, 4}));
    assertEquals(6, countChanges(new int[]{1, 2, 3}, new int[]{4, 5, 6}));
  }

  private static Diff.Change buildChange(int[] first, int[] second, int expectedNonDiags) {
    assertEquals(expectedNonDiags, countChanges(first, second));
    MyersLCS intLCS = new MyersLCS(first, second);
    intLCS.execute();
    Reindexer reindexer = new Reindexer();
    reindexer.idInit(first.length, second.length);
    Diff.ChangeBuilder builder = new Diff.ChangeBuilder(0);
    reindexer.reindex(intLCS.getChanges(), builder);
    return builder.getFirstChange();
  }

  public static void checkChange(Diff.Change change, int line0, int line1, int inserted, int deleted) {
    assertNotNull(change, "Expected not null change");
    String message = change.toString();
    assertEquals(line0, change.line0, message + " line0:");
    assertEquals(line1, change.line1, message + " line1:");
    assertEquals(inserted, change.inserted, message + " insert:");
    assertEquals(deleted, change.deleted, message + " delete:");
  }

  public static void checkLastChange(Diff.Change change, int line0, int line1, int inserted, int deleted) {
    checkChange(change, line0, line1, inserted, deleted);
    assertNull(change.link, "Expected last change");
  }

  private static int countChanges(int[] first, int[] second) {
    MyersLCS lcs = new MyersLCS(first, second);
    lcs.execute();
    BitSet[] changes = lcs.getChanges();
    return changes[0].cardinality() + changes[1].cardinality();
  }
}
