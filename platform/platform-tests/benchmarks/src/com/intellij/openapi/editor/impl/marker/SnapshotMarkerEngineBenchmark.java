// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker;

import com.intellij.openapi.editor.ex.DocumentSnapshot;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.editor.impl.DocumentImpl;
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
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Timeout;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Measures complete fixed-size snapshot-marker-engine workloads.
 *
 * <p>Every benchmark invocation performs exactly the number of operations stated in its method name. Because the
 * class uses {@link Mode#SingleShotTime} and does not use {@code OperationsPerInvocation}, JMH reports the total time
 * for the complete batch in milliseconds.</p>
 *
 * <p>Suggested profiler options:</p>
 *
 * <pre>
 * -prof gc
 * -prof stack
 * -rf json -rff marker-storage-jmh.json
 * </pre>
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1)
@Measurement(iterations = 5)
@Fork(value = 0, jvmArgsAppend = {"-Xms2g", "-Xmx2g"})
@Threads(1)
@Timeout(time = 10, timeUnit = TimeUnit.MINUTES)
public class SnapshotMarkerEngineBenchmark {
  /**
   * Creates a fresh engine outside the timed region, then measures creation of 50,000 range markers.
   *
   * <p>Marker handles are retained in {@link CreateState#createdMarkers} so their lifetime matches a real marker
   * population and they cannot become garbage during the measured invocation.</p>
   */
  @Benchmark
  public long create50KRangeMarkers(CreateState state) {
    long checksum = 0L;

    for (int index = 0; index < MARKER_COUNT; index++) {
      int startOffset = markerStart(index);
      PMarker marker = SnapshotMarkerEngineImpl.INSTANCE.createRangeMarker(
        state.document,
        state.snapshot,
        startOffset,
        startOffset + MARKER_LENGTH,
        NON_GREEDY_SPEC
      );
      state.createdMarkers[index] = marker;
      checksum ^= marker.getId();
    }

    return checksum;
  }

  /**
   * Resolves markers 4,000,000 times through {@link SnapshotMarkerEngineImpl}.
   *
   * <p>The precomputed access order visits every one of the 50,000 markers exactly 80 times in a permuted order. Each
   * loop iteration performs one {@code resolve(snapshot)} call and consumes both resolved offsets through the returned
   * checksum.</p>
   */
  @Benchmark
  public long resolve4MMarkers(ResolveState state) {
    PMarker[] markers = state.markers;
    int[] resolveOrder = state.resolveOrder;
    DocumentSnapshot snapshot = state.snapshot;
    long checksum = 0L;

    for (int index = 0; index < RESOLVE_CALLS; index++) {
      PMarkerResolution resolution = markers[resolveOrder[index]].resolve(snapshot);
      checksum += (long)resolution.getStartOffset() + resolution.getEndOffset();
    }

    return checksum;
  }

  /**
   * Calls {@link PMarkerRoot#processRangeMarkersOverlappingWith} 2,000,000 times.
   *
   * <p>{@link IntersectionState#markersPerQuery} controls the exact output size {@code k}. The benchmark uses a
   * reusable consumer, so it does not allocate a capturing lambda for every query. The returned checksum consumes
   * every reported marker ID and the total number of reported markers.</p>
   *
   * <p>This benchmark targets the persistent interval index directly to isolate tree reporting from marker-handle
   * lookup and materialization.</p>
   */
  @Benchmark
  public long reportIntersecting2MQueries(IntersectionState state) {
    PMarkerRoot root = state.root;
    int[] queryStarts = state.queryStarts;
    int[] queryEnds = state.queryEnds;
    MarkerIdAccumulator accumulator = state.accumulator;
    accumulator.reset();

    for (int index = 0; index < INTERSECTION_CALLS; index++) {
      root.processRangeMarkersOverlappingWith(queryStarts[index], queryEnds[index], accumulator);
    }

    return accumulator.getChecksum() ^ accumulator.getCount();
  }

  /**
   * Calls {@link SnapshotMarkerEngineImpl#processRangeMarkersOverlappingWith} 2,000,000 times.
   *
   * <p>This uses the same marker population and query workload as {@link #reportIntersecting2MQueries}, but creates
   * and retains real marker handles and enumerates them through the engine. The timed path therefore includes the
   * marker-reference lookup and weak-reference dereference for every reported marker.</p>
   */
  @Benchmark
  public long enumerateIntersecting2MQueries(EngineIntersectionState state) {
    DocumentSnapshot snapshot = state.snapshot;
    int[] queryStarts = state.queryStarts;
    int[] queryEnds = state.queryEnds;
    RangeMarkerIdAccumulator accumulator = state.accumulator;
    accumulator.reset();

    for (int index = 0; index < INTERSECTION_CALLS; index++) {
      SnapshotMarkerEngineImpl.INSTANCE.processRangeMarkersOverlappingWith(
        snapshot,
        queryStarts[index],
        queryEnds[index],
        0,
        accumulator
      );
    }

    return accumulator.getChecksum() ^ accumulator.getCount();
  }

  @State(Scope.Thread)
  public static class CreateState {
    DocumentImpl document;
    DocumentSnapshot snapshot;
    PMarker[] createdMarkers;

    /** A new mutable engine is required for every measured creation batch. */
    @Setup(Level.Invocation)
    public void setUp() {
      document = new DocumentImpl(BENCHMARK_TEXT);
      snapshot = document.getCore().snapshot();
      createdMarkers = new PMarker[MARKER_COUNT];
    }
  }

