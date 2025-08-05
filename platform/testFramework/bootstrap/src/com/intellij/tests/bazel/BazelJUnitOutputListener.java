/**
 * This file was originally part of [rules_jvm] (https://github.com/bazel-contrib/rules_jvm)
 * Original source:
 * https://github.com/bazel-contrib/rules_jvm/blob/201fa7198cfd50ae4d686715651500da656b368a/java/src/com/github/bazel_contrib/contrib_rules_jvm/junit5/BazelJUnitOutputListener.java
 * Licensed under the Apache License, Version 2.0
 */
package com.intellij.tests.bazel;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

public class BazelJUnitOutputListener implements TestExecutionListener, Closeable {
  public static final Logger LOG = Logger.getLogger(BazelJUnitOutputListener.class.getName());
  private final XMLStreamWriter xml;

  // When grouping test cases in matchTestCasesToSuites_locked, we want to group by the
  // closes parent that is a class or nested-class if there is one.
  // This means using nested-class for @Nested test case and class for other test cases.
  // See https://docs.junit.org/current/user-guide/#running-tests-discovery-selectors
  // We ignore suites here to align with Bazel's XmlWriter for JUnitRunner.
  private static final Set<String> SegmentClassTypes = Set.of("class", "nested-class");

  private final Object resultsLock = new Object();
  // Commented out to avoid adding a dependency to building the test runner.
  // This is really just documentation until someone actually turns on a static analyser.
  // If they do, we can decide whether we want to pick up the dependency.
  // @GuardedBy("resultsLock")
  private final Map<UniqueId, TestData> results = new ConcurrentHashMap<>();
  private TestPlan testPlan;

  // If we have already closed this listener, we shouldn't write any more XML.
  private final AtomicBoolean hasClosed = new AtomicBoolean();
  // Whether test-running was interrupted (e.g. because our tests timed out and we got SIGTERM'd)
  // and when writing results we want to flush any pending tests as interrupted,
  // rather than ignoring them because they're incomplete.
  private final AtomicBoolean wasInterrupted = new AtomicBoolean();

  public BazelJUnitOutputListener(Path xmlOut) {
    try {
      Files.createDirectories(xmlOut.getParent());
      // Use LazyFileWriter to defer file creation until the first write operation.
      // This prevents premature file creation in cases where the JVM terminates abruptly
      // before any content is written. If no output is generated, Bazel has custom logic
      // to create the XML file from test.log, but this logic only activates if the
      // output file does not already exist.
      xml = XMLOutputFactory.newFactory().createXMLStreamWriter(new LazyFileWriter(xmlOut));
      xml.writeStartDocument("UTF-8", "1.0");
    } catch (IOException | XMLStreamException e) {
      throw new IllegalStateException("Unable to create output file", e);
    }
  }

