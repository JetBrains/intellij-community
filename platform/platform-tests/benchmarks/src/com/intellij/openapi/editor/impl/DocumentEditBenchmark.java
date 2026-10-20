// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * Measures the edits of a {@link DocumentImpl} that its document core sees most often: typing, backspace, a word
 * replace, a whole-text replace, a console append, a stamp change, and an edit-and-save cycle that clears the line
 * flags.
 *
 * <p>Each invocation applies a fixed batch of edits to a fresh document. The document has about 250,000 characters
 * and its line data, as an editor builds it. The batch size is the operation count, so JMH reports the time of one
 * edit.</p>
 *
 * <p>Suggested profiler options:</p>
 *
 * <pre>
 * -prof gc
 * -rf json -rff document-edit-jmh.json
 * </pre>
 *
 * <p>To run it with Bazel from the {@code community} directory, start JMH on the classpath of the test target:</p>
 *
 * <pre>
 * ./bazel.cmd run //platform/platform-tests/benchmarks:benchmarks_test -- \
 *   --main_advice=org.openjdk.jmh.Main '^com\.intellij\.openapi\.editor\.impl\.DocumentEditBenchmark\..*$' -prof gc
 * </pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 0, jvmArgsAppend = {"-Xms2g", "-Xmx2g"})
@Threads(1)
public class DocumentEditBenchmark {
  /**
   * Types one character at a time at a moving caret in the middle of the text.
   */
  @Benchmark
  @OperationsPerInvocation(EDITS)
  public int typeChars(DocumentState state) {
    DocumentImpl document = state.document;
    int caret = MIDDLE;
    for (int index = 0; index < EDITS; index++) {
      document.insertString(caret, "x");
      caret++;
    }
    return document.getTextLength();
  }

  /**
   * Deletes one character at a time before a moving caret in the middle of the text.
   */
  @Benchmark
  @OperationsPerInvocation(EDITS)
  public int backspace(DocumentState state) {
    DocumentImpl document = state.document;
    int caret = MIDDLE;
    for (int index = 0; index < EDITS; index++) {
      document.deleteString(caret - 1, caret);
      caret--;
    }
    return document.getTextLength();
  }

  /**
   * Replaces a word with another one at places spread over the text, as a completion does.
   */
  @Benchmark
  @OperationsPerInvocation(EDITS)
  public int replaceWord(DocumentState state) {
    DocumentImpl document = state.document;
    int span = document.getTextLength() - WORD_LENGTH;
    for (int index = 0; index < EDITS; index++) {
      int offset = (int)((long)index * WORD_STRIDE % span);
      document.replaceString(offset, offset + WORD_LENGTH, "value");
    }
    return document.getTextLength();
  }

  /**
   * Replaces the whole text with a string. The document core narrows the change to the changed part.
   */
  @Benchmark
  @OperationsPerInvocation(WHOLE_TEXT_EDITS)
  public int replaceWholeTextWithString(DocumentState state) {
    DocumentImpl document = state.document;
    for (int index = 0; index < WHOLE_TEXT_EDITS; index++) {
      document.setText(index % 2 == 0 ? CHANGED_TEXT : BENCHMARK_TEXT);
    }
    return document.getTextLength();
  }

  /**
   * Replaces the whole text with an immutable char sequence. The document core then keeps the whole change and
   * clears every line flag.
   */
  @Benchmark
  @OperationsPerInvocation(WHOLE_TEXT_EDITS)
  public int replaceWholeTextWithImmutable(DocumentState state) {
    DocumentImpl document = state.document;
    for (int index = 0; index < WHOLE_TEXT_EDITS; index++) {
      document.setText(index % 2 == 0 ? CHANGED_IMMUTABLE_TEXT : BENCHMARK_IMMUTABLE_TEXT);
    }
    return document.getTextLength();
  }

  /**
   * Appends one line at a time to a console document whose cycle buffer is full, so each append also deletes the
   * oldest text.
   */
  @Benchmark
  @OperationsPerInvocation(EDITS)
  public int appendToConsole(ConsoleState state) {
    DocumentImpl document = state.document;
    for (int index = 0; index < EDITS; index++) {
      document.insertString(document.getTextLength(), CONSOLE_LINE);
    }
    return document.getTextLength();
  }

  /**
   * Sets a new modification stamp, as a save and a reload do.
   */
  @Benchmark
  @OperationsPerInvocation(EDITS)
  public long setModificationStamp(DocumentState state) {
    DocumentImpl document = state.document;
    long stamp = document.getModificationStamp();
    for (int index = 0; index < EDITS; index++) {
      stamp++;
      document.setModificationStamp(stamp);
    }
    return document.getModificationStamp();
  }