  @State(Scope.Thread)
  public static class ResolveState {
    DocumentImpl document;
    DocumentSnapshot snapshot;
    PMarker[] markers;
    int[] resolveOrder;

    /** Builds the 50,000-marker population once per fork, outside measured invocations. */
    @Setup(Level.Trial)
    public void setUp() {
      document = new DocumentImpl(BENCHMARK_TEXT);
      snapshot = document.getCore().snapshot();
      markers = new PMarker[MARKER_COUNT];

      for (int index = 0; index < MARKER_COUNT; index++) {
        int startOffset = markerStart(index);
        markers[index] = SnapshotMarkerEngineImpl.INSTANCE.createRangeMarker(
          document,
          snapshot,
          startOffset,
          startOffset + MARKER_LENGTH,
          NON_GREEDY_SPEC
        );
      }

      resolveOrder = buildResolveOrder();
    }
  }

  @State(Scope.Thread)
  public static class IntersectionState {
    @Param({"1", "8"})
    public int markersPerQuery = 1;

    PMarkerRoot root;
    int[] queryStarts;
    int[] queryEnds;
    final MarkerIdAccumulator accumulator = new MarkerIdAccumulator();

    /** Builds the root and deterministic query workload once per parameter value and fork. */
    @Setup(Level.Trial)
    public void setUp() {
      PMarkerRoot currentRoot = PMarkerRootImpl.Companion.empty();
      for (int index = 0; index < MARKER_COUNT; index++) {
        int startOffset = markerStart(index);
        currentRoot = currentRoot.insert(
          (long)index + 1L,
          startOffset,
          startOffset + MARKER_LENGTH,
          NON_GREEDY_SPEC
        );
      }
      root = currentRoot;

      queryStarts = new int[INTERSECTION_CALLS];
      queryEnds = new int[INTERSECTION_CALLS];
      fillIntersectionQueries(markersPerQuery, queryStarts, queryEnds);
    }
  }

  @State(Scope.Thread)
  public static class EngineIntersectionState {
    @Param({"1", "8"})
    public int markersPerQuery = 1;

    DocumentImpl document;
    DocumentSnapshot snapshot;
    PMarker[] markers;
    int[] queryStarts;
    int[] queryEnds;
    final RangeMarkerIdAccumulator accumulator = new RangeMarkerIdAccumulator();

    /** Builds and retains the real 50,000-marker population once per parameter value and fork. */
    @Setup(Level.Trial)
    public void setUp() {
      document = new DocumentImpl(BENCHMARK_TEXT);
      snapshot = document.getCore().snapshot();
      markers = new PMarker[MARKER_COUNT];

      for (int index = 0; index < MARKER_COUNT; index++) {
        int startOffset = markerStart(index);
        markers[index] = SnapshotMarkerEngineImpl.INSTANCE.createRangeMarker(
          document,
          snapshot,
          startOffset,
          startOffset + MARKER_LENGTH,
          NON_GREEDY_SPEC
        );
      }

      queryStarts = new int[INTERSECTION_CALLS];
      queryEnds = new int[INTERSECTION_CALLS];
      fillIntersectionQueries(markersPerQuery, queryStarts, queryEnds);
    }
  }

  /** Reusable callback used by the intersection benchmark. */
  public static final class MarkerIdAccumulator implements Processor<PMarkerRoot.MarkerEntry> {
    private long checksum;
    private long count;

    @Override
    public boolean process(PMarkerRoot.MarkerEntry entry) {
      checksum += entry.getMarkerId();
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

  /** Reusable callback used by the engine enumeration benchmark. */
  public static final class RangeMarkerIdAccumulator implements Processor<RangeMarkerEx> {
    private long checksum;
    private long count;

    @Override
    public boolean process(RangeMarkerEx marker) {
      checksum += marker.getId();
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
  private static final MarkerSpec NON_GREEDY_SPEC = new MarkerSpec(false, false, false);

  /**
   * Runs this benchmark class through JMH. Command-line arguments are forwarded directly to the JMH runner. When no
   * arguments are supplied, all benchmark methods declared by {@link SnapshotMarkerEngineBenchmark} are selected.
   *
   * <p>Examples:</p>
   *
   * <pre>
   * SnapshotMarkerEngineBenchmark -prof gc
   * SnapshotMarkerEngineBenchmark .*resolve4MMarkers.* -f 1 -wi 1 -i 3
   * </pre>
   */
  static void main(String[] args) throws Exception {
    System.setProperty("jmh.separateClasspathJAR", "true");

    final Options opt = new OptionsBuilder()
      .jvmArgs()
      //.forks(1)
      .forks(0)
      .threads(1)
      .jvmArgsAppend("-Djmh.separateClasspathJAR=true")
      .include("\\W" + SnapshotMarkerEngineBenchmark.class.getSimpleName() + ".*")
      .build();

    new Runner(opt).run();
  }

  private static int markerStart(int index) {
    return index * MARKER_STRIDE;
  }

  private static void fillIntersectionQueries(int markersPerQuery, int[] queryStarts, int[] queryEnds) {
    int availableBases = MARKER_COUNT - markersPerQuery + 1;
    int baseMarker = 0;

    for (int index = 0; index < INTERSECTION_CALLS; index++) {
      int firstMarkerStart = markerStart(baseMarker);
      int lastMarkerStart = markerStart(baseMarker + markersPerQuery - 1);
      queryStarts[index] = firstMarkerStart + QUERY_INSET;
      queryEnds[index] = lastMarkerStart + QUERY_INSET + 1;

      baseMarker += QUERY_STEP;
      if (baseMarker >= availableBases) {
        baseMarker %= availableBases;
      }
    }
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
