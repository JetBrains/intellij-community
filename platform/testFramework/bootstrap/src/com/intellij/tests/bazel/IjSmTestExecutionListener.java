// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests.bazel;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.CompositeTestSource;
import org.junit.platform.engine.support.descriptor.FileSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.MultipleFailuresError;

import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Lightweight IntelliJ Service Messages emitter for JUnit 5 execution.
 *
 * This listener prints TeamCity/IntelliJ SM messages (##teamcity[...]) to stdout so that
 * the IDE can build a live test tree and show progress while tests are running under Bazel.
 *
 * The goal is to keep dependencies to a minimum; therefore, we format SM lines directly
 * and implement a minimal escaping routine compatible with the protocol.
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public final class IjSmTestExecutionListener implements TestExecutionListener {
  private TestPlan testPlan;

  // Registry of discovered nodes for quick lookup by id (used for stdout attribution and base attrs)
  private final Map<String, TestIdentifier> nodesById = new HashMap<>();

  private final Map<String, Long> testStartNanos = new HashMap<>();
  private final Set<String> startedSuites = new HashSet<>();
  private final Set<String> startedTests = new HashSet<>();

  // Stdout/stderr capture
  private PrintStream originalOut;
  private PrintStream originalErr;
  private CapturingPrintStream capturingOut;
  private CapturingPrintStream capturingErr;

  // Current executing test per thread (best-effort attribution)
  private final ThreadLocal<String> currentTestIdTL = new ThreadLocal<>();

  @Override
  public void testPlanExecutionStarted(TestPlan testPlan) {
    this.testPlan = testPlan;

    // Index all known nodes
    nodesById.clear();
    for (TestIdentifier root : testPlan.getRoots()) {
      indexRecursively(root);
    }

    // Install stdout/stderr capturing to attribute output to running tests
    try {
      originalOut = System.out;
      originalErr = System.err;
      capturingOut = new CapturingPrintStream(originalOut, false, this);
      capturingErr = new CapturingPrintStream(originalErr, true, this);
      System.setOut(capturingOut);
      System.setErr(capturingErr);
    } catch (Throwable ignore) {
      // fail-safe: keep originals
    }

    // Signal start of testing to IDE
    serviceMessage("enteredTheMatrix", Collections.emptyMap());
    serviceMessage("testingStarted", Collections.emptyMap());

    // Pre-emit full test tree disabled for Bazel SM converter compatibility
    // Previously emitted testTreeStarted/testTreeNode/testTreeEnded which some converters don't handle and spam console.
    // We rely on live execution events (testSuiteStarted/testStarted) instead.
  }

  @Override
  public void dynamicTestRegistered(TestIdentifier testIdentifier) {
    // Register dynamic node for later lookup; avoid emitting testTreeNode to prevent console spam
    nodesById.put(getId(testIdentifier), testIdentifier);
  }

  @Override
  public void executionStarted(TestIdentifier testIdentifier) {
    String id = getId(testIdentifier);
    if (testIdentifier.isContainer()) {
      if (!startedSuites.contains(id)) {
        Map<String, String> attrs = baseAttrs(testIdentifier);
        // Provide location hint for class-like containers
        String location = getLocationHint(testIdentifier);
        if (location != null) attrs.put("locationHint", location);
        serviceMessage("testSuiteStarted", attrs);
        startedSuites.add(id);
      }
    }
    else if (testIdentifier.isTest()) {
      Map<String, String> attrs = baseAttrs(testIdentifier);
      String location = getLocationHint(testIdentifier);
      if (location != null) attrs.put("locationHint", location);
      attrs.put("captureStandardOutput", "true");
      serviceMessage("testStarted", attrs);
      startedTests.add(id);
      testStartNanos.put(id, System.nanoTime());
      // Attribute stdout/stderr on the current thread to this test while it runs
      currentTestIdTL.set(id);
    }
  }

  @Override
  public void executionSkipped(TestIdentifier testIdentifier, String reason) {
    if (testIdentifier.isTest()) {
      Map<String, String> attrs = baseAttrs(testIdentifier);
      if (reason != null && !reason.isEmpty()) attrs.put("message", reason);
      serviceMessage("testIgnored", attrs);
    } else if (testIdentifier.isContainer()) {
      // Emit started/ignored/finished trio for skipped containers to show up in UI
      executionStarted(testIdentifier);
      Map<String, String> attrs = baseAttrs(testIdentifier);
      if (reason != null && !reason.isEmpty()) attrs.put("message", reason);
      serviceMessage("testIgnored", attrs);
      executionFinished(testIdentifier, TestExecutionResult.successful());
    }
  }

  @Override
  public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
    String id = getId(testIdentifier);
    if (testIdentifier.isContainer()) {
      // If container failed/aborted, report a synthetic failure test under the suite
      Optional<Throwable> throwable = testExecutionResult.getThrowable();
      if ((testExecutionResult.getStatus() == TestExecutionResult.Status.FAILED
           || testExecutionResult.getStatus() == TestExecutionResult.Status.ABORTED) && throwable.isPresent()) {
        String syntheticId = id + "/[suite-setup]";
        String parentId = id;
        String suiteSetupName = "<suite setup>";
        Map<String, String> start = new LinkedHashMap<>();
        start.put("name", suiteSetupName);
        start.put("nodeId", syntheticId);
        start.put("parentNodeId", parentId);
        String loc = getLocationHint(testIdentifier);
        if (loc != null) start.put("locationHint", loc);
        start.put("captureStandardOutput", "true");
        serviceMessage("testStarted", start);

        Throwable t = throwable.get();
        Map<String, String> fail = new LinkedHashMap<>();
        fail.put("name", suiteSetupName);
        fail.put("nodeId", syntheticId);
        fail.put("parentNodeId", parentId);
        String msg = t.getMessage();
        if (msg != null && !msg.isEmpty()) fail.put("message", msg);
        fail.put("details", stackTraceToString(t));
        serviceMessage("testFailed", fail);

        Map<String, String> fin = new LinkedHashMap<>();
        fin.put("name", suiteSetupName);
        fin.put("nodeId", syntheticId);
        fin.put("parentNodeId", parentId);
        serviceMessage("testFinished", fin);
      }

      if (startedSuites.contains(id)) {
        Map<String, String> attrs = baseAttrs(testIdentifier);
        serviceMessage("testSuiteFinished", attrs);
        startedSuites.remove(id);
      }
      return;
    }

    if (testIdentifier.isTest()) {
      Optional<Throwable> throwable = testExecutionResult.getThrowable();
      if (capturingOut != null) capturingOut.flushBufferForCurrentThread();
      if (capturingErr != null) capturingErr.flushBufferForCurrentThread();
      currentTestIdTL.remove();
      if (testExecutionResult.getStatus() == TestExecutionResult.Status.FAILED && throwable.isPresent()) {
        Throwable t = throwable.get();
        if (t instanceof MultipleFailuresError) {
          for (Throwable sub : ((MultipleFailuresError) t).getFailures()) {
            Map<String, String> fail = baseAttrs(testIdentifier);
            String message = sub.getMessage();
            if (message != null && !message.isEmpty()) fail.put("message", message);
            if (sub instanceof AssertionFailedError) {
              AssertionFailedError afe = (AssertionFailedError) sub;
              if (afe.isExpectedDefined() || afe.isActualDefined()) {
                fail.put("type", "comparisonFailure");
                String expected = afe.isExpectedDefined() && afe.getExpected() != null ? String.valueOf(afe.getExpected().getValue()) : "";
                String actual = afe.isActualDefined() && afe.getActual() != null ? String.valueOf(afe.getActual().getValue()) : "";
                fail.put("expected", expected);
                fail.put("actual", actual);
              }
            }
            fail.put("details", stackTraceToString(sub));
            serviceMessage("testFailed", fail);
          }
        } else {
          Map<String, String> fail = baseAttrs(testIdentifier);
          String message = t.getMessage();
          if (message != null && !message.isEmpty()) fail.put("message", message);
          if (t instanceof AssertionFailedError) {
            AssertionFailedError afe = (AssertionFailedError) t;
            if (afe.isExpectedDefined() || afe.isActualDefined()) {
              fail.put("type", "comparisonFailure");
              String expected = afe.isExpectedDefined() && afe.getExpected() != null ? String.valueOf(afe.getExpected().getValue()) : "";
              String actual = afe.isActualDefined() && afe.getActual() != null ? String.valueOf(afe.getActual().getValue()) : "";
              fail.put("expected", expected);
              fail.put("actual", actual);
            }
          }
          fail.put("details", stackTraceToString(t));
          serviceMessage("testFailed", fail);
        }
      }
      else if (testExecutionResult.getStatus() == TestExecutionResult.Status.ABORTED) {
        Map<String, String> attrs = baseAttrs(testIdentifier);
        attrs.put("message", "Test aborted");
        testExecutionResult.getThrowable().ifPresent(t -> attrs.put("details", stackTraceToString(t)));
        serviceMessage("testFailed", attrs);
      }

      // finished with duration
      Map<String, String> fin = baseAttrs(testIdentifier);
      Long startNs = testStartNanos.remove(id);
      if (startNs != null) {
        long durationMs = Math.max(0, (System.nanoTime() - startNs) / 1_000_000);
        fin.put("duration", Long.toString(durationMs));
      }
      serviceMessage("testFinished", fin);
      startedTests.remove(id);
    }
  }

  @Override
  public void reportingEntryPublished(TestIdentifier testIdentifier, ReportEntry entry) {
    if (!testIdentifier.isTest()) return;
    Map<String, String> attrs = baseAttrs(testIdentifier);
    Map<String, String> kv = entry != null ? entry.getKeyValuePairs() : null;
    boolean emitted = false;
    if (kv != null && !kv.isEmpty()) {
      String stderr = firstNonEmpty(kv, Arrays.asList("stderr", "stdErr", "err"));
      if (stderr != null) {
        Map<String, String> a = new LinkedHashMap<>(attrs);
        a.put("out", stderr);
        serviceMessage("testStdErr", a);
        emitted = true;
      }
      String stdout = firstNonEmpty(kv, Arrays.asList("stdout", "stdOut", "out"));
      if (stdout != null) {
        Map<String, String> a = new LinkedHashMap<>(attrs);
        a.put("out", stdout);
        serviceMessage("testStdOut", a);
        emitted = true;
      }
    }
    if (!emitted) {
      String text = formatReportEntry(entry);
      if (!text.isEmpty()) {
        attrs.put("out", text);
        serviceMessage("testStdOut", attrs);
      }
    }
  }

  @Override
  public void testPlanExecutionFinished(TestPlan testPlan) {
    // Close any still-open suites/tests defensively
    for (String id : new ArrayList<>(startedTests)) {
      Map<String, String> fin = baseAttrsById(id);
      serviceMessage("testFinished", fin);
    }
    for (String id : new ArrayList<>(startedSuites)) {
      Map<String, String> fin = baseAttrsById(id);
      serviceMessage("testSuiteFinished", fin);
    }
    startedTests.clear();
    startedSuites.clear();

    serviceMessage("testingFinished", Collections.emptyMap());
    this.testPlan = null;

    // Restore streams
    tryRestoreStreams();
  }

  private Map<String, String> baseAttrs(TestIdentifier testIdentifier) {
    Map<String, String> attrs = new LinkedHashMap<>();
    attrs.put("name", testIdentifier.getDisplayName());
    String id = getId(testIdentifier);
    attrs.put("nodeId", id);
    String parentId = getParentId(testIdentifier);
    attrs.put("parentNodeId", parentId);
    String metainfo = getMetaInfo(testIdentifier);
    attrs.put("metainfo", metainfo);
    return attrs;
  }

  private String getId(TestIdentifier id) {
    return id.getUniqueId();
  }

  private String getParentId(TestIdentifier id) {
    if (testPlan == null) return "0";
    return testPlan.getParent(id).map(this::getId).orElse("0");
  }

  private static String getMetaInfo(TestIdentifier id) {
    // Keep simple: include type information
    String type = id.isContainer() ? (id.isTest() ? "test+container" : "container") : (id.isTest() ? "test" : "unknown");
    return type;
  }

  private static String stackTraceToString(Throwable t) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    t.printStackTrace(pw);
    pw.flush();
    return sw.toString();
  }

  private static String formatReportEntry(ReportEntry entry) {
    if (entry == null) return "";
    Map<String, String> kv = entry.getKeyValuePairs();
    if (kv == null || kv.isEmpty()) return "";
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (Map.Entry<String, String> e : kv.entrySet()) {
      if (!first) sb.append(" \u00B7 "); // middle dot separator
      sb.append(e.getKey()).append(": ").append(e.getValue());
      first = false;
    }
    return sb.toString();
  }

  private static String firstNonEmpty(Map<String, String> map, java.util.List<String> keys) {
    for (String k : keys) {
      String v = map.get(k);
      if (v != null && !v.isEmpty()) return v;
    }
    return null;
  }

  private static String getLocationHint(TestIdentifier id) {
    Optional<TestSource> source = id.getSource();
    if (source.isEmpty()) return null;
    TestSource s = source.get();
    if (s instanceof MethodSource) {
      MethodSource ms = (MethodSource) s;
      String className = ms.getClassName();
      String methodName = ms.getMethodName();
      if (className != null && methodName != null) {
        return "java:test://" + className + "/" + methodName;
      }
    }
    else if (s instanceof ClassSource) {
      ClassSource cs = (ClassSource) s;
      String className = cs.getClassName();
      if (className != null) return "java:suite://" + className;
    }
    else if (s instanceof FileSource) {
      FileSource fs = (FileSource) s;
      return "file://" + fs.getFile();
    }
    else if (s instanceof CompositeTestSource) {
      // Fallback to first known source inside composite
      for (TestSource child : ((CompositeTestSource) s).getSources()) {
        if (child instanceof MethodSource) {
          MethodSource ms = (MethodSource) child;
          String className = ms.getClassName();
          String methodName = ms.getMethodName();
          if (className != null && methodName != null) {
            return "java:test://" + className + "/" + methodName;
          }
        }
        else if (child instanceof ClassSource) {
          ClassSource cs = (ClassSource) child;
          String className = cs.getClassName();
          if (className != null) return "java:suite://" + className;
        }
      }
    }
    return null;
  }

  private void indexRecursively(TestIdentifier id) {
    nodesById.put(getId(id), id);
    if (testPlan == null) return;
    for (TestIdentifier child : testPlan.getChildren(id)) {
      indexRecursively(child);
    }
  }

  private Map<String, String> baseAttrsById(String nodeId) {
    TestIdentifier id = nodesById.get(nodeId);
    if (id != null) return baseAttrs(id);
    Map<String, String> attrs = new LinkedHashMap<>();
    // Fallback: derive minimal required attributes from UniqueId so SM parser is satisfied
    attrs.put("name", deriveNameFromUniqueId(nodeId));
    attrs.put("nodeId", nodeId);
    String parent = deriveParentIdFromUniqueId(nodeId);
    if (parent != null && !parent.isEmpty()) attrs.put("parentNodeId", parent);
    return attrs;
    }

  private static String deriveNameFromUniqueId(String uid) {
    if (uid == null || uid.isEmpty()) return "unknown";
    // UniqueId format example: [engine:junit-jupiter]/[class:com.acme.MyTest]/[method:test()]
    // Extract last segment content between '[' and ']', then take substring after ':' if present
    int end = uid.lastIndexOf(']');
    int start = uid.lastIndexOf("[");
    if (start >= 0 && end > start) {
      String seg = uid.substring(start + 1, end); // e.g., engine:junit-jupiter or class:com.acme.MyTest
      int colon = seg.indexOf(':');
      String value = colon >= 0 ? seg.substring(colon + 1) : seg;
      // For class FQCN, use simple name for readability
      int lastDot = value.lastIndexOf('.');
      if (lastDot >= 0 && colon >= 0 && seg.startsWith("class:")) {
        return value.substring(lastDot + 1);
      }
      return value;
    }
    return uid; // fallback to whole uid
  }

  private static String deriveParentIdFromUniqueId(String uid) {
    if (uid == null) return null;
    int slash = uid.lastIndexOf('/')
;    if (slash < 0) return null;
    return uid.substring(0, slash);
  }

  private void emitStd(boolean err, String nodeId, String text) {
    if (text == null || text.isEmpty()) return;
    Map<String, String> attrs = baseAttrsById(nodeId);
    attrs.put("out", text);
    serviceMessage(err ? "testStdErr" : "testStdOut", attrs);
  }

  public void closeForInterrupt() {
    try {
      // Mark started tests as interrupted and finish them
      for (String id : new ArrayList<>(startedTests)) {
        Map<String, String> fail = baseAttrsById(id);
        fail.put("message", "Interrupted");
        serviceMessage("testFailed", fail);
        Map<String, String> fin = baseAttrsById(id);
        serviceMessage("testFinished", fin);
      }
      startedTests.clear();

      // Finish any open suites
      for (String id : new ArrayList<>(startedSuites)) {
        Map<String, String> fin = baseAttrsById(id);
        serviceMessage("testSuiteFinished", fin);
      }
      startedSuites.clear();

      // Ensure closing marker
      serviceMessage("testingFinished", Collections.emptyMap());
    } catch (Throwable ignore) {
      // best-effort
    } finally {
      // Restore original streams
      tryRestoreStreams();
    }
  }

  private void tryRestoreStreams() {
    try {
      if (originalOut != null) System.setOut(originalOut);
      if (originalErr != null) System.setErr(originalErr);
    } catch (Throwable ignore) {
    }
  }

  private void serviceMessage(String name, Map<String, String> attrs) {
    StringBuilder sb = new StringBuilder();
    sb.append("##teamcity[").append(name);
    for (Map.Entry<String, String> e : attrs.entrySet()) {
      if (e.getValue() == null) continue;
      sb.append(' ').append(e.getKey()).append("='").append(escapeTc(e.getValue())).append("'");
    }
    sb.append(']');
    PrintStream out = (originalOut != null) ? originalOut : System.out;
    out.println(sb);
  }

  // Minimal TeamCity service messages escaping
  private static String escapeTc(String s) {
    StringBuilder r = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '\n': r.append("|n"); break;
        case '\r': r.append("|r"); break;
        case '\u0085': r.append("|x"); break; // next line
        case '\u2028': r.append("|l"); break; // line sep
        case '\u2029': r.append("|p"); break; // paragraph sep
        case '|': r.append("||"); break;
        case '\'': r.append("|'"); break;
        case '[': r.append("|["); break;
        case ']': r.append("|]"); break;
        default: r.append(c);
      }
    }
    return r.toString();
  }

  // Lightweight capturing PrintStream that attributes output to the current test via ThreadLocal
  private static final class CapturingPrintStream extends PrintStream {
    private final PrintStream original;
    private final boolean isErr;
    private final IjSmTestExecutionListener owner;
    private final ThreadLocal<StringBuilder> buffer = ThreadLocal.withInitial(StringBuilder::new);

    private CapturingPrintStream(PrintStream original, boolean isErr, IjSmTestExecutionListener owner) {
      super(new OutputStream() {
        @Override
        public void write(int b) { }
      }, true);
      this.original = original;
      this.isErr = isErr;
      this.owner = owner;
    }

    @Override
    public void write(byte[] buf, int off, int len) {
      if (len <= 0) return;
      String s = new String(buf, off, len);
      handleText(s);
    }

    @Override
    public void write(int b) {
      handleText(new String(new byte[]{(byte)b}));
    }

    @Override
    public void flush() {
      flushBufferForCurrentThread();
      original.flush();
    }

    @Override
    public void close() {
      flushBufferForCurrentThread();
      original.close();
      super.close();
    }

    void flushBufferForCurrentThread() {
      String id = owner.currentTestIdTL.get();
      if (id == null) return;
      StringBuilder sb = buffer.get();
      if (sb.length() > 0) {
        owner.emitStd(isErr, id, sb.toString());
        sb.setLength(0);
      }
    }

    private void handleText(String s) {
      String id = owner.currentTestIdTL.get();
      if (id == null || !owner.startedTests.contains(id)) {
        // Not inside a known test: forward to original stream as-is
        original.print(s);
        return;
      }
      StringBuilder sb = buffer.get();
      int start = 0;
      for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        if (c == '\n') {
          sb.append(s, start, i + 1);
          owner.emitStd(isErr, id, sb.toString());
          sb.setLength(0);
          start = i + 1;
        }
      }
      if (start < s.length()) {
        sb.append(s, start, s.length());
      }
    }
  }
}
