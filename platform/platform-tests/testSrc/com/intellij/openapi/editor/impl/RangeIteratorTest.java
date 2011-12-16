package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.tree.IElementType;
import gnu.trove.Equality;
import junit.framework.TestCase;

public class RangeIteratorTest extends TestCase {
  private static final Equality ALL_EQUAL = new DummyEquality(true);
  private static final Equality ALL_DIFFERENT = new DummyEquality(false);
  private static final RangeIterator.Gaps NO_GAPS = new RangeIterator.Gaps() {
    @Override
    public boolean isGapAt(int offset) {
      return false;
    }
  };
  private static final RangeIterator.Gaps MY_GAPS = new RangeIterator.Gaps() {
    @Override
    public boolean isGapAt(int offset) {
      return 3 <= offset && offset < 6;
    }
  };
  private static final Condition<TextAttributes> ANY = Conditions.alwaysTrue();

  public void testExpanded() {
    RangeIterator iterator = createIterator(ALL_EQUAL, NO_GAPS);
    checkPosition(iterator, 2, 4);
    iterator.advance();
    checkPosition(iterator, 5, 9);
    assertTrue(iterator.atEnd());
  }

  public void testCollapsed() {
    RangeIterator iterator = createIterator(ALL_EQUAL, MY_GAPS);
    checkPosition(iterator, 2, 3);
    iterator.advance();
    checkPosition(iterator, 6, 9);
    assertTrue(iterator.atEnd());
  }

  public void testDifferentExpanded() {
    RangeIterator iterator = createIterator(ALL_DIFFERENT, NO_GAPS);
    checkPosition(iterator, 2, 4);
    iterator.advance();
    checkPosition(iterator, 5, 7);
    assertFalse(iterator.atEnd());
    iterator.advance();
    checkPosition(iterator, 7, 9);
    assertTrue(iterator.atEnd());
  }

  public void testEndInFolding() {
    RangeIterator iterator = createIterator(ALL_EQUAL, new int[]{1}, new int[]{5}, MY_GAPS);
    checkPosition(iterator, 1, 3);
    assertTrue(iterator.atEnd());
  }

  public void testDividedByFolding() {
    RangeIterator iterator = createIterator(ALL_EQUAL, new int[]{1}, new int[]{9}, MY_GAPS);
    checkPosition(iterator, 1, 3);
    iterator.advance();
    checkPosition(iterator, 6, 9);
    assertTrue(iterator.atEnd());
  }

  public void testAllFolded() {
    RangeIterator iterator = new RangeIterator(MY_GAPS, ALL_EQUAL,
                                               new MyHighlighterIterator(new int[]{4}, new int[]{5}), ANY);
    iterator.init(new TextRange(0, Integer.MAX_VALUE));
    assertTrue(iterator.atEnd());
  }

  private void checkPosition(RangeIterator iterator, int start, int end) {
    assertEquals(start, iterator.getStart());
    assertEquals(end, iterator.getEnd());
  }

  private RangeIterator createIterator(Equality equality, RangeIterator.Gaps gaps) {
    return createIterator(equality, new int[]{2, 5, 7}, new int[]{4, 7, 9}, gaps);
  }

  private RangeIterator createIterator(Equality equality, int[] starts, int[] ends, RangeIterator.Gaps gaps) {
    RangeIterator iterator = new RangeIterator(gaps, equality,
                                               new MyHighlighterIterator(starts, ends), ANY);
    iterator.init(new TextRange(0, Integer.MAX_VALUE));
    iterator.advance();
    return iterator;
  }

  private static class MyHighlighterIterator implements HighlighterIterator {
    private final int[] myStarts;
    private final int[] myEnds;
    private int myIndex = 0;

    public MyHighlighterIterator(int[] starts, int[] ends) {
      myStarts = starts;
      myEnds = ends;
    }

    @Override
    public TextAttributes getTextAttributes() {
      return null;
    }

    @Override
    public IElementType getTokenType() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void advance() {
      myIndex++;
    }

    @Override
    public int getEnd() {
      return myEnds[myIndex];
    }

    @Override
    public int getStart() {
      return myStarts[myIndex];
    }

    @Override
    public boolean atEnd() {
      return myIndex == myStarts.length;
    }

    @Override
    public Document getDocument() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void retreat() {
      throw new UnsupportedOperationException();
    }
  }

  private static class DummyEquality implements Equality {
    private final boolean myResult;

    public DummyEquality(boolean result) {
      myResult = result;
    }

    @Override
    public boolean equals(Object o1, Object o2) {
      return myResult;
    }
  }
}