  @Override
  public void testPlanExecutionStarted(TestPlan testPlan) {
    this.testPlan = testPlan;

    try {
      // Closed when we call `testPlanExecutionFinished`
      xml.writeStartElement("testsuites");
    } catch (XMLStreamException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void testPlanExecutionFinished(TestPlan testPlan) {
    if (this.testPlan == null) {
      throw new IllegalStateException("Test plan is not currently executing");
    }

    try {
      // Closing `testsuites` element
      xml.writeEndElement();
    } catch (XMLStreamException e) {
      throw new RuntimeException(e);
    }

    this.testPlan = null;
  }

  // Requires the caller to have acquired resultsLock.
  // Commented out to avoid adding a dependency to building the test runner.
  // This is really just documentation until someone actually turns on a static analyser.
  // If they do, we can decide whether we want to pick up the dependency.
  // @GuardedBy("resultsLock")
  private Map<TestData, List<TestData>> matchTestCasesToSuites_locked(
    List<TestData> testCases, boolean includeIncompleteTests) {
    Map<TestData, List<TestData>> knownSuites = new HashMap<>();

    // Find the containing test suites for the test cases.
    for (TestData testCase : testCases) {
      if (!testCase.getId().isTest()
          || (!includeIncompleteTests && testCase.getDuration() == null)) {
        // If this case is not a test or there is no duration and includeIncompleteTests is not set,
        // skip this test case
        continue;
      }

      // Default to the current testCase parent id
      Optional<TestData> parent = testCase.getId().getParentIdObject().map(results::get);

      // Loop over the segments in reverse to find this test case's class parent. We utilize the
      // closest parent that is either a class or nested class when nested classes are involved.
      // If no parent exists that is a class, then we default to the current test case.
      List<UniqueId.Segment> segments = testCase.getId().getUniqueIdObject().getSegments();
      for (int i = segments.size() - 2; i >= 0; i--) {
        if (SegmentClassTypes.contains(segments.get(i).getType()) || parent.isEmpty()) {
          break;
        }

        parent = parent.get().getId().getParentIdObject().map(results::get);
      }

      // If no class or nested-class segment was found, default to the current test case
      knownSuites.computeIfAbsent(parent.orElse(testCase), id -> new ArrayList<>()).add(testCase);
    }

    return knownSuites;
  }

  // Requires the caller to have acquired resultsLock.
  // Commented out to avoid adding a dependency to building the test runner.
  // This is really just documentation until someone actually turns on a static analyser.
  // If they do, we can decide whether we want to pick up the dependency.
  // @GuardedBy("resultsLock")
  private List<TestData> findTestCases_locked(String engineId) {
    return results.values().stream()
      // Ignore test plan roots. These are always the engine being used.
      .filter(result -> !testPlan.getRoots().contains(result.getId()))
      .filter(
        result ->
          engineId == null
          || result
            .getId()
            .getUniqueIdObject()
            .getEngineId()
            .map(engineId::equals)
            .orElse(false))
      .collect(Collectors.toList());
  }

  @Override
  public void dynamicTestRegistered(TestIdentifier testIdentifier) {
    getResult(testIdentifier).setDynamic(true);
  }

  @Override
  public void executionSkipped(TestIdentifier testIdentifier, String reason) {
    getResult(testIdentifier).mark(TestExecutionResult.aborted(null)).skipReason(reason);
    outputIfTestRootIsComplete(testIdentifier);
  }

  @Override
  public void executionStarted(TestIdentifier testIdentifier) {
    getResult(testIdentifier).started();
  }

  @Override
  public void executionFinished(
    TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
    getResult(testIdentifier).mark(testExecutionResult);
    outputIfTestRootIsComplete(testIdentifier);
  }

  private void outputIfTestRootIsComplete(TestIdentifier testIdentifier) {
    if (!testPlan.getRoots().contains(testIdentifier)) {
      return;
    }

    output(false, testIdentifier.getUniqueIdObject().getEngineId().orElse(null));
  }

  private void output(boolean includeIncompleteTests, /*@Nullable*/ String engineId) {
    synchronized (this.resultsLock) {
      List<TestData> testCases = findTestCases_locked(engineId);
      Map<TestData, List<TestData>> testSuites =
        matchTestCasesToSuites_locked(testCases, includeIncompleteTests);

      // Write the results
      try {
        for (Map.Entry<TestData, List<TestData>> suiteAndTests : testSuites.entrySet()) {
          TestData suite = suiteAndTests.getKey();
          List<TestData> tests = suiteAndTests.getValue();
          if (suite.getResult() != null
              && suite.getResult().getStatus() != TestExecutionResult.Status.SUCCESSFUL) {
            // If a test suite fails or is skipped, all its tests must be included in the XML output
            // with the same result as the suite, since the XML format does not support marking a
            // suite as failed or skipped. This aligns with Bazel's XmlWriter for JUnitRunner.
            getTestsFromSuite(suite.getId())
              .forEach(
                testIdentifier -> {
                  TestData test = results.get(testIdentifier.getUniqueIdObject());
                  if (test == null) {
                    // add test to results.
                    test = getResult(testIdentifier);
                    tests.add(test);
                  }
                  test.mark(suite.getResult()).skipReason(suite.getSkipReason());
                });
          }
          new TestSuiteXmlRenderer(testPlan).toXml(xml, suite, tests);
        }
      } catch (XMLStreamException e) {
        throw new RuntimeException(e);
      }

      // Delete the results we've used to conserve memory. This is safe to do
      // since we only do this when the test root is complete, so we know that
      // we won't be adding to the list of suites and test cases for that root
      // (because tests and containers are arranged in a hierarchy --- the
      // containers only complete when all the things they contain are
      // finished. We are leaving all the test data that we have _not_ written
      // to the XML file.
      Stream.concat(testCases.stream(), testSuites.keySet().stream())
        .forEach(data -> results.remove(data.getId().getUniqueIdObject()));
    }
  }

  private List<TestIdentifier> getTestsFromSuite(TestIdentifier suiteIdentifier) {
    return testPlan.getChildren(suiteIdentifier).stream()
      .flatMap(
        testIdentifier -> {
          if (testIdentifier.isContainer()) {
            return getTestsFromSuite(testIdentifier).stream();
          }
          return Stream.of(testIdentifier);
        })
      .collect(Collectors.toList());
  }

  @Override
  public void reportingEntryPublished(TestIdentifier testIdentifier, ReportEntry entry) {
    getResult(testIdentifier).addReportEntry(entry);
  }

  private TestData getResult(TestIdentifier id) {
    synchronized (resultsLock) {
      return results.computeIfAbsent(id.getUniqueIdObject(), ignored -> new TestData(id));
    }
  }

  public void closeForInterrupt() {
    wasInterrupted.set(true);
    close();
  }

  @Override
  public void close() {
    if (hasClosed.getAndSet(true)) {
      return;
    }
    if (wasInterrupted.get()) {
      output(true, null);
    }
    try {
      xml.writeEndDocument();
      xml.close();
    } catch (XMLStreamException e) {
      LOG.log(Level.SEVERE, "Unable to close xml output", e);
    }
  }

  static class LazyFileWriter extends Writer {
    private final Path path;
    private BufferedWriter delegate;
    private boolean isCreated = false;

    public LazyFileWriter(Path path) {
      this.path = path;
    }

    private void createWriterIfNeeded() throws IOException {
      if (!isCreated) {
        delegate = Files.newBufferedWriter(path, UTF_8);
        isCreated = true;
      }
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
      createWriterIfNeeded();
      delegate.write(cbuf, off, len);
    }

    @Override
    public void flush() throws IOException {
      if (isCreated) {
        delegate.flush();
      }
    }

    @Override
    public void close() throws IOException {
      if (isCreated) {
        delegate.close();
      }
    }
  }
}