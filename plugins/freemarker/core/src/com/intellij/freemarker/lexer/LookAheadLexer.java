/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.freemarker.lexer;

import com.intellij.lexer.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ImmutableUserMap;

import java.util.List;

/**
 * @author peter
 */
public abstract class LookAheadLexer extends LexerBase{
  private int myLastOffset;
  private int myLastState;

  private final Lexer myBaseLexer;
  private int myTokenStart;
  private List<IElementType> myTypeCache = new SmartList<IElementType>();
  private List<Integer> myEndOffsetCache = new SmartList<Integer>();

  public LookAheadLexer(final Lexer baseLexer) {
    myBaseLexer = baseLexer;
  }

  protected void addToken(IElementType type) {
    addToken(myBaseLexer.getTokenEnd(), type);
  }

  protected void addToken(int endOffset, IElementType type) {
    myTypeCache.add(type);
    myEndOffsetCache.add(endOffset);
  }

  protected void lookAhead(Lexer baseLexer) {
    addToken(baseLexer.getTokenType());
    baseLexer.advance();
  }

  public Lexer getBaseLexer() {
    return myBaseLexer;
  }

  public void advance() {
    if (!myTypeCache.isEmpty()) {
      myTypeCache.remove(0);
      myTokenStart = myEndOffsetCache.remove(0);
    }
    if (myTypeCache.isEmpty()) {
      doLookAhead();
    }
  }

  private void doLookAhead() {
    myLastOffset = myTokenStart;
    myLastState = myBaseLexer.getState();

    lookAhead(myBaseLexer);
    assert !myTypeCache.isEmpty();
  }

  public char[] getBuffer() {
    return myBaseLexer.getBuffer();
  }

  public int getBufferEnd() {
    return myBaseLexer.getBufferEnd();
  }

  public int getState() {
    int offset = myTypeCache.size() - 1;
    return myLastState | (offset << 16);
  }

  public int getTokenEnd() {
    return myEndOffsetCache.get(0);
  }

  public int getTokenStart() {
    return myTokenStart;
  }

  public LookAheadLexerPosition getCurrentPosition() {
    return new LookAheadLexerPosition(ImmutableUserMap.EMPTY);
  }

  public final void restore(final LexerPosition _position) {
    restore((LookAheadLexerPosition) _position);
  }

  protected void restore(final LookAheadLexerPosition position) {
    start(myBaseLexer.getBuffer(), position.lastOffset, myBaseLexer.getBufferEnd(), position.lastState);
    for (int i = 0; i < position.advanceCount; i++) {
      advance();
    }
  }

  public IElementType getTokenType() {
    return myTypeCache.get(0);
  }

  public void start(final char[] buffer, final int startOffset, final int endOffset, final int initialState) {
    myBaseLexer.start(buffer, startOffset, endOffset, initialState & 0xFFFF);
    myTokenStart = startOffset;
    myTypeCache.clear();
    myEndOffsetCache.clear();
    doLookAhead();
  }

  protected class LookAheadLexerPosition implements LexerPosition {
    final int lastOffset = myLastOffset;
    final int lastState = myLastState;
    final int tokenStart = myTokenStart;
    final int curState = getState();
    final int advanceCount = myTypeCache.size() - 1;
    final ImmutableUserMap customMap;

    public LookAheadLexerPosition(final ImmutableUserMap map) {
      customMap = map;
    }

    public ImmutableUserMap getCustomMap() {
      return customMap;
    }

    public int getOffset() {
      return tokenStart;
    }

    public int getState() {
      return lastState;
    }
  }
}
