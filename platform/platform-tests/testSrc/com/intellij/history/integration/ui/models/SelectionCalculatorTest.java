// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.integration.ui.models;

import com.intellij.diff.Block;
import com.intellij.history.core.InMemoryLocalHistoryFacade;
import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.core.LocalHistoryTestCase;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.RootEntry;
import com.intellij.history.integration.IdeaGateway;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.easymock.EasyMock.*;

public class SelectionCalculatorTest extends LocalHistoryTestCase {
  IdeaGateway gw = new MyIdeaGateway();
  LocalHistoryFacade vcs = new InMemoryLocalHistoryFacade();

  @Test
  public void testSelectionWasNotChanged() {
    List<Revision> rr = createRevisions("abc\ndef\nghi", "abc1\ndef1\nghi1");
    RevisionSelectionCalculator c = new RevisionSelectionCalculator(gw, rr, 0, 2);

    Block b0 = c.getSelectionFor(rr.get(0), new NullProgress());
    Block b1 = c.getSelectionFor(rr.get(1), new NullProgress());

    assertBlock(0, 3, "abc1\ndef1\nghi1", b0);
    assertBlock(0, 3, "abc\ndef\nghi", b1);
  }

  @Test
  public void testSelectionWasMoved() {
    List<Revision> rr = createRevisions("abc\ndef\nghi", "def\nghi");
    RevisionSelectionCalculator c = new RevisionSelectionCalculator(gw, rr, 0, 1);

    Block b0 = c.getSelectionFor(rr.get(0), new NullProgress());
    Block b1 = c.getSelectionFor(rr.get(1), new NullProgress());

    assertBlock(0, 2, "def\nghi", b0);
    assertBlock(1, 3, "def\nghi", b1);
  }

  @Test
  public void testSelectionForVeryOldRevisionTakenBackward() {
    List<Revision> rr = createRevisions("ghi\nabc\ndef", "abc\nghi\ndef", "abc\ndef\nghi");
    RevisionSelectionCalculator c = new RevisionSelectionCalculator(gw, rr, 0, 1);

    Block b2 = c.getSelectionFor(rr.get(2), new NullProgress());
    Block b1 = c.getSelectionFor(rr.get(1), new NullProgress());
    Block b0 = c.getSelectionFor(rr.get(0), new NullProgress());

    assertBlock(0, 2, "abc\ndef", b0);
    assertBlock(0, 3, "abc\nghi\ndef", b1);
    assertBlock(1, 3, "abc\ndef", b2);
  }

  @Test
  public void testNormalizingLineEnds() {
    List<Revision> rr = createRevisions("abc\ndef\nghi", "abc\r\ndef\r\nghi");
    RevisionSelectionCalculator c = new RevisionSelectionCalculator(gw, rr, 0, 1);

    Block b0 = c.getSelectionFor(rr.get(0), new NullProgress());
    Block b1 = c.getSelectionFor(rr.get(1), new NullProgress());

    assertBlock(0, 2, "abc\ndef", b0);
    assertBlock(0, 2, "abc\ndef", b1);
  }

  @Test
  public void testProgressOnGetSelection() {
    List<Revision> rr = createRevisions("one", "two", "three", "four");
    RevisionSelectionCalculator c = new RevisionSelectionCalculator(gw, rr, 0, 0);

    Progress p = createStrictMock(Progress.class);
    p.processed(25);
    p.processed(50);
    p.processed(75);
    p.processed(100);
    replay(p);

    c.getSelectionFor(rr.get(3), p);

    verify(p);
  }

  @Test
  public void testProgressOnCanCalculate() {
    List<Revision> rr = createRevisions("one", "two");
    RevisionSelectionCalculator c = new RevisionSelectionCalculator(gw, rr, 0, 0);

    Progress p = createMock(Progress.class);
    p.processed(50);
    p.processed(100);
    replay(p);

    c.canCalculateFor(rr.get(1), p);

    verify(p);
  }

  private List<Revision> createRevisions(String... contents) {
    RootEntry r = new RootEntry();
    vcs.addChangeInTests(createFile(r, "f", contents[0], -1, false));
    for (int i = 1; i < contents.length; i++) {
      vcs.addChangeInTests(changeContent(r, "f", contents[i], i));
    }
    return collectRevisions(vcs, r, "f", null, null);
  }

  private void assertBlock(int from, int to, String content, Block b) {
    assertEquals(from, b.getStart());
    assertEquals(to, b.getEnd());
    assertEquals(content, b.getBlockContent());
  }

  private static class MyIdeaGateway extends IdeaGateway {
    @Override
    public String stringFromBytes(byte @NotNull [] bytes, @NotNull String path) {
      return new String(bytes, StandardCharsets.UTF_8);
    }
  }
}
