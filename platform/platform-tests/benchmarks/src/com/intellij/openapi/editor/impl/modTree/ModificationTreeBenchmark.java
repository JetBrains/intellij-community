// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.modTree;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 7, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
public class ModificationTreeBenchmark {

  @Benchmark
  public static ModificationTree applyInsertOnlyEdits(BenchmarkState state) {
    ModificationTree tree = state.implementation.initial(state.version0Length);

    for (Edit edit : state.insertOnlyEdits) {
      tree = tree.insert(edit.startInCurrent(), edit.length());
    }

    return tree;
  }

  @Benchmark
  public static ModificationTree applyMixedEdits(BenchmarkState state) {
    ModificationTree tree = state.implementation.initial(state.version0Length);

    for (Edit edit : state.mixedEdits) {
      if (edit.isInsert()) {
        tree = tree.insert(edit.startInCurrent(), edit.length());
      }
      else {
        tree = tree.delete(edit.startInCurrent(), edit.endInCurrent());
      }
    }

    return tree;
  }

  @Benchmark
  public static int toCurrentOffsetAfterInsertOnlyEdits(BenchmarkState state) {
    ModificationTree tree = state.insertOnlyTree;
    int result = 0;

    for (int offset : state.version0QueryOffsets) {
      result += tree.toCurrentOffset(offset);
    }

    return result;
  }

  @Benchmark
  public static int toVersion0OffsetAfterInsertOnlyEdits(BenchmarkState state) {
    ModificationTree tree = state.insertOnlyTree;
    int result = 0;

    for (int offset : state.insertOnlyCurrentQueryOffsets) {
      result += tree.toVersion0Offset(offset);
    }

    return result;
  }

  @Benchmark
  public static int toCurrentOffsetAfterMixedEdits(BenchmarkState state) {
    ModificationTree tree = state.mixedTree;
    int result = 0;

    for (int offset : state.version0QueryOffsets) {
      result += tree.toCurrentOffset(offset);
    }

    return result;
  }

  @Benchmark
  public static int toVersion0OffsetAfterMixedEdits(BenchmarkState state) {
    ModificationTree tree = state.mixedTree;
    int result = 0;

    for (int offset : state.mixedCurrentQueryOffsets) {
      result += tree.toVersion0Offset(offset);
    }

    return result;
  }

  @State(Scope.Thread)
  public static class BenchmarkState {
    @Param({
      "BINARY",
      "BTREE"
    })
    public Implementation implementation;

    @Param({
      "1000000"
    })
    public int version0Length;

    @Param({
      "10000",
      "100000"
    })
    public int editCount;

    private static final int QUERY_COUNT = 100_000;

    Edit[] insertOnlyEdits;
    Edit[] mixedEdits;

    int[] version0QueryOffsets;
    int[] insertOnlyCurrentQueryOffsets;
    int[] mixedCurrentQueryOffsets;

    ModificationTree insertOnlyTree;
    ModificationTree mixedTree;

    @Setup(Level.Trial)
    public void setup() {
      insertOnlyEdits = generateInsertOnlyEdits(version0Length, editCount, 0x1234_5678_9abc_def0L);
      mixedEdits = generateMixedEdits(version0Length, editCount, 0xfedc_ba98_7654_3210L);

      insertOnlyTree = applyEdits(
        implementation.initial(version0Length),
        insertOnlyEdits
      );

      mixedTree = applyEdits(
        implementation.initial(version0Length),
        mixedEdits
      );

      version0QueryOffsets = generateOffsets(
        QUERY_COUNT,
        version0Length,
        0x1111_2222_3333_4444L
      );

      insertOnlyCurrentQueryOffsets = generateOffsets(
        QUERY_COUNT,
        currentLengthAfter(version0Length, insertOnlyEdits),
        0x5555_6666_7777_8888L
      );

      mixedCurrentQueryOffsets = generateOffsets(
        QUERY_COUNT,
        currentLengthAfter(version0Length, mixedEdits),
        0x9999_aaaa_bbbb_ccccL
      );
    }
  }