  /**
   * Edits a few lines and then clears the line flags except the caret line, as a save with trailing space
   * stripping does. One operation is one cycle.
   */
  @Benchmark
  @OperationsPerInvocation(SAVES)
  public int editAndSave(DocumentState state) {
    DocumentImpl document = state.document;
    int lineCount = document.getLineCount();
    int[] caretLines = new int[1];
    for (int save = 0; save < SAVES; save++) {
      for (int edit = 0; edit < EDITS_PER_SAVE; edit++) {
        int line = (save * EDITS_PER_SAVE + edit) * LINE_STRIDE % lineCount;
        document.insertString(document.getLineStartOffset(line), "x");
      }
      caretLines[0] = save * LINE_STRIDE % lineCount;
      document.clearLineModificationFlagsExcept(caretLines);
    }
    return document.getTextLength();
  }

  /**
   * A fresh document of {@link #BENCHMARK_TEXT} for each invocation, with its line data.
   */
  @State(Scope.Thread)
  public static class DocumentState {
    DocumentImpl document;

    @Setup(Level.Invocation)
    public void setUp() {
      document = new DocumentImpl(BENCHMARK_TEXT, true);
      buildLineData(document);
    }
  }

  /**
   * A fresh console document for each invocation, whose cycle buffer is already full.
   */
  @State(Scope.Thread)
  public static class ConsoleState {
    DocumentImpl document;

    @Setup(Level.Invocation)
    public void setUp() {
      document = new DocumentImpl(CONSOLE_TEXT, true);
      document.setCyclicBufferSize(CONSOLE_TEXT.length());
      buildLineData(document);
    }
  }

  private static final int EDITS = 1_000;
  private static final int WHOLE_TEXT_EDITS = 20;
  private static final int SAVES = 50;
  private static final int EDITS_PER_SAVE = 10;
  private static final int TEXT_LENGTH = 250_000;
  private static final int CONSOLE_LENGTH = 100_000;
  private static final int MIDDLE = TEXT_LENGTH / 2;
  private static final int WORD_LENGTH = 5;
  private static final int WORD_STRIDE = 7_919;
  private static final int LINE_STRIDE = 613;
  private static final String CONSOLE_LINE = "2026-10-20 16:30:00,000 INFO - the console prints one more line of its log\n";

  private static final String BENCHMARK_TEXT = createBenchmarkText(TEXT_LENGTH);
  private static final String CHANGED_TEXT = changedInTheMiddle();
  private static final String CONSOLE_TEXT = createBenchmarkText(CONSOLE_LENGTH);
  private static final CharSequence BENCHMARK_IMMUTABLE_TEXT = new DocumentImpl(BENCHMARK_TEXT).getImmutableCharSequence();
  private static final CharSequence CHANGED_IMMUTABLE_TEXT = new DocumentImpl(CHANGED_TEXT).getImmutableCharSequence();

  /**
   * Runs this benchmark class through JMH. Command-line arguments are forwarded directly to the JMH runner. When no
   * arguments are supplied, all benchmark methods declared by {@link DocumentEditBenchmark} are selected.
   */
  static void main(String[] args) throws Exception {
    String[] effectiveArgs = args.length == 0
      ? new String[] {"^" + DocumentEditBenchmark.class.getName().replace(".", "\\.") + "\\..*$"}
      : args;
    org.openjdk.jmh.Main.main(effectiveArgs);
  }

  /**
   * Java-like lines of about 30 characters, up to at least {@code length} characters.
   */
  private static String createBenchmarkText(int length) {
    StringBuilder text = new StringBuilder(length + 64);
    int line = 0;
    while (text.length() < length) {
      text.append("    int field").append(line).append(" = compute(").append(line).append(");\n");
      line++;
    }
    return text.toString();
  }

  /**
   * {@link #BENCHMARK_TEXT} with one character in the middle replaced.
   */
  private static String changedInTheMiddle() {
    int middle = BENCHMARK_TEXT.length() / 2;
    return BENCHMARK_TEXT.substring(0, middle) + '#' + BENCHMARK_TEXT.substring(middle + 1);
  }

  /**
   * Builds the line data of {@code document}, as an editor that reads the lines does. The document then keeps its
   * line data on every edit.
   */
  private static void buildLineData(DocumentImpl document) {
    if (document.getLineCount() < 2) {
      throw new IllegalStateException("The benchmark text has no lines: " + document.getTextLength() + " characters");
    }
  }
}
