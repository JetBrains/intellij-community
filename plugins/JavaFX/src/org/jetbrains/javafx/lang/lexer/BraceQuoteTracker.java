package org.jetbrains.javafx.lang.lexer;

import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
class BraceQuoteTracker {
  public static final BraceQuoteTracker NULL_BQT = new BraceQuoteTracker(null, -1, false);

  private final int myPreviousState;
  private boolean myPercentIsFormat;
  private int myBraceDepth;
  private final BraceQuoteTracker myNext;

  public BraceQuoteTracker(BraceQuoteTracker previous, int previousState, boolean percentIsFormat) {
    myPreviousState = previousState;
    myPercentIsFormat = percentIsFormat;
    myBraceDepth = 1;
    myNext = previous;
  }

  public void enterBrace() {
    if (inBraceQuote()) {
      ++myBraceDepth;
    }
  }

  @Nullable
  public BraceQuoteTracker enterBrace(int state, boolean percentIsFormat) {
    return new BraceQuoteTracker(this, state, percentIsFormat); // push
  }

  public int leaveBrace() {
    if (inBraceQuote() && --myBraceDepth == 0) {
      return myPreviousState;
    }
    return -1;
  }

  public BraceQuoteTracker leaveQuote() {
    assert (inBraceQuote() && myBraceDepth == 0);
    return myNext; // pop
  }

  public boolean inBraceQuote() {
    return this != NULL_BQT;
  }
}
