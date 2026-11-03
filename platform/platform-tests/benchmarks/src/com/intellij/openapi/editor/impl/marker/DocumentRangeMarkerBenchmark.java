// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.RangeMarkersImpl;
import com.intellij.util.Processor;
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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Timeout;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Compares legacy markers and snapshot markers through the document API.
 * Snapshot markers use {@link PMarkerDirectRoot}.
 * Measures the same workloads as {@link SnapshotMarkerEngineBenchmark} and edits with large marker populations.
 *
 * <p>Each edit invocation changes the document once. Other invocations perform the operation count stated in their names.
 * This class uses {@link Mode#SingleShotTime} without {@code OperationsPerInvocation}.
 * JMH reports the total time for the complete batch in milliseconds.</p>
 *
 * <p>The native {@link RangeMarker} API does not expose a single operation that resolves both offsets. Therefore,
 * {@link #resolve4MMarkers(ResolveState)} calls both {@link RangeMarker#getStartOffset()} and
 * {@link RangeMarker#getEndOffset()} for each logical resolution.</p>
 *
 * <p>Suggested profiler options:</p>
 *
 * <pre>
 * -prof gc
 * -prof stack
 * -rf json -rff intellij-document-range-marker-jmh.json
 * </pre>
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1)
@Measurement(iterations = 5)
@Fork(value = 0, jvmArgsAppend = {"-Xms2g", "-Xmx2g"})
@Threads(1)
@Timeout(time = 10, timeUnit = TimeUnit.MINUTES)
public class DocumentRangeMarkerBenchmark {
  /** Replaces or deletes three quarters of a document, invalidating the markers in that range. */
  @Benchmark
  public DocumentEx invalidateManyMarkers(BulkEditState state) {
    RangeMarkersImpl.usePMarkerImplementationIn(state.usesSnapshotMarkers(), () -> {
      state.document.replaceString(state.editStart, state.editEnd, state.replacement);
    });
    return state.document;
  }

  /** Measures a small edit among many markers that overlap the edit. */
  @Benchmark
  public DocumentEx editOverlappingMarkers(SmallEditState state) {
    RangeMarkersImpl.usePMarkerImplementationIn(state.usesSnapshotMarkers(), () -> {
      switch (state.edit) {
        case "type" -> state.document.insertString(SmallEditState.EDIT_OFFSET, "y");
        case "backspace" -> state.document.deleteString(SmallEditState.EDIT_OFFSET - 1, SmallEditState.EDIT_OFFSET);
        case "replaceSelection" -> state.document.replaceString(SmallEditState.EDIT_OFFSET - 1, SmallEditState.EDIT_OFFSET, "y");
        case "move" -> state.document.moveText(SmallEditState.EDIT_OFFSET - 1, SmallEditState.EDIT_OFFSET, SmallEditState.MOVE_TARGET);
        default -> throw new IllegalArgumentException("Unknown edit: " + state.edit);
      }
    });
    return state.document;
  }

  /**
   * Creates a fresh document outside the timed region, then measures creation of 50,000 range markers.
   *
   * <p>Marker handles are retained in {@link CreateState#createdMarkers} so their lifetime matches a real marker
   * population and they cannot become garbage during the measured invocation.</p>
   */
  @Benchmark
  public long create50KRangeMarkers(CreateState state) {
    long[] checksum = {0L};

    RangeMarkersImpl.usePMarkerImplementationIn(state.usesSnapshotMarkers(), () -> {
      for (int index = 0; index < MARKER_COUNT; index++) {
        int startOffset = markerStart(index);
        RangeMarker marker = state.document.createRangeMarker(startOffset, startOffset + MARKER_LENGTH);
        state.createdMarkers[index] = marker;
        checksum[0]++;
      }
    });

    return checksum[0];
  }

  /**
   * Resolves the selected marker implementation 4,000,000 times.
   *
   * <p>The precomputed access order visits every one of the 50,000 markers exactly 80 times in a permuted order. Each
   * loop iteration reads both offsets and consumes them through the returned checksum.</p>
   */
  @Benchmark
  public long resolve4MMarkers(ResolveState state) {
    RangeMarker[] markers = state.markers;
    int[] resolveOrder = state.resolveOrder;
    long[] checksum = {0L};

    RangeMarkersImpl.usePMarkerImplementationIn(state.usesSnapshotMarkers(), () -> {
      for (int index = 0; index < RESOLVE_CALLS; index++) {
        RangeMarker marker = markers[resolveOrder[index]];
        checksum[0] += (long)marker.getStartOffset() + marker.getEndOffset();
      }
    });

    return checksum[0];
  }

  /**
   * Calls {@link DocumentEx#processRangeMarkersOverlappingWith(int, int, Processor)} 2,000,000 times.
   *
   * <p>{@link IntersectionState#hitsPerQuery} controls the exact output size {@code k}. The benchmark uses one
   * reusable processor, so it does not allocate a callback for every query. The returned value consumes the total
   * number of reported markers without adding offset-resolution work to the spatial-query benchmark.</p>
   */
  @Benchmark
  public long reportIntersecting2MQueries(IntersectionState state) {
    DocumentEx document = state.document;
    int[] queryStarts = state.queryStarts;
    int[] queryEnds = state.queryEnds;
    RangeMarkerAccumulator accumulator = state.accumulator;
    accumulator.reset();

    RangeMarkersImpl.usePMarkerImplementationIn(state.usesSnapshotMarkers(), () -> {
      for (int index = 0; index < INTERSECTION_CALLS; index++) {
        boolean completed = document.processRangeMarkersOverlappingWith(
          queryStarts[index],
          queryEnds[index],
          accumulator
        );
        if (!completed) {
          throw new IllegalStateException("Range-marker processing stopped unexpectedly");
        }
      }

      long expectedCount = (long)INTERSECTION_CALLS * state.hitsPerQuery;
      if (accumulator.getCount() != expectedCount) {
        throw new IllegalStateException(
          "Expected " + expectedCount + " intersecting markers, got " + accumulator.getCount()
        );
      }
    });

    return accumulator.getCount();
  }

  /**
   * Calls {@link DocumentEx#processRangeMarkersOverlappingWith(int, int, Processor)} 2,000,000 times.
   *
   * <p>This method uses the same marker population and query workload as {@link #reportIntersecting2MQueries}. It also
   * consumes each marker ID to match {@link SnapshotMarkerEngineBenchmark#enumerateIntersecting2MQueries}.</p>
   */
  @Benchmark
  public long enumerateIntersecting2MQueries(IntersectionState state) {
    DocumentEx document = state.document;
    int[] queryStarts = state.queryStarts;
    int[] queryEnds = state.queryEnds;
    RangeMarkerIdAccumulator accumulator = state.idAccumulator;
    accumulator.reset();

    RangeMarkersImpl.usePMarkerImplementationIn(state.usesSnapshotMarkers(), () -> {
      for (int index = 0; index < INTERSECTION_CALLS; index++) {
        boolean completed = document.processRangeMarkersOverlappingWith(
          queryStarts[index],
          queryEnds[index],
          accumulator
        );
        if (!completed) {
          throw new IllegalStateException("Range-marker processing stopped unexpectedly");
        }
      }

      long expectedCount = (long)INTERSECTION_CALLS * state.hitsPerQuery;
      if (accumulator.getCount() != expectedCount) {
        throw new IllegalStateException(
          "Expected " + expectedCount + " intersecting markers, got " + accumulator.getCount()
        );
      }
    });

    return accumulator.getChecksum() ^ accumulator.getCount();
  }

  /** Shares the implementation parameter across all workloads. */
  @State(Scope.Thread)
  public static class ImplementationState {
    @Param({"legacy", "direct"})
    @SuppressWarnings("WeakerAccess")
    public String implementation = "direct";

    final boolean usesSnapshotMarkers() {
      return !"legacy".equals(implementation);
    }
  }

  @State(Scope.Thread)
  public static class CreateState extends ImplementationState {
    Document document;
    RangeMarker[] createdMarkers;

    /** A new mutable document is required for every measured marker-creation batch. */
    @Setup(Level.Invocation)
    public void setUp() {
      RangeMarkersImpl.usePMarkerImplementationIn(usesSnapshotMarkers(), () -> {
        document = new DocumentImpl(BENCHMARK_TEXT);
        createdMarkers = new RangeMarker[MARKER_COUNT];
      });
    }
  }

  @State(Scope.Thread)
  public static class BulkEditState extends ImplementationState {
    @Param({"20000", "40000", "80000"})
    public int markerCount = 80_000;

    @Param({"replaceMost", "trimPrefix"})
    public String edit = "replaceMost";

    DocumentEx document;
    RangeMarker[] markers;
    int editStart;
    int editEnd;
    String replacement;

    /** Creates a fresh document and retains all markers outside the timed section. */
    @Setup(Level.Invocation)
    public void setUp() {
      RangeMarkersImpl.usePMarkerImplementationIn(usesSnapshotMarkers(), () -> {
        document = new DocumentImpl(BULK_EDIT_TEXT, true);
        markers = new RangeMarker[markerCount];
        for (var index = 0; index < markerCount; index++) {
          var start = (int)((long)index * BULK_EDIT_TEXT.length() / markerCount);
          markers[index] = document.createRangeMarker(start, start + MARKER_LENGTH);
        }
        switch (edit) {
          case "replaceMost" -> {
            editStart = BULK_EDIT_TEXT.length() / 4;
            editEnd = BULK_EDIT_TEXT.length();
            replacement = "y";
          }
          case "trimPrefix" -> {
            editStart = 0;
            editEnd = BULK_EDIT_TEXT.length() * 3 / 4;
            replacement = "";
          }
          default -> throw new IllegalArgumentException("Unknown edit: " + edit);
        }
      });
    }

    /** Checks invalidation, survivor offsets, and overlap queries outside the timed section. */
    @TearDown(Level.Invocation)
    public void verify() {
      RangeMarkersImpl.usePMarkerImplementationIn(usesSnapshotMarkers(), () -> {
        var delta = replacement.length() - (editEnd - editStart);
        if (document.getTextLength() != BULK_EDIT_TEXT.length() + delta) {
          throw new IllegalStateException("The edit produced an unexpected document length");
        }
        for (var index = 0; index < markerCount; index++) {
          var start = (int)((long)index * BULK_EDIT_TEXT.length() / markerCount);
          var valid = start < editStart || start >= editEnd;
          var marker = markers[index];
          if (marker.isValid() != valid) {
            throw new IllegalStateException("Unexpected validity for marker " + index);
          }
          if (valid) {
            var expectedStart = start < editStart ? start : start + delta;
            if (marker.getStartOffset() != expectedStart || marker.getEndOffset() != expectedStart + MARKER_LENGTH) {
              throw new IllegalStateException("Unexpected offsets for marker " + index);
            }
          }
        }
        var accumulator = new RangeMarkerAccumulator();
        if (!document.processRangeMarkersOverlappingWith(0, document.getTextLength(), accumulator) ||
            accumulator.getCount() != markerCount / 4) {
          throw new IllegalStateException("The overlap query returned an unexpected number of surviving markers");
        }
      });
    }
  }

  @State(Scope.Thread)
  public static class SmallEditState extends ImplementationState {
    private static final int MARKER_COUNT = 80_000;
    private static final int MARKER_STRIDE = BULK_EDIT_TEXT.length() / MARKER_COUNT;
    private static final int EDIT_OFFSET = BULK_EDIT_TEXT.length() / 2 + MARKER_LENGTH + 2;
    private static final int MOVE_TARGET = EDIT_OFFSET + 3 * MARKER_STRIDE;

    @Param({"200", "400", "800"})
    public int overlappingMarkerCount = 400;

    @Param({"type", "backspace", "replaceSelection", "move"})
    public String edit = "type";

    DocumentEx document;
    RangeMarker[] markers;

    /** Creates distinct long markers across the edit among short markers throughout the document. */
    @Setup(Level.Invocation)
    public void setUp() {
      RangeMarkersImpl.usePMarkerImplementationIn(usesSnapshotMarkers(), () -> {
        document = new DocumentImpl(BULK_EDIT_TEXT, true);
        markers = new RangeMarker[MARKER_COUNT];
        for (var index = 0; index < MARKER_COUNT; index++) {
          var start = index * MARKER_STRIDE;
          var end = index < overlappingMarkerCount ? MOVE_TARGET + MARKER_STRIDE + index : start + MARKER_LENGTH;
          markers[index] = document.createRangeMarker(start, end);
        }
      });
    }

    /** Checks all offsets and the overlap query outside the timed section. */
    @TearDown(Level.Invocation)
    public void verify() {
      RangeMarkersImpl.usePMarkerImplementationIn(usesSnapshotMarkers(), () -> {
        var delta = switch (edit) {
          case "type" -> 1;
          case "backspace" -> -1;
          case "replaceSelection", "move" -> 0;
          default -> throw new IllegalArgumentException("Unknown edit: " + edit);
        };
        if (document.getTextLength() != BULK_EDIT_TEXT.length() + delta) {
          throw new IllegalStateException("The edit produced an unexpected document length");
        }
        for (var index = 0; index < MARKER_COUNT; index++) {
          var start = index * MARKER_STRIDE;
          int end;
          if (index < overlappingMarkerCount) {
            end = MOVE_TARGET + MARKER_STRIDE + index + delta;
          }
          else {
            if (start > EDIT_OFFSET && (!"move".equals(edit) || start < MOVE_TARGET)) {
              start += "move".equals(edit) ? -1 : delta;
            }
            end = start + MARKER_LENGTH;
          }
          var marker = markers[index];
          if (!marker.isValid() || marker.getStartOffset() != start || marker.getEndOffset() != end) {
            throw new IllegalStateException("Unexpected range for marker " + index);
          }
        }
        var accumulator = new RangeMarkerAccumulator();
        if (!document.processRangeMarkersOverlappingWith(0, document.getTextLength(), accumulator) ||
            accumulator.getCount() != MARKER_COUNT) {
          throw new IllegalStateException("The overlap query returned an unexpected number of markers");
        }
      });
    }
  }

  @State(Scope.Thread)
  public static class ResolveState extends ImplementationState {
    Document document;
    RangeMarker[] markers;
    int[] resolveOrder;

    /** Builds the 50,000-marker population once per fork, outside measured invocations. */
    @Setup(Level.Trial)
    public void setUp() {
      RangeMarkersImpl.usePMarkerImplementationIn(usesSnapshotMarkers(), () -> {
        document = new DocumentImpl(BENCHMARK_TEXT);
        markers = new RangeMarker[MARKER_COUNT];

        for (int index = 0; index < MARKER_COUNT; index++) {
          int startOffset = markerStart(index);
          markers[index] = document.createRangeMarker(startOffset, startOffset + MARKER_LENGTH);
        }

        resolveOrder = buildResolveOrder();
      });
    }
  }

  @State(Scope.Thread)
  public static class IntersectionState extends ImplementationState {
    /** The number of markers that each intersection query returns. */
    @Param({"1", "8"})
    @SuppressWarnings("WeakerAccess")
    public int hitsPerQuery = 1;

    DocumentEx document;
    RangeMarker[] markers;
    int[] queryStarts;
    int[] queryEnds;
    final RangeMarkerAccumulator accumulator = new RangeMarkerAccumulator();
    final RangeMarkerIdAccumulator idAccumulator = new RangeMarkerIdAccumulator();

    /** Builds the 50,000-marker population and deterministic query workload once per parameter value and fork. */
    @Setup(Level.Trial)
    public void setUp() {
      RangeMarkersImpl.usePMarkerImplementationIn(usesSnapshotMarkers(), () -> {
        document = new DocumentImpl(BENCHMARK_TEXT);
        markers = new RangeMarker[MARKER_COUNT];
        for (int index = 0; index < MARKER_COUNT; index++) {
          int startOffset = markerStart(index);
          markers[index] = document.createRangeMarker(startOffset, startOffset + MARKER_LENGTH);
        }

        queryStarts = new int[INTERSECTION_CALLS];
        queryEnds = new int[INTERSECTION_CALLS];
        int availableBases = MARKER_COUNT - hitsPerQuery + 1;
        int baseMarker = 0;

        for (int index = 0; index < INTERSECTION_CALLS; index++) {
          int firstMarkerStart = markerStart(baseMarker);
          int lastMarkerStart = markerStart(baseMarker + hitsPerQuery - 1);
          queryStarts[index] = firstMarkerStart + QUERY_INSET;
          queryEnds[index] = lastMarkerStart + QUERY_INSET + 1;

          baseMarker += QUERY_STEP;
          if (baseMarker >= availableBases) {
            baseMarker %= availableBases;
          }
        }
      });
    }
  }

  /** Reusable processor used by the intersection benchmark. */
  public static final class RangeMarkerAccumulator implements Processor<RangeMarker> {
    private long count;

    @Override
    public boolean process(RangeMarker marker) {
      count++;
      return true;
    }

    public long getCount() {
      return count;
    }

    public void reset() {
      count = 0L;
    }
  }

  /** Reusable processor used by the marker enumeration benchmark. */
  public static final class RangeMarkerIdAccumulator implements Processor<RangeMarker> {
    private long checksum;
    private long count;

    @Override
    public boolean process(RangeMarker marker) {
      checksum += ((RangeMarkerEx)marker).getId();
      count++;
      return true;
    }

    public long getChecksum() {
      return checksum;
    }

    public long getCount() {
      return count;
    }

    public void reset() {
      checksum = 0L;
      count = 0L;
    }
  }

  private static final int MARKER_COUNT = 50_000;
  private static final int RESOLVE_CALLS = 4_000_000;
  private static final int INTERSECTION_CALLS = 2_000_000;
  private static final int MARKER_STRIDE = 16;
  private static final int MARKER_LENGTH = 8;
  private static final int QUERY_INSET = 4;
  private static final int RESOLVE_STEP = 8_191;
  private static final int QUERY_STEP = 7_919;
  private static final int DOCUMENT_LENGTH = MARKER_COUNT * MARKER_STRIDE + MARKER_LENGTH;

  private static final String BENCHMARK_TEXT = createBenchmarkText();
  private static final String BULK_EDIT_TEXT = "0123456789\n".repeat(80_000);

  /**
   * Runs this benchmark class through JMH. Command-line arguments are forwarded directly to the JMH runner. When no
   * arguments are supplied, all benchmark methods declared by {@link DocumentRangeMarkerBenchmark} are
   * selected.
   *
   * <p>Examples:</p>
   *
   * <pre>
   * IntelliJDocumentRangeMarkerBenchmark -prof gc
   * IntelliJDocumentRangeMarkerBenchmark .*resolve4MMarkers.* -f 1 -wi 1 -i 3
   * </pre>
   */
  static void main(String[] args) throws Exception {
    String[] effectiveArgs = args.length == 0
      ? new String[] {"^" + DocumentRangeMarkerBenchmark.class.getName().replace(".", "\\.") + "\\..*$"}
      : args;
    org.openjdk.jmh.Main.main(effectiveArgs);
  }

  private static int markerStart(int index) {
    return index * MARKER_STRIDE;
  }

  /**
   * Generates a 4,000,000-element order without adding pseudo-random generation or modulo cost to the timed region.
   * {@code RESOLVE_STEP} is coprime to 50,000, so one cycle visits every marker exactly once.
   */
  private static int[] buildResolveOrder() {
    int[] result = new int[RESOLVE_CALLS];
    int markerIndex = 0;

    for (int index = 0; index < result.length; index++) {
      result[index] = markerIndex;
      markerIndex += RESOLVE_STEP;
      if (markerIndex >= MARKER_COUNT) {
        markerIndex -= MARKER_COUNT;
      }
    }

    return result;
  }

  /** Creates the benchmark text without relying on Java 11's {@code String.repeat}. */
  private static String createBenchmarkText() {
    char[] characters = new char[DOCUMENT_LENGTH];
    Arrays.fill(characters, 'x');
    return new String(characters);
  }
}
