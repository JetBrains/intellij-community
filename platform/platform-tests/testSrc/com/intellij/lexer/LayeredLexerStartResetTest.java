// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

/**
 * Test for {@link LayeredLexer#start} clearing the layer-end-gap transient state.
 * <p>
 * {@code LayeredLexer.advance()} enters a "layer end gap" state when a self-stopping sub-lexer
 * finishes with {@code sub.getTokenEnd() < myBaseTokenEnd} — see {@code advance()}'s gap branch
 * which sets {@code myLayerLeftPart} to the sub-lexer's end and parks the lexer there until the
 * next {@code advance()}. If a caller stops driving the lexer at exactly that point and then
 * calls {@code start(...)} on a new buffer, {@code start} must reset the transient gap-state
 * fields ({@code myLayerLeftPart}, {@code myBaseTokenEnd}, {@code myCurrentBaseTokenType});
 * otherwise the next reported token carries stale offsets and an element type from the previous
 * buffer.
 */
public class LayeredLexerStartResetTest extends TestCase {

  private static final IElementType OUTER = new IElementType("OUTER", null);
  private static final IElementType INNER = new IElementType("INNER", null);

  public void testSecondStartClearsLayerEndGapState() {
    LayeredLexer lexer = new LayeredLexer(new OuterLexer());
    lexer.registerSelfStoppingLayer(new OneCharLayerLexer(),
                                    new IElementType[]{OUTER},
                                    IElementType.EMPTY_ARRAY);

    // Buffer 1 — sub-lexer reports a single INNER token, then null while the base token still
    // covers more characters. That puts the lexer into the layer-end-gap state on the next
    // advance().
    lexer.start("AAAA");
    assertEquals(INNER, lexer.getTokenType());
    assertEquals(0, lexer.getTokenStart());
    assertEquals(1, lexer.getTokenEnd());

    lexer.advance();
    // Now in gap state: myLayerLeftPart = 1, myBaseTokenEnd = 4, myCurrentBaseTokenType = OUTER.
    assertEquals(OUTER, lexer.getTokenType());
    assertEquals(1, lexer.getTokenStart());
    assertEquals(4, lexer.getTokenEnd());

    // Second start() — without consuming the gap. start() must clear the transient fields so the
    // new buffer's lex begins from a clean state. Without the reset, getTokenType / getTokenStart
    // / getTokenEnd would return the stale (OUTER, 1, 4) triple from buffer 1.
    lexer.start("BBBB");

    assertEquals(INNER, lexer.getTokenType());
    assertEquals(0, lexer.getTokenStart());
    assertEquals(1, lexer.getTokenEnd());
  }

  /** Base lexer: reports the entire buffer as one OUTER token. */
  private static final class OuterLexer extends LexerBase {
    private CharSequence buffer;
    private int end;
    private int pos;

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
      this.buffer = buffer;
      this.end = endOffset;
      this.pos = startOffset;
    }

    @Override public int getState() { return 0; }
    @Override public IElementType getTokenType() { return pos < end ? OUTER : null; }
    @Override public int getTokenStart() { return pos; }
    @Override public int getTokenEnd() { return end; }
    @Override public void advance() { pos = end; }
    @Override public @NotNull CharSequence getBufferSequence() { return buffer; }
    @Override public int getBufferEnd() { return end; }
  }

  /**
   * Sub-lexer: reports a single INNER token covering only the first character of its assigned
   * sub-buffer, then null. {@link #getTokenEnd()} keeps returning {@code start + 1} even after
   * {@link #advance()}, so the layered lexer's gap check sees
   * {@code sub.tokenEnd < myBaseTokenEnd} whenever the sub-buffer is longer than one character —
   * which is the precondition for the layer-end-gap branch.
   */
  private static final class OneCharLayerLexer extends LexerBase {
    private CharSequence buffer;
    private int start;
    private int end;
    private boolean advanced;

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
      this.buffer = buffer;
      this.start = startOffset;
      this.end = endOffset;
      this.advanced = false;
    }

    @Override public int getState() { return 0; }
    @Override public IElementType getTokenType() { return advanced || start >= end ? null : INNER; }
    @Override public int getTokenStart() { return start; }
    @Override public int getTokenEnd() { return Math.min(start + 1, end); }
    @Override public void advance() { advanced = true; }
    @Override public @NotNull CharSequence getBufferSequence() { return buffer; }
    @Override public int getBufferEnd() { return end; }
  }
}
