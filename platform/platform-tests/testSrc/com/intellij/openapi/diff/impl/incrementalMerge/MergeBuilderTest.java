package com.intellij.openapi.diff.impl.incrementalMerge;

import com.intellij.idea.IdeaLogger;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.util.ContextLogger;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Assertion;
import junit.framework.TestCase;

import java.util.List;

public class MergeBuilderTest extends TestCase {
  private final MergeBuilder myMergeBuilder = new MergeBuilder(new ContextLogger("TEST"));
  private final Assertion CHECK = new Assertion();

  public void testEqual() {
    addLeft(new TextRange(0, 1), new TextRange(0, 1));
    addRight(new TextRange(0, 1), new TextRange(0, 1));
    CHECK.empty(finish(1, 1, 1));
  }

  public void testWholeConflict() {
    CHECK.singleElement(finish(1, 2, 3),
                        fragment(new TextRange(0, 1), new TextRange(0, 2), new TextRange(0, 3)));
  }

  public void testTailInsert() {
    TextRange range = new TextRange(0, 1);
    addLeft(range, range);
    addRight(range, range);
    CHECK.singleElement(finish(1, 1, 2),
                        fragment(new TextRange(1, 1), new TextRange(1, 1), new TextRange(1, 2)));
  }

  public void testSameInsertsConflicts1() {
    TextRange base = new TextRange(0, 1);
    TextRange version = new TextRange(1, 2);
    addLeft(base, version);
    addRight(base, version);
    CHECK.singleElement(finish(2, 1, 2),
                        fragment(new TextRange(0, 1), new TextRange(0, 0), new TextRange(0, 1)));
  }

  public void testSameInsertsConflicts2() {
    TextRange base = new TextRange(1, 2);
    TextRange version = new TextRange(0, 1);
    addLeft(base, version);
    addRight(base, version);
    CHECK.compareAll(new MergeFragment[]{
      fragment(new TextRange(0, 0), new TextRange(0, 1), new TextRange(0, 0)),
      fragment(new TextRange(1, 2), new TextRange(2, 2), new TextRange(1, 2))
    }, finish(2, 2, 2));
  }

  public void testHeadInsert() {
    TextRange range = new TextRange(0, 1);
    addRight(range, new TextRange(1, 2));
    addLeft(range, range);
    CHECK.singleElement(finish(1, 1, 2),
                        fragment(new TextRange(0, 0), new TextRange(0, 0), new TextRange(0, 1)));
  }

  public void testOneSideChange() {
    addRight(new TextRange(0, 2), new TextRange(0, 2));
    addLeft(new TextRange(1, 2), new TextRange(2, 3));
    CHECK.singleElement(finish(3, 2, 2),
                        fragment(new TextRange(0, 2), new TextRange(0, 1), new TextRange(0, 1)));
  }

  public void testNotAllignedConflict() {
    addLeft(new TextRange(1, 3), new TextRange(0, 2));
    addRight(new TextRange(2, 4), new TextRange(1, 3));
    CHECK.compareAll(new MergeFragment[]{
      fragment(new TextRange(0, 1), new TextRange(0, 2), new TextRange(0, 1)),
      fragment(new TextRange(2, 3), new TextRange(3, 4), new TextRange(2, 3))
    }, finish(3, 4, 3));
  }

  public void testBug() {
    addRight(new TextRange(0, 1), new TextRange(0, 1));
    addLeft(new TextRange(0, 2), new TextRange(0, 2));
    CHECK.compareAll(new MergeFragment[]{
      fragment(new TextRange(1, 3), new TextRange(1, 2), new TextRange(1, 1)),
    }, finish(3, 2, 1));
  }

  public void testMultiChanges() {
    addLeft(new TextRange(1, 8), new TextRange(1, 8));
    addRight(new TextRange(1, 2), new TextRange(0, 1));
    addRight(new TextRange(3, 4), new TextRange(1, 2));
    addRight(new TextRange(4, 5), new TextRange(3, 4));
    addRight(new TextRange(6, 7), new TextRange(5, 6));
    addLeft(new TextRange(9, 10), new TextRange(9, 10));
    CHECK.compareAll(new MergeFragment[]{
      fragment(new TextRange(0, 1), new TextRange(0, 1), new TextRange(0, 0)),
      fragment(new TextRange(2, 3), new TextRange(2, 3), new TextRange(1, 1)),
      fragment(new TextRange(4, 4), new TextRange(4, 4), new TextRange(2, 3)),
      fragment(new TextRange(5, 6), new TextRange(5, 6), new TextRange(4, 5)),
      fragment(new TextRange(7, 10), new TextRange(7, 10), new TextRange(6, 7))
    }, finish(10, 10, 7));
  }

  public void testNoIntersection() {
    addLeft(new TextRange(0, 1), new TextRange(0, 1));
    addRight(new TextRange(0, 2), new TextRange(0, 2));
    addLeft(new TextRange(3, 5), new TextRange(1, 3));
    addRight(new TextRange(4, 5), new TextRange(2, 3));
    CHECK.compareAll(new MergeFragment[]{
      fragment(new TextRange(1, 2), new TextRange(1, 4), new TextRange(1, 2))
    }, finish(3, 5, 3));
  }

  private MergeFragment fragment(TextRange left, TextRange base, TextRange right) {
    return new MergeFragment(left, base, right);
  }

  private void addRight(TextRange base, TextRange right) {
    myMergeBuilder.add(base, right, FragmentSide.SIDE2);
  }

  private void addLeft(TextRange base, TextRange left) {
    myMergeBuilder.add(base, left, FragmentSide.SIDE1);
  }

  private List<MergeFragment> finish(int left, int base, int right) {
    return myMergeBuilder.finish(left, base, right);
  }

  @Override
  protected void runTest() throws Throwable {
    try {
      super.runTest();
    }
    finally {
      if (IdeaLogger.ourErrorsOccurred != null) throw IdeaLogger.ourErrorsOccurred;
    }
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    IdeaLogger.ourErrorsOccurred = null;
  }
}
