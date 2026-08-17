// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.mock.MockDocument;
import com.intellij.openapi.editor.ex.DocumentText;
import com.intellij.openapi.editor.ex.DocumentTextOp;
import com.intellij.openapi.editor.ex.DocumentTextPatch;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.DocumentInternalUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.PropertyChecker;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves {@link ModifiedLineSet} tracks line-modification exactly like {@link LineSet} does, by driving both
 * with the identical sequence of operations (built from the same initial text, via the same {@link DocumentText}
 * chain {@link ModifiedLineSet#update} relies on) and comparing observable behavior -- including exceptions --
 * after every step.
 */
@TestApplication
public class ModifiedLineSetTest {

  // ---- Ported / hand-derived regression cases ----

  @Test
  public void testSlashRIssues() {
    checkSingleUpdate("\n\r", 0, 0, "");
    checkSingleUpdate("\r\n", 0, 1, "");
    checkSingleUpdate("\r", 0, 0, "\r");
  }

  @Test
  public void testClearSingleLineEnd() {
    checkSingleUpdate("\n", 0, 1, "");
  }

  @Test
  public void testWholeTextDeleteToEmpty() {
    checkSingleUpdate("ab\ncd", 0, 5, "");
    checkSingleUpdate("a\n", 0, 2, "");
  }

  @Test
  public void testTypingRightAfterTrailingNewline() {
    checkSingleUpdate("ab\n", 3, 3, "c");
  }

  @Test
  public void testDeleteThroughEndOfTextWithTrailingSeparator() {
    checkSingleUpdate("ab\n", 1, 3, "");
    checkSingleUpdate("ab\ncd\nef", 3, 8, ""); // delete-to-end starting exactly at a line boundary
    checkSingleUpdate("ab\ncd\nef", 4, 8, ""); // delete-to-end starting mid-line
  }

  @Test
  public void testMultiLineToMultiLineReplacement() {
    checkSingleUpdate("aa\nbb\ncc\ndd", 3, 8, "x\ny\nz");
    checkSingleUpdate("aa\nbb\ncc\ndd\nee", 3, 8, "x\ny\nz"); // with an untouched tail line, to exercise the suffix copy
  }

  private static void checkSingleUpdate(String initialText, int start, int end, String replacement) {
    DocumentText text = documentText(initialText);
    LineSet lineSet = LineSet.createLineSet(text.cachedChars());
    ModifiedLineSet modifiedLineSet = ModifiedLineSet.create(text.cachedChars());

    String context = "checkSingleUpdate(\"" + StringUtil.escapeStringCharacters(initialText) + "\", " +
                      start + ", " + end + ", \"" + StringUtil.escapeStringCharacters(replacement) + "\")";
    lineSet = lineSet.update(text.chars(), start, end, replacement);
    modifiedLineSet = modifiedLineSet.update(text, start, end, replacement);
    assertLinesAgree(lineSet, modifiedLineSet, context);
  }

  // ---- Deliberate, named branch-by-branch coverage (verified against actual code coverage, not just fuzzing) ----

  @Test
  public void testCreateFreshIsAllUnmodified() {
    // create(text) (markModified=false) must never mark a line modified, regardless of text shape.
    for (String text : new String[]{"", "abc", "a\nb\nc", "a\nb\nc\n"}) {
      DocumentText documentText = documentText(text);
      LineSet lineSet = LineSet.createLineSet(documentText.cachedChars());
      ModifiedLineSet modifiedLineSet = ModifiedLineSet.create(documentText.cachedChars());
      String label = "fresh create() for \"" + StringUtil.escapeStringCharacters(text) + "\"";
      assertLinesAgree(lineSet, modifiedLineSet, label);
      for (int i = 0; i < lineSet.getLineCount(); i++) {
        assertFalse(lineSet.isModified(i), label + " -- LineSet line " + i);
        assertFalse(modifiedLineSet.isModified(i), label + " -- ModifiedLineSet line " + i);
      }
    }
  }

  @Test
  public void testEditingEmptyOldTextMarksEverythingModified() {
    // update()'s oldText.length()==0 shortcut: create(replacement, markModified=true) -- every resulting
    // line must come back modified, mirroring LineSet.update's identical createLineSet(replacement, true).
    checkSingleUpdate("", 0, 0, "a\nb\nc");
    DocumentText text = documentText("");
    ModifiedLineSet modifiedLineSet = ModifiedLineSet.create(text.cachedChars()).update(text, 0, 0, "a\nb\nc");
    for (int i = 0; i < modifiedLineSet.getLineCount(); i++) {
      assertTrue(modifiedLineSet.isModified(i), "line " + i + " should be modified after editing from empty");
    }
  }

  @Test
  public void testCarriageReturnMergeAdjustmentBranches() {
    // Left adjustment (update()'s first `if`), via its first disjunct: the edit starts exactly between an
    // existing \r and \n, which the merge logic re-attaches the \r to rather than splitting the pair.
    checkSingleUpdate("a\r\nb", 2, 2, "X");
    // Left adjustment via its second disjunct: the replacement starts with \n, which would newly pair up
    // with a preceding lone \r if not merged.
    checkSingleUpdate("a\rb", 2, 2, "\nX");
    // Right adjustment (update()'s second `if`), via its first disjunct: the edit ends exactly at an
    // existing \r\n pair's \n, so the \r immediately before it must be pulled into the change too.
    checkSingleUpdate("ab\r\ncd", 1, 3, "X");
    // Right adjustment via its second disjunct: the replacement ends with \r, which would newly pair up
    // with a following \n if not merged.
    checkSingleUpdate("ab\ncd", 1, 2, "X\r");
    // Neither adjustment applies: an ordinary edit not adjacent to any \r -- the common case, exercised
    // pervasively elsewhere too, named here for completeness of this branch group.
    checkSingleUpdate("ab\ncd", 1, 2, "X");
  }

  @Test
  public void testSingleLineChangeBranches() {
    // start==0 && end==length && replacement=="": the explicit early-return-false special case.
    checkSingleUpdate("abc", 0, 3, "");
    // Different lines, not the whole-delete special case above: startLine != endLine -> false.
    checkSingleUpdate("aa\nbb\ncc", 1, 5, "X");
    // Same line, but the replacement itself contains a line break -> disqualified, false.
    checkSingleUpdate("abc", 1, 1, "\n");
    // Same line, no line break, not the virtual trailing line -> true, single-line fast path.
    checkSingleUpdate("abc", 1, 2, "X");
    // Same line, no line break, but it IS the virtual trailing line -> false (see testTypingRightAfterTrailingNewline).
  }

  @Test
  public void testUpdateInsideOneLinePositions() {
    // The single-line fast path at the first line, a middle line, and the last real line (no separator
    // after it, so it's real, not virtual).
    checkSingleUpdate("aa\nbb\ncc", 0, 1, "X");
    checkSingleUpdate("aa\nbb\ncc", 4, 5, "X");
    checkSingleUpdate("aa\nbb\ncc", 7, 8, "X");
  }

  @Test
  public void testAddStartLineFalse() {
    // genericUpdate's addStartLine = (startOffset>startLineStart) || (patch has lines) || (endOffset<oldLength).
    // Deleting from a line's exact start through the true end of text with an empty replacement makes all
    // three false at once.
    checkSingleUpdate("aa\nbb\ncc", 3, 8, "");
  }

  @Test
  public void testAddEndLineBranches() {
    // Same base text/range throughout (replacing offsets [3,8) = "bb\ncc"), varying only what determines
    // addEndLine = (endOffset<oldLength) && (replacement non-empty) && (replacement's last line has a separator).
    String text = "aa\nbb\ncc\ndd\n"; // trailing separator, so the "unchanged, carried over" case below is non-trivial
    checkSingleUpdate(text, 3, 8, "x\n");  // all three true -> addEndLine=true
    checkSingleUpdate(text, 3, 11, "x\n"); // endOffset==oldLength -> addEndLine=false
    checkSingleUpdate(text, 3, 8, "");     // replacement empty -> addEndLine=false
    checkSingleUpdate(text, 3, 8, "x\ny"); // replacement's last line "y" has no separator -> addEndLine=false
    //                                        ("x\ny" also has 2 tokenized lines, exercising interiorCount==1)
  }

  @Test
  public void testNewLastLineHasSeparatorBranches() {
    // endOffset < oldLength: the tail survives verbatim, so the trailing separator is carried over unchanged
    // (both when it's present and when it's absent).
    checkSingleUpdate("aa\nbb\ncc\ndd\n", 3, 8, "x\ny\nz"); // carried over: true (base text ends with \n)
    checkSingleUpdate("aa\nbb\ncc\ndd", 3, 8, "x\ny\nz");   // carried over: false (base text has no trailing separator)

    // endOffset == oldLength, replacement non-empty, ends with a separator -> true.
    checkSingleUpdate("ab", 2, 2, "c\n");
    // endOffset == oldLength, replacement non-empty, does NOT end with a separator -> false.
    // (see testTypingRightAfterTrailingNewline for this one, same shape)

    // endOffset == oldLength, replacement empty, startOffset exactly at a non-first line boundary -> true.
    // endOffset == oldLength, replacement empty, startOffset mid-line -> false.
    // (see testDeleteThroughEndOfTextWithTrailingSeparator for both)
    // endOffset == oldLength, replacement empty, startLine==0 (whole-text delete) -> false despite
    // startOffset==startLineStart, because startLine>0 is also required.
    // (see testWholeTextDeleteToEmpty)
  }

  @Test
  public void testIsModifiedIsAlwaysFalseForTheVirtualTrailingLine() {
    // "ab\n" -> 1 real line + a synthetic trailing empty line (index 1). isModified must short-circuit to
    // false for the virtual line no matter what -- even right after an edit on the real line beside it.
    DocumentText text = documentText("ab\n");
    LineSet lineSet = LineSet.createLineSet(text.cachedChars());
    ModifiedLineSet modifiedLineSet = ModifiedLineSet.create(text.cachedChars());
    assertEquals(2, lineSet.getLineCount());
    assertFalse(lineSet.isModified(1));
    assertFalse(modifiedLineSet.isModified(1));

    lineSet = lineSet.update(text.chars(), 0, 0, "X");
    modifiedLineSet = modifiedLineSet.update(text, 0, 0, "X");
    assertLinesAgree(lineSet, modifiedLineSet, "after editing the real line beside the virtual one");
    assertFalse(lineSet.isModified(1));
  }

  @Test
  public void testSetModifiedNoOpCases() {
    DocumentText text = documentText("ab\ncd\n"); // 2 real lines + a virtual trailing line at index 2
    LineSet lineSet = LineSet.createLineSet(text.cachedChars());
    ModifiedLineSet modifiedLineSet = ModifiedLineSet.create(text.cachedChars());
    int virtualLine = lineSet.getLineCount() - 1;
    assertEquals(2, virtualLine);

    // empty indices: always a no-op, same instance back.
    assertSame(lineSet, lineSet.setModified(new IntArrayList(0)));
    assertSame(modifiedLineSet, modifiedLineSet.setModified(new IntArrayList(0)));

    // singleton targeting the virtual line: a no-op (isLastEmptyLine short-circuits before any mutation).
    IntList virtualSingleton = new IntArrayList(new int[]{virtualLine});
    assertSame(lineSet, lineSet.setModified(virtualSingleton));
    assertSame(modifiedLineSet, modifiedLineSet.setModified(virtualSingleton));

    // singleton targeting an already-modified real line: a no-op.
    IntList line0 = new IntArrayList(new int[]{0});
    lineSet = lineSet.setModified(line0);
    modifiedLineSet = modifiedLineSet.setModified(line0);
    assertLinesAgree(lineSet, modifiedLineSet, "after marking line 0 modified");
    assertSame(lineSet, lineSet.setModified(line0));
    assertSame(modifiedLineSet, modifiedLineSet.setModified(line0));

    // singleton targeting a fresh, real, not-yet-modified line: actually mutates.
    IntList line1 = new IntArrayList(new int[]{1});
    LineSet lineSetAfter = lineSet.setModified(line1);
    ModifiedLineSet modifiedLineSetAfter = modifiedLineSet.setModified(line1);
    assertNotSame(lineSet, lineSetAfter);
    assertNotSame(modifiedLineSet, modifiedLineSetAfter);
    assertLinesAgree(lineSetAfter, modifiedLineSetAfter, "after marking line 1 modified");
    assertTrue(lineSetAfter.isModified(1));
  }

  @Test
  public void testSetModifiedMultipleLinesAtOnce() {
    DocumentText text = documentText("a\nb\nc\nd\ne");
    LineSet lineSet = LineSet.createLineSet(text.cachedChars());
    ModifiedLineSet modifiedLineSet = ModifiedLineSet.create(text.cachedChars());
    IntList indices = new IntArrayList(new int[]{0, 2, 4});
    lineSet = lineSet.setModified(indices);
    modifiedLineSet = modifiedLineSet.setModified(indices);
    assertLinesAgree(lineSet, modifiedLineSet, "setModified([0, 2, 4])");
    assertTrue(lineSet.isModified(0));
    assertFalse(lineSet.isModified(1));
    assertTrue(lineSet.isModified(2));
    assertFalse(lineSet.isModified(3));
    assertTrue(lineSet.isModified(4));
  }

  @Test
  public void testClearModificationFlagsStartAfterEndThrows() {
    DocumentText text = documentText("a\nb\nc");
    LineSet lineSet = LineSet.createLineSet(text.cachedChars());
    ModifiedLineSet modifiedLineSet = ModifiedLineSet.create(text.cachedChars());
    compareOutcome(() -> lineSet.clearModificationFlags(2, 1), () -> modifiedLineSet.clearModificationFlags(2, 1),
                   "clearModificationFlags(2, 1) -- startLine > endLine");
  }

  @Test
  public void testClearModificationFlagsEmptyDocumentBypass() {
    DocumentText text = documentText("");
    LineSet lineSet = LineSet.createLineSet(text.cachedChars());
    ModifiedLineSet modifiedLineSet = ModifiedLineSet.create(text.cachedChars());
    assertEquals(0, lineSet.getLineCount());

    // lineCount==0 && startLine==0 && endLine==0: bypass, no-op.
    assertSame(lineSet, lineSet.clearModificationFlags(0, 0));
    assertSame(modifiedLineSet, modifiedLineSet.clearModificationFlags(0, 0));

    // lineCount==0 && startLine==0 && endLine==MAX_VALUE: bypass, no-op.
    assertSame(lineSet, lineSet.clearModificationFlags(0, Integer.MAX_VALUE));
    assertSame(modifiedLineSet, modifiedLineSet.clearModificationFlags(0, Integer.MAX_VALUE));

    // lineCount==0 && startLine==0, but endLine is neither special value: bypass does NOT apply, falls
    // through to checkLineIndex(startLine=0), which throws since an empty document has no valid line index.
    compareOutcome(() -> lineSet.clearModificationFlags(0, 1), () -> modifiedLineSet.clearModificationFlags(0, 1),
                   "clearModificationFlags(0, 1) on an empty document");

    // lineCount==0 but startLine != 0: the bypass's own precondition fails, straight to checkLineIndex, throws.
    compareOutcome(() -> lineSet.clearModificationFlags(1, Integer.MAX_VALUE),
                   () -> modifiedLineSet.clearModificationFlags(1, Integer.MAX_VALUE),
                   "clearModificationFlags(1, MAX_VALUE) on an empty document");
  }

  @Test
  public void testClearModificationFlagsExcludesVirtualLine() {
    // "ab\n" -> 1 real line + a virtual trailing line (index 1). Clearing through MAX_VALUE must decrement
    // endLine past the virtual line before the actual Arrays.fill, touching only the real line.
    DocumentText text = documentText("ab\n");
    LineSet lineSet = LineSet.createLineSet(text.cachedChars());
    ModifiedLineSet modifiedLineSet = ModifiedLineSet.create(text.cachedChars());
    lineSet = lineSet.update(text.chars(), 0, 0, "X");
    modifiedLineSet = modifiedLineSet.update(text, 0, 0, "X");
    assertTrue(lineSet.isModified(0));

    lineSet = lineSet.clearModificationFlags(0, Integer.MAX_VALUE);
    modifiedLineSet = modifiedLineSet.clearModificationFlags(0, Integer.MAX_VALUE);
    assertLinesAgree(lineSet, modifiedLineSet, "clearModificationFlags(0, MAX_VALUE) with a virtual trailing line");
    assertFalse(lineSet.isModified(0));

    // After the virtual-line decrement, clearing [1, MAX_VALUE) becomes an empty range (startLine >=
    // endLine) -- a no-op, same instance back.
    assertSame(lineSet, lineSet.clearModificationFlags(1, Integer.MAX_VALUE));
    assertSame(modifiedLineSet, modifiedLineSet.clearModificationFlags(1, Integer.MAX_VALUE));
  }

  // ---- Multi-step fuzzing: a random sequence of edits, compared after every step ----

  @Test
  public void testFuzzMultiStepUpdate() {
    PropertyChecker.customized()
      .withIterationCount(1_000)
      .checkScenarios(() -> env -> {
        Generator<String> strings = Generator.stringsOf("a \n\r");
        String initialText = env.generateValue(strings, null);
        int steps = env.generateValue(Generator.integers(1, 6), null);

        DocumentText text = documentText(initialText);
        LineSet lineSet = LineSet.createLineSet(text.cachedChars());
        ModifiedLineSet modifiedLineSet = ModifiedLineSet.create(text.cachedChars());
        StringBuilder trace = new StringBuilder(
          "checkFuzzMultiStepUpdate(\"" + StringUtil.escapeStringCharacters(initialText) + "\")");
        assertLinesAgree(lineSet, modifiedLineSet, trace + " -- initial state");

        long modStamp = 0;
        for (int step = 0; step < steps; step++) {
          int start = env.generateValue(Generator.integers(0, text.length()), null);
          int end = env.generateValue(Generator.integers(start, text.length()), null);
          String replacement = env.generateValue(strings, null);
          trace.append(" .update(").append(start).append(", ").append(end).append(", \"")
               .append(StringUtil.escapeStringCharacters(replacement)).append("\")");
          env.logMessage(trace.toString());

          lineSet = lineSet.update(text.chars(), start, end, replacement);
          modifiedLineSet = modifiedLineSet.update(text, start, end, replacement);
          text = applyPatch(text, DocumentTextPatch.simple(start, end, replacement, ++modStamp, false));

          assertLinesAgree(lineSet, modifiedLineSet, trace.toString());
        }
      });
  }

  // ---- setModified / clearModificationFlags fuzzing, interleaved with edits ----

  @Test
  public void testFuzzInterleavedClearAndSetModified() {
    PropertyChecker.customized()
      .withIterationCount(1_000)
      .checkScenarios(() -> env -> {
        Generator<String> strings = Generator.stringsOf("a \n\r");
        String initialText = env.generateValue(strings, null);
        int steps = env.generateValue(Generator.integers(1, 6), null);

        DocumentText text = documentText(initialText);
        LineSet lineSet = LineSet.createLineSet(text.cachedChars());
        ModifiedLineSet modifiedLineSet = ModifiedLineSet.create(text.cachedChars());
        StringBuilder trace = new StringBuilder(
          "checkFuzzInterleaved(\"" + StringUtil.escapeStringCharacters(initialText) + "\")");

        long modStamp = 0;
        for (int step = 0; step < steps; step++) {
          int start = env.generateValue(Generator.integers(0, text.length()), null);
          int end = env.generateValue(Generator.integers(start, text.length()), null);
          String replacement = env.generateValue(strings, null);
          trace.append(" .update(").append(start).append(", ").append(end).append(", \"")
               .append(StringUtil.escapeStringCharacters(replacement)).append("\")");

          lineSet = lineSet.update(text.chars(), start, end, replacement);
          modifiedLineSet = modifiedLineSet.update(text, start, end, replacement);
          text = applyPatch(text, DocumentTextPatch.simple(start, end, replacement, ++modStamp, false));
          env.logMessage(trace.toString());
          assertLinesAgree(lineSet, modifiedLineSet, trace.toString());

          int lineCount = lineSet.getLineCount();
          if (lineCount > 0 && env.generateValue(Generator.integers(0, 1), null) == 1) {
            int clearStart = env.generateValue(Generator.integers(0, lineCount - 1), null);
            // endLine==0 is a legitimate throw on any non-empty document (only the lineCount==0 bypass allows
            // it) -- that's exercised deliberately by testBoundaryIndexExceptionParity, not by this state-
            // transition fuzz test, so keep endLine >= 1 here.
            int clearEnd = env.generateValue(Generator.integers(Math.max(clearStart, 1), lineCount), null);
            trace.append(" .clearModificationFlags(").append(clearStart).append(", ").append(clearEnd).append(")");

            lineSet = lineSet.clearModificationFlags(clearStart, clearEnd);
            modifiedLineSet = modifiedLineSet.clearModificationFlags(clearStart, clearEnd);
            env.logMessage(trace.toString());
            assertLinesAgree(lineSet, modifiedLineSet, trace.toString());
          }

          if (lineCount > 0 && env.generateValue(Generator.integers(0, 1), null) == 1) {
            int setCount = env.generateValue(Generator.integers(0, lineCount), null);
            IntList indices = new IntArrayList(setCount);
            for (int i = 0; i < setCount; i++) {
              indices.add((int) env.generateValue(Generator.integers(0, lineCount - 1), null));
            }
            trace.append(" .setModified(").append(indices).append(")");
            env.logMessage(trace.toString());

            // A multi-element list may legitimately include the synthetic trailing-empty-line index -- LineSet
            // only bounds-checks the size()==1 fast path, so setModified([..., realLineCount, ...]) throws
            // ArrayIndexOutOfBoundsException by design. Compare exception behavior rather than assuming success.
            LineSet newLineSet = null;
            Class<? extends Throwable> lineSetException = null;
            try {
              newLineSet = lineSet.setModified(indices);
            }
            catch (Throwable t) {
              lineSetException = t.getClass();
            }

            ModifiedLineSet newModifiedLineSet = null;
            Class<? extends Throwable> modifiedLineSetException = null;
            try {
              newModifiedLineSet = modifiedLineSet.setModified(indices);
            }
            catch (Throwable t) {
              modifiedLineSetException = t.getClass();
            }

            assertEquals(lineSetException, modifiedLineSetException, trace + " -- setModified exception parity");
            if (lineSetException == null) {
              lineSet = newLineSet;
              modifiedLineSet = newModifiedLineSet;
              assertLinesAgree(lineSet, modifiedLineSet, trace.toString());
            }
          }
        }
      });
  }

  // ---- Overhead measurement: how many LineSet-family update() calls happen per edit, and what that costs ----

  /**
   * Measures the actual overhead the "wip" commit introduced (a second, wasted {@link LineSet#update} call
   * inside {@code DocumentModStateImpl} per document edit) and how much {@link ModifiedLineSet} recovers,
   * without needing to check out other commits: the three scenarios below reproduce exactly what each
   * historical state did per edit, using the real {@link LineSet#update}/{@link ModifiedLineSet#update}
   * methods against the same growing document and the same edit sequence, differing only in which/how many
   * of those methods run per edit -- precisely the mechanism in question:
   * <ul>
   *   <li>{@code baseline}: pre-wip -- a single {@link LineSet} served both offset mapping and modification
   *       tracking, so one {@link LineSet#update} call per edit.</li>
   *   <li>{@code wip (defect)}: {@code DocumentTextImpl} keeps its own {@link LineSet} (needed) and
   *       {@code DocumentModStateImpl} kept a second, independent one purely for {@code isModified} -- two
   *       full {@link LineSet#update} calls per edit against the same growing document.</li>
   *   <li>{@code current (fix)}: {@code DocumentTextImpl}'s {@link LineSet#update} (unchanged, still needed)
   *       plus {@code DocumentModStateImpl}'s {@link ModifiedLineSet#update}, which skips the {@code int[]}
   *       offsets array entirely.</li>
   * </ul>
   * Many lines, not long lines, is the relevant shape here: {@link LineSet}'s and {@link ModifiedLineSet}'s
   * per-edit cost is dominated by cloning/rebuilding an array sized to the document's *line count* (see
   * {@code updateInsideOneLine}/{@code genericUpdate} in both classes), so the overhead only shows up on
   * documents with many lines -- unlike {@code LineSetIncrementalUpdateTest.testTypingInLongLinePerformance},
   * which stress-tests one long line instead.
   */
  @PerformanceUnitTest
  @Test
  public void testLineSetDuplicationOverhead() {
    int lineCount = 100_000;
    int edits = 30_000;
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < lineCount; i++) {
      sb.append("line ").append(i).append('\n');
    }
    String bigText = sb.toString();
    int midLine = lineCount / 2;

    Benchmark.newBenchmark("LineSet duplication overhead -- baseline (pre-wip): 1x LineSet.update() per edit", () -> {
      DocumentText text = documentText(bigText);
      int offset = text.lineStartOffset(midLine);
      for (int i = 0; i < edits; i++) {
        // simulates the pre-split DocumentTextImpl: its own LineSet served offset mapping AND modification
        // tracking, so this one call is the entire per-edit cost.
        text = applyPatch(text, DocumentTextPatch.simple(offset, offset, "x", i, false));
        offset += 1;
      }
    }).runAsStressTest().start();

    Benchmark.newBenchmark("LineSet duplication overhead -- wip (defect): 2x LineSet.update() per edit", () -> {
      DocumentText text = documentText(bigText);                    // DocumentTextImpl's own LineSet (real, needed)
      LineSet modStateLineSet = LineSet.createLineSet(text.cachedChars()); // DocumentModStateImpl's wasted duplicate
      int offset = text.lineStartOffset(midLine);
      for (int i = 0; i < edits; i++) {
        modStateLineSet = modStateLineSet.update(text.chars(), offset, offset, "x"); // the wasted call, using the OLD text
        text = applyPatch(text, DocumentTextPatch.simple(offset, offset, "x", i, false));
        offset += 1;
      }
    }).runAsStressTest().start();

    Benchmark.newBenchmark("LineSet duplication overhead -- current (fix): LineSet.update() + ModifiedLineSet.update() per edit", () -> {
      DocumentText text = documentText(bigText);
      ModifiedLineSet modifiedLineSet = ModifiedLineSet.create(text.cachedChars());
      int offset = text.lineStartOffset(midLine);
      for (int i = 0; i < edits; i++) {
        modifiedLineSet = modifiedLineSet.update(text, offset, offset, "x"); // using the OLD text, per the real contract
        text = applyPatch(text, DocumentTextPatch.simple(offset, offset, "x", i, false));
        offset += 1;
      }
    }).runAsStressTest().start();
  }

  // ---- Boundary/invalid-index exception parity (targets the setModified fast-path bug found during design review) ----

  @Test
  public void testBoundaryIndexExceptionParity() {
    checkBoundaryIndices("ab");         // 1 line, no trailing separator
    checkBoundaryIndices("ab\n");       // 1 real line with a trailing separator -> synthetic empty last line
    checkBoundaryIndices("");           // empty document, 0 lines
    checkBoundaryIndices("a\nb\nc");    // 3 lines, no trailing separator
    checkBoundaryIndices("a\nb\nc\n");  // 3 real lines, trailing separator
  }

  private static void checkBoundaryIndices(String text) {
    DocumentText documentText = documentText(text);
    LineSet lineSet = LineSet.createLineSet(documentText.cachedChars());
    ModifiedLineSet modifiedLineSet = ModifiedLineSet.create(documentText.cachedChars());
    String label = "\"" + StringUtil.escapeStringCharacters(text) + "\"";
    int lineCount = lineSet.getLineCount();
    assertEquals(lineCount, modifiedLineSet.getLineCount(), "line count for " + label);

    int[] indices = {-1, 0, lineCount - 1, lineCount, lineCount + 1, Integer.MAX_VALUE};
    for (int index : indices) {
      compareResult(() -> lineSet.isModified(index), () -> modifiedLineSet.isModified(index),
                    "isModified(" + index + ") on " + label);
      IntList singleton = new IntArrayList(new int[]{index});
      compareOutcome(() -> lineSet.setModified(singleton), () -> modifiedLineSet.setModified(singleton),
                     "setModified([" + index + "]) on " + label);
    }

    // multi-element setModified mixing a valid and a wildly out-of-range index: LineSet does NOT bounds-check
    // once indices.size() > 1 (only the singleton fast path does) -- both must fail the same raw-array-access way.
    IntList mixed = new IntArrayList(new int[]{0, lineCount + 5});
    compareOutcome(() -> lineSet.setModified(mixed), () -> modifiedLineSet.setModified(mixed),
                   "setModified(" + mixed + ") on " + label);

    for (int startLine : indices) {
      for (int endLine : new int[]{startLine, startLine + 1, lineCount, Integer.MAX_VALUE}) {
        compareOutcome(() -> lineSet.clearModificationFlags(startLine, endLine),
                       () -> modifiedLineSet.clearModificationFlags(startLine, endLine),
                       "clearModificationFlags(" + startLine + ", " + endLine + ") on " + label);
      }
    }
  }

  private static void compareResult(Supplier<Object> lineSetOp, Supplier<Object> modifiedLineSetOp, String context) {
    assertEquals(captureResult(lineSetOp), captureResult(modifiedLineSetOp), context);
  }

  private static Object captureResult(Supplier<Object> op) {
    try {
      return op.get();
    }
    catch (Throwable t) {
      return t.getClass();
    }
  }

  private static void compareOutcome(Runnable lineSetOp, Runnable modifiedLineSetOp, String context) {
    assertEquals(captureExceptionClass(lineSetOp), captureExceptionClass(modifiedLineSetOp), context);
  }

  private static Class<? extends Throwable> captureExceptionClass(Runnable op) {
    try {
      op.run();
      return null;
    }
    catch (Throwable t) {
      return t.getClass();
    }
  }

  // ---- Empty-document round trips embedded mid-chain ----

  @Test
  public void testEmptyDocumentRoundTripMidChain() {
    DocumentText text = documentText("");
    LineSet lineSet = LineSet.createLineSet(text.cachedChars());
    ModifiedLineSet modifiedLineSet = ModifiedLineSet.create(text.cachedChars());
    assertLinesAgree(lineSet, modifiedLineSet, "fresh empty document");
    assertEquals(0, lineSet.getLineCount());

    long modStamp = 0;
    for (String insertion : new String[]{"line1\n", "line2\n", "line3"}) {
      int offset = text.length();
      lineSet = lineSet.update(text.chars(), offset, offset, insertion);
      modifiedLineSet = modifiedLineSet.update(text, offset, offset, insertion);
      text = applyPatch(text, DocumentTextPatch.simple(offset, offset, insertion, ++modStamp, false));
      assertLinesAgree(lineSet, modifiedLineSet, "after inserting \"" + StringUtil.escapeStringCharacters(insertion) + "\"");
    }

    int fullLength = text.length();
    lineSet = lineSet.update(text.chars(), 0, fullLength, "");
    modifiedLineSet = modifiedLineSet.update(text, 0, fullLength, "");
    text = applyPatch(text, DocumentTextPatch.simple(0, fullLength, "", ++modStamp, false));
    assertLinesAgree(lineSet, modifiedLineSet, "delete back to empty");
    assertEquals(0, lineSet.getLineCount());

    // clearModificationFlags(0, MAX_VALUE) on an empty-but-materialized state must not throw -- see
    // LineSet.clearModificationFlags's lineCount==0 && startLine==0 bypass.
    lineSet = lineSet.clearModificationFlags(0, Integer.MAX_VALUE);
    modifiedLineSet = modifiedLineSet.clearModificationFlags(0, Integer.MAX_VALUE);
    assertLinesAgree(lineSet, modifiedLineSet, "clearModificationFlags(0, MAX_VALUE) on empty");

    lineSet = lineSet.setModified(new IntArrayList(0));
    modifiedLineSet = modifiedLineSet.setModified(new IntArrayList(0));
    assertLinesAgree(lineSet, modifiedLineSet, "setModified([]) on empty");

    // editing again from empty re-exercises the oldText.length()==0 shortcut
    lineSet = lineSet.update(text.chars(), 0, 0, "new content");
    modifiedLineSet = modifiedLineSet.update(text, 0, 0, "new content");
    assertLinesAgree(lineSet, modifiedLineSet, "edit again from empty");
  }

  // ---- Interleaved update() + clearModificationFlags(), mirroring the real
  //      DocumentTest.testClearLineFlagsInBeforeDocumentChange call pattern ----

  @Test
  public void testInterleavedUpdateAndClear() {
    DocumentText text = documentText("one\ntwo");
    LineSet lineSet = LineSet.createLineSet(text.cachedChars());
    ModifiedLineSet modifiedLineSet = ModifiedLineSet.create(text.cachedChars());
    long modStamp = 0;

    lineSet = lineSet.update(text.chars(), 0, 0, "x");
    modifiedLineSet = modifiedLineSet.update(text, 0, 0, "x");
    text = applyPatch(text, DocumentTextPatch.simple(0, 0, "x", ++modStamp, false));
    assertLinesAgree(lineSet, modifiedLineSet, "after inserting x");
    assertTrue(lineSet.isModified(0));
    assertFalse(lineSet.isModified(1));

    // clear all flags, as DocumentImpl.clearLineModificationFlags() does when called from a
    // beforeDocumentChange listener -- i.e. before the *next* edit's patch is even computed.
    lineSet = lineSet.clearModificationFlags(0, Integer.MAX_VALUE);
    modifiedLineSet = modifiedLineSet.clearModificationFlags(0, Integer.MAX_VALUE);
    assertLinesAgree(lineSet, modifiedLineSet, "after clearing all flags");
    assertFalse(lineSet.isModified(0));

    int line1Start = text.lineStartOffset(1);
    lineSet = lineSet.update(text.chars(), line1Start, line1Start, "y");
    modifiedLineSet = modifiedLineSet.update(text, line1Start, line1Start, "y");
    assertLinesAgree(lineSet, modifiedLineSet, "after inserting y on line 1");
    assertFalse(lineSet.isModified(0));
    assertTrue(lineSet.isModified(1));
  }

  private static void assertLinesAgree(LineSet lineSet, ModifiedLineSet modifiedLineSet, String context) {
    int lineCount = lineSet.getLineCount();
    assertEquals(lineCount, modifiedLineSet.getLineCount(), context + " -- line count");
    for (int i = 0; i < lineCount; i++) {
      assertEquals(lineSet.isModified(i), modifiedLineSet.isModified(i), context + " -- isModified(" + i + ")");
    }
  }

  /**
   * Builds a {@link DocumentText} without touching {@link DocumentTextImpl} directly -- it is Kotlin
   * {@code internal} to the {@code intellij.platform.core.impl} module and not safely reachable from this
   * test's own {@code intellij.platform.tests} module -- and without going through {@link DocumentImpl}'s
   * constructor, which validates that a document uses one consistent line-separator style and would reject the
   * deliberately chaotic {@code \r}/{@code \n} mixes the fuzz tests need to exercise {@link LineSet}'s
   * {@code \r\n}-merge logic. {@link DocumentInternalUtil#getDocumentText}'s non-{@link DocumentImpl} branch
   * does exactly this for any {@link com.intellij.openapi.editor.Document}: it builds a real
   * {@link DocumentTextImpl} itself (legitimately, from the same module) and hands back the public
   * {@link DocumentText} view -- so this is a genuine production instance with the real, incrementally
   * maintained {@link DocumentText#applyOp}, not a hand-rolled stand-in. {@link MockDocument#replaceText}
   * does no separator validation, so it is a fitting non-{@link DocumentImpl} source of chaotic content.
   */
  private static DocumentText documentText(String text) {
    MockDocument document = new MockDocument();
    document.replaceText(text, 0);
    return DocumentInternalUtil.getDocumentText(document);
  }

  private static DocumentText applyPatch(DocumentText text, DocumentTextPatch patch) {
    for (DocumentTextOp op : patch.toOps()) {
      text = text.applyOp(op);
    }
    return text;
  }
}