  public enum Implementation {
    BINARY {
      @Override
      ModificationTree initial(int version0Length) {
        return ModificationTreeImpl.initial(version0Length);
      }
    },

    BTREE {
      @Override
      ModificationTree initial(int version0Length) {
        return ModificationBTreeImpl.initial(version0Length);
      }
    };

    abstract ModificationTree initial(int version0Length);
  }

  private record Edit(
    boolean isInsert,
    int startInCurrent,
    int endInCurrent,
    int length
  ) {
    static Edit insert(int offsetInCurrent, int length) {
      return new Edit(true, offsetInCurrent, offsetInCurrent, length);
    }

    static Edit delete(int startInCurrent, int endInCurrent) {
      return new Edit(false, startInCurrent, endInCurrent, endInCurrent - startInCurrent);
    }
  }

  private static ModificationTree applyEdits(ModificationTree tree, Edit[] edits) {
    for (Edit edit : edits) {
      if (edit.isInsert()) {
        tree = tree.insert(edit.startInCurrent(), edit.length());
      }
      else {
        tree = tree.delete(edit.startInCurrent(), edit.endInCurrent());
      }
    }

    return tree;
  }

  private static Edit[] generateInsertOnlyEdits(
    int initialLength,
    int editCount,
    long seed
  ) {
    SplitMix64 random = new SplitMix64(seed);
    Edit[] edits = new Edit[editCount];

    int currentLength = initialLength;

    for (int i = 0; i < editCount; i++) {
      int offset = random.nextInt(currentLength + 1);
      int length = 1 + random.nextInt(8);

      edits[i] = Edit.insert(offset, length);

      currentLength += length;
    }

    return edits;
  }

  private static Edit[] generateMixedEdits(
    int initialLength,
    int editCount,
    long seed
  ) {
    SplitMix64 random = new SplitMix64(seed);
    Edit[] edits = new Edit[editCount];

    int currentLength = initialLength;

    for (int i = 0; i < editCount; i++) {
      boolean insert = currentLength == 0 || random.nextInt(100) < 60;

      if (insert) {
        int offset = random.nextInt(currentLength + 1);
        int length = 1 + random.nextInt(8);

        edits[i] = Edit.insert(offset, length);

        currentLength += length;
      }
      else {
        int start = random.nextInt(currentLength);
        int maxLength = Math.min(8, currentLength - start);
        int length = 1 + random.nextInt(maxLength);
        int end = start + length;

        edits[i] = Edit.delete(start, end);

        currentLength -= length;
      }
    }

    return edits;
  }

  private static int currentLengthAfter(int initialLength, Edit[] edits) {
    int currentLength = initialLength;

    for (Edit edit : edits) {
      if (edit.isInsert()) {
        currentLength += edit.length();
      }
      else {
        currentLength -= edit.length();
      }
    }

    return currentLength;
  }

  private static int[] generateOffsets(
    int count,
    int inclusiveUpperBound,
    long seed
  ) {
    SplitMix64 random = new SplitMix64(seed);
    int[] offsets = new int[count];

    for (int i = 0; i < count; i++) {
      offsets[i] = random.nextInt(inclusiveUpperBound + 1);
    }

    return offsets;
  }

  private static final class SplitMix64 {
    private long state;

    private SplitMix64(long seed) {
      this.state = seed;
    }

    int nextInt(int bound) {
      if (bound <= 0) {
        throw new IllegalArgumentException("bound must be > 0: " + bound);
      }

      return Math.floorMod(nextLong(), bound);
    }

    private int nextLong() {
      long z = (state += 0x9e3779b97f4a7c15L);

      z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
      z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
      z = z ^ (z >>> 31);

      return (int)z;
    }
  }

  static void main(final String[] args) throws RunnerException {
    System.setProperty("jmh.separateClasspathJAR", "true");

    final Options opt = new OptionsBuilder()
      .jvmArgs()
      //.forks(1)
      .forks(0)
      .threads(1)
      .jvmArgsAppend("-Djmh.separateClasspathJAR=true")
      .include("\\W" + ModificationTreeBenchmark.class.getSimpleName() + ".*")
      .build();

    new Runner(opt).run();
  }
}