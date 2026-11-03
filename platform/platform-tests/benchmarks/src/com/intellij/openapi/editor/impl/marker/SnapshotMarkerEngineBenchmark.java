// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker;

import com.intellij.openapi.editor.ex.DocumentPatch;
import com.intellij.openapi.editor.ex.DocumentSnapshot;
import com.intellij.openapi.editor.ex.DocumentText;
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
import org.openjdk.jmh.runner.options.CommandLineOptions;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Measures complete fixed-size snapshot-marker-engine workloads.
 *
 * <p>Every benchmark invocation performs either the number of operations stated in its method name or the number
 * selected by its load parameter. Because the class uses {@link Mode#SingleShotTime} and does not use
 * {@code OperationsPerInvocation}, JMH reports the total time for the complete batch in milliseconds.</p>
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
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(value = 1, jvmArgsAppend = {"-Xms2g", "-Xmx2g"})
@Threads(1)
@Timeout(time = 10, timeUnit = TimeUnit.MINUTES)
@SuppressWarnings({"MethodMayBeStatic", "WeakerAccess"})
public class SnapshotMarkerEngineBenchmark {
  /** Creates the marker population in a persistent root. */
  @Benchmark
  public Object create50KRootMarkers(RootCreateState state) {
    var root = state.root;
    for (int index = 0; index < MARKER_COUNT; index++) {
      var start = markerStart(index);
      root =
        root.insert(SnapshotMarkerReference.create(state.markerForHandles, false), start, start + MARKER_LENGTH, NON_GREEDY_SPEC, (byte)0,
                    0, DefaultMarkerPolicy.INSTANCE);
    }
    return root;
  }

  /** Resolves the root through retained handles. */
  @Benchmark
  public long resolve4MRootMarkers(RootState state) {
    long checksum = 0;
    for (int index : state.resolveOrder) checksum += state.root.resolve(state.handles[index]);
    return checksum;
  }

  /** Removes 1,000 markers while preserving the input root. */
  @Benchmark
  public Object remove1KRootMarkers(RootState state) {
    var root = state.root;
    for (int index = 0; index < ROOT_UPDATES; index++) root = root.remove(state.handles[state.resolveOrder[index]]);
    return root;
  }

  /** Updates 1,000 specifications while preserving the input root. */
  @Benchmark
  public Object update1KRootSpecs(RootState state) {
    byte spec = MarkerSpec.create(true, true, false);
    var root = state.root;
    for (int index = 0; index < ROOT_UPDATES; index++) root = root.updateSpec(state.handles[state.resolveOrder[index]], spec);
    return root;
  }

  /** Applies an insertion at the middle of the document to the root. */
  @Benchmark
  public Object patchRoot(RootState state) {
    return state.root.applyPatch(state.patch, state.beforeText, state.afterText,
                                 PMarkerRoot.EMPTY_HANDLE_CONSUMER, PMarkerRoot.EMPTY_HANDLE_CONSUMER);
  }

  @State(Scope.Thread)
  public static class RootCreateState {
    PMarkerRoot root;
    final SnapshotRangeMarkerImpl markerForHandles = createMarkerForHandles();

    @Setup(Level.Invocation)
    public void setUp() {
      root = PMarkerRoot.empty();
    }
  }

  @State(Scope.Thread)
  public static class RootState {
    PMarkerRoot root;
    SnapshotRangeMarkerImpl markerForHandles;
    SnapshotMarkerReference<?>[] handles;
    int[] resolveOrder;
    DocumentText beforeText;
    DocumentText afterText;
    DocumentPatch patch;

    @Setup(Level.Trial)
    public void setUp() {
      var population = createRootPopulation();
      root = population.root();
      markerForHandles = population.markerForHandles();
      handles = population.handles();
      resolveOrder = buildResolveOrder();
      beforeText = DocumentText.createText(BENCHMARK_TEXT);
      int offset = DOCUMENT_LENGTH / 2;
      afterText = beforeText.insert(offset, "x");
      patch = DocumentPatch.simple(offset, offset, "x", 1, false);
    }
  }

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
      SnapshotRangeMarkerImpl marker = SnapshotRangeMarkerImpl.createRangeMarker(
        state.document,
        startOffset,
        startOffset + MARKER_LENGTH,
        NON_GREEDY_SPEC,
        false,
        DefaultMarkerPolicy.INSTANCE
      );
      state.createdMarkers[index] = marker;
      checksum ^= marker.getId();
    }

    return checksum;
  }

  /**
   * Resolves markers 4,000,000 times through {@link SnapshotRangeMarkerImpl}.
   *
   * <p>The precomputed access order visits every one of the 50,000 markers exactly 80 times in a permuted order. Each
   * loop iteration resolves one marker and consumes both resolved offsets through the returned checksum.</p>
   */
  @Benchmark
  public long resolve4MMarkers(ResolveState state) {
    SnapshotRangeMarkerImpl[] markers = state.markers;
    int[] resolveOrder = state.resolveOrder;
    long checksum = 0L;

    for (int index = 0; index < RESOLVE_CALLS; index++) {
      SnapshotRangeMarkerImpl marker = markers[resolveOrder[index]];
      checksum += (long)marker.getStartOffset() + marker.getEndOffset();
    }

    return checksum;
  }

  /**
   * Calls {@link PMarkerRoot#processRangeMarkersOverlappingWith} 2,000,000 times.
   *
   * <p>{@link IntersectionState#markersPerQuery} controls the exact output size {@code k}. The benchmark uses a
   * reusable consumer, so it does not allocate a capturing lambda for every query. The returned checksum consumes
   * every reported marker identity and the total number of reported markers.</p>
   *
   * <p>This benchmark targets the persistent interval index directly to isolate tree reporting from marker-handle
   * lookup and materialization.</p>
   */
  @Benchmark
  public long reportIntersecting2MQueries(IntersectionState state) {
    PMarkerRoot root = state.root;
    int[] queryStarts = state.queryStarts;
    int[] queryEnds = state.queryEnds;
    MarkerEntryAccumulator accumulator = state.accumulator;
    accumulator.reset();

    for (int index = 0; index < INTERSECTION_CALLS; index++) {
      root.processRangeMarkersOverlappingWith(queryStarts[index], queryEnds[index], 0, accumulator);
    }

    return accumulator.getChecksum() ^ accumulator.getCount();
  }

  /**
   * Calls {@link SnapshotMarkerRootStore#processRangeMarkersOverlappingWith} 2,000,000 times.
   *
   * <p>This uses the same marker population and query workload as {@link #reportIntersecting2MQueries}, but creates
   * and retains real marker handles and enumerates them through the store. The timed path therefore includes the
   * embedded weak-reference dereference and marker-handle materialization for every reported marker.</p>
   */
  @Benchmark
  public long enumerateIntersecting2MQueries(EngineIntersectionState state) {
    DocumentSnapshot snapshot = state.snapshot;
    int[] queryStarts = state.queryStarts;
    int[] queryEnds = state.queryEnds;
    RangeMarkerIdAccumulator accumulator = state.accumulator;
    accumulator.reset();

    for (int index = 0; index < INTERSECTION_CALLS; index++) {
      state.rootStore.processRangeMarkersOverlappingWith(
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
    SnapshotRangeMarkerImpl[] createdMarkers;

    /** A new mutable engine is required for every measured creation batch. */
    @Setup(Level.Invocation)
    public void setUp() {
      document = new DocumentImpl(BENCHMARK_TEXT);
      createdMarkers = new SnapshotRangeMarkerImpl[MARKER_COUNT];
    }
  }

  @State(Scope.Thread)
  public static class ResolveState {
    DocumentImpl document;
    SnapshotRangeMarkerImpl[] markers;
    int[] resolveOrder;

    /** Builds the 50,000-marker population once per fork, outside measured invocations. */
    @Setup(Level.Trial)
    public void setUp() {
      document = new DocumentImpl(BENCHMARK_TEXT);
      markers = new SnapshotRangeMarkerImpl[MARKER_COUNT];

      for (int index = 0; index < MARKER_COUNT; index++) {
        int startOffset = markerStart(index);
        markers[index] = SnapshotRangeMarkerImpl.createRangeMarker(
          document,
          startOffset,
          startOffset + MARKER_LENGTH,
          NON_GREEDY_SPEC,
          false,
          DefaultMarkerPolicy.INSTANCE
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
    SnapshotRangeMarkerImpl markerForHandles;
    int[] queryStarts;
    int[] queryEnds;
    final MarkerEntryAccumulator accumulator = new MarkerEntryAccumulator();

    /** Builds the root and deterministic query workload once per parameter value and fork. */
    @Setup(Level.Trial)
    public void setUp() {
      var population = createRootPopulation();
      root = population.root();
      markerForHandles = population.markerForHandles();

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
    SnapshotMarkerRootStore<?> rootStore;
    SnapshotRangeMarkerImpl[] markers;
    int[] queryStarts;
    int[] queryEnds;
    final RangeMarkerIdAccumulator accumulator = new RangeMarkerIdAccumulator();

    /** Builds and retains the real 50,000-marker population once per parameter value and fork. */
    @Setup(Level.Trial)
    public void setUp() {
      document = new DocumentImpl(BENCHMARK_TEXT);
      snapshot = document.getCore().snapshot();
      rootStore = document.getRangeMarkers().rootStore();
      markers = new SnapshotRangeMarkerImpl[MARKER_COUNT];

      for (int index = 0; index < MARKER_COUNT; index++) {
        int startOffset = markerStart(index);
        markers[index] = SnapshotRangeMarkerImpl.createRangeMarker(
          document,
          startOffset,
          startOffset + MARKER_LENGTH,
          NON_GREEDY_SPEC,
          false,
          DefaultMarkerPolicy.INSTANCE
        );
      }

      queryStarts = new int[INTERSECTION_CALLS];
      queryEnds = new int[INTERSECTION_CALLS];
      fillIntersectionQueries(markersPerQuery, queryStarts, queryEnds);
    }
  }

  /** Reusable callback used by the intersection benchmark. */
  public static final class MarkerEntryAccumulator implements Processor<PMarkerRoot.MarkerEntry> {
    private long checksum;
    private long count;

    @Override
    public boolean process(PMarkerRoot.MarkerEntry entry) {
      checksum += System.identityHashCode(entry.getHandle());
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
  private static final int ROOT_UPDATES = 1_000;

  private static final String BENCHMARK_TEXT = createBenchmarkText();
  private static final byte NON_GREEDY_SPEC = MarkerSpec.create(false, false, false);

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

    var options = new OptionsBuilder().parent(new CommandLineOptions(args));
    if (args.length == 0) {
      options.include("\\W" + SnapshotMarkerEngineBenchmark.class.getSimpleName() + ".*");
    }
    var classpath = System.getProperty("java.class.path");
    var classpathJar = createClasspathJar(classpath);
    try {
      System.setProperty("java.class.path", classpathJar.toString());
      new Runner(options.build()).run();
    }
    finally {
      System.setProperty("java.class.path", classpath);
      Files.deleteIfExists(classpathJar);
    }
  }

  private static Path createClasspathJar(String classpath) throws IOException {
    var entries = new StringJoiner(" ");
    for (var entry : classpath.split(File.pathSeparator)) {
      entries.add(new File(entry).toURI().toASCIIString());
    }
    var manifest = new Manifest();
    var attributes = manifest.getMainAttributes();
    attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
    attributes.put(Attributes.Name.CLASS_PATH, entries.toString());
    var jar = Files.createTempFile("snapshot-marker-benchmark-", ".jar");
    jar.toFile().deleteOnExit();
    try (var output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
      output.finish();
    }
    return jar;
  }

  private record RootPopulation(PMarkerRoot root, SnapshotMarkerReference<?>[] handles, SnapshotRangeMarkerImpl markerForHandles) {
  }

  private static SnapshotRangeMarkerImpl createMarkerForHandles() {
    return SnapshotRangeMarkerImpl.createRangeMarker(
      new DocumentImpl(""), 0, 0, NON_GREEDY_SPEC, false, DefaultMarkerPolicy.INSTANCE
    );
  }

  private static RootPopulation createRootPopulation() {
    var root = PMarkerRoot.empty();
    var markerForHandles = createMarkerForHandles();
    var handles = new SnapshotMarkerReference<?>[MARKER_COUNT];
    for (int index = 0; index < MARKER_COUNT; index++) {
      var start = markerStart(index);
      handles[index] = SnapshotMarkerReference.create(markerForHandles, false);
      root = root.insert(handles[index], start, start + MARKER_LENGTH, NON_GREEDY_SPEC, (byte)0,
                         0, DefaultMarkerPolicy.INSTANCE);
    }
    return new RootPopulation(root, handles, markerForHandles);
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
