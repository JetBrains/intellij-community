// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.ex.DocumentOp;
import com.intellij.openapi.editor.ex.DocumentPatch;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.ImmutableCharSequence;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OptimizedTextReplacementTest {
  private static final long MOD_STAMP = 42L;

  @Test
  public void commonAffixIsNarrowedAndOriginRangeIsKept() {
    OptimizedTextReplacement replacement = replacement("abcOLDxyz", 0, 9, "abcNEWxyz", false);
    assertFalse(replacement.perform());
    DocumentPatch patch = replacement.getPatch();
    assertEquals(3, patch.startOffset());
    assertEquals(6, patch.endOffset());
    assertEquals("NEW", patch.newFragment().toString());
    assertEquals(0, patch.originStartOffset());
    assertEquals(9, patch.originEndOffset());
    assertEquals(3, patch.moveOffset());
    assertEquals(MOD_STAMP, patch.newModStamp());
    assertFalse(patch.clearLineFlags());
  }

  @Test
  public void wholeTextReplacementReusesUntrimmedFragmentAsNewText() {
    ImmutableCharSequence fragment = CharArrayUtil.createImmutableCharSequence("abcNEWxyz");
    OptimizedTextReplacement replacement = replacement("abcOLDxyz", 0, 9, fragment, true);
    assertFalse(replacement.perform());
    DocumentPatch patch = replacement.getPatch();
    assertEquals(0, patch.startOffset());
    assertEquals(9, patch.endOffset());
    assertSame(fragment, patch.newFragment());
    assertEquals(0, patch.originStartOffset());
    assertEquals(9, patch.originEndOffset());
    assertTrue(patch.clearLineFlags());
    // the event stays narrowed even though the patch covers the full range
    assertEquals(3, replacement.getStartOffset());
    assertEquals(6, replacement.getEndOffset());
    assertEquals(3, patch.moveOffset());
  }

  @Test
  public void wholeTextReplacementOpsCarryNoMoveOffset() {
    // The patch keeps the move offset of the narrowed event, but a replace is not half of a move.
    ImmutableCharSequence fragment = CharArrayUtil.createImmutableCharSequence("abcNEWxyz");
    OptimizedTextReplacement replacement = replacement("abcOLDxyz", 0, 9, fragment, true);
    assertFalse(replacement.perform());
    List<DocumentOp> ops = replacement.getPatch().ops();
    assertEquals(DocumentOp.deleteOp(0, 9), ops.get(0));
    assertEquals(DocumentOp.insertOp(0, fragment), ops.get(1));
  }

  @Test
  public void partialRangeRequestWithWholeTextFlagFallsBackToNarrowedPatch() {
    ImmutableCharSequence fragment = CharArrayUtil.createImmutableCharSequence("NEW");
    OptimizedTextReplacement replacement = replacement("abcOLDxyz", 3, 6, fragment, true);
    assertFalse(replacement.perform());
    DocumentPatch patch = replacement.getPatch();
    // the fragment is not the whole text here, so it must not be widened to the full range
    assertEquals(3, patch.startOffset());
    assertEquals(6, patch.endOffset());
    assertEquals("NEW", patch.newFragment().toString());
    assertEquals(3, patch.originStartOffset());
    assertEquals(6, patch.originEndOffset());
    assertTrue(patch.clearLineFlags());
  }

  @Test
  public void wholeTextReplacementOnEmptyDocumentClearsLineFlags() {
    ImmutableCharSequence fragment = CharArrayUtil.createImmutableCharSequence("abc");
    OptimizedTextReplacement replacement = replacement("", 0, 0, fragment, true);
    assertFalse(replacement.perform());
    DocumentPatch patch = replacement.getPatch();
    assertEquals(0, patch.startOffset());
    assertEquals(0, patch.endOffset());
    assertSame(fragment, patch.newFragment());
    assertTrue(patch.clearLineFlags());
  }

  @Test
  public void insertionIntoEmptyDocumentDoesNotClearLineFlags() {
    OptimizedTextReplacement replacement = replacement("", 0, 0, "abc", false);
    assertFalse(replacement.perform());
    DocumentPatch patch = replacement.getPatch();
    assertEquals(0, patch.startOffset());
    assertEquals(0, patch.endOffset());
    assertEquals("abc", patch.newFragment().toString());
    assertFalse(patch.clearLineFlags());
  }

  @Test
  public void moveInsertionKeepsMoveOffsetInPatch() {
    OptimizedTextReplacement replacement = new OptimizedTextReplacement(
      CharArrayUtil.createImmutableCharSequence("abcdef"),
      1,
      1,
      /* initialMoveOffset = */ 4,
      "cd",
      false,
      MOD_STAMP
    );
    assertFalse(replacement.perform());
    DocumentPatch patch = replacement.getPatch();
    assertEquals(1, patch.startOffset());
    assertEquals(1, patch.endOffset());
    assertEquals(1, patch.originStartOffset());
    assertEquals(1, patch.originEndOffset());
    assertEquals(4, patch.moveOffset());
  }

  @Test
  public void replacementWithSameContentIsNoOp() {
    OptimizedTextReplacement replacement = replacement("abcdef", 2, 4, "cd", false);
    assertTrue(replacement.perform());
  }

  private static OptimizedTextReplacement replacement(
    String wholeText,
    int startOffset,
    int endOffset,
    CharSequence newFragment,
    boolean wholeTextReplaced
  ) {
    return new OptimizedTextReplacement(
      CharArrayUtil.createImmutableCharSequence(wholeText),
      startOffset,
      endOffset,
      startOffset,
      newFragment,
      wholeTextReplaced,
      MOD_STAMP
    );
  }
}
