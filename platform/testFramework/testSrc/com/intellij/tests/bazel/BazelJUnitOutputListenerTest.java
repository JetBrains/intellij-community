// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests.bazel;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

class BazelJUnitOutputListenerTest {
  //Disable test-purpose classes to prevent them from being executed as actual tests.
  private static final String SCENARIO_DISABLED_REASON = "Executed explicitly by BazelJUnitOutputListenerTest";

  @Test
  void reportsClassConfigurationFailureWhenBeforeAllFails(@TempDir Path tempDir) throws Exception {
    Document xml = executeAndReadXml(BeforeAllFailureScenario.class, tempDir.resolve("beforeAllFailure.xml"));

    Element suite = getOnlyElement(xml, "testsuite");
    assertThat(suite.getAttribute("tests")).isEqualTo("3");
    assertThat(suite.getAttribute("failures")).isEqualTo("0");
    assertThat(suite.getAttribute("errors")).isEqualTo("1");
    assertThat(suite.getAttribute("skipped")).isEqualTo("2");
    assertThat(getTestCaseNames(xml)).contains("firstTest", "secondTest");
    assertThat(countTestCasesWithTag(xml, "error")).isEqualTo(1);
    assertThat(getOnlyTestCase(xml, "firstTest").getElementsByTagName("skipped").getLength()).isEqualTo(1);
    assertThat(getOnlyTestCase(xml, "secondTest").getElementsByTagName("skipped").getLength()).isEqualTo(1);
  }

  @Test
  void preservesExecutedTestsWhenAfterAllFails(@TempDir Path tempDir) throws Exception {
    Document xml = executeAndReadXml(AfterAllFailureScenario.class, tempDir.resolve("afterAllFailure.xml"));

    Element suite = getOnlyElement(xml, "testsuite");
    assertThat(suite.getAttribute("tests")).isEqualTo("2");
    assertThat(suite.getAttribute("failures")).isEqualTo("0");
    assertThat(suite.getAttribute("errors")).isEqualTo("1");
    assertThat(suite.getAttribute("skipped")).isEqualTo("0");
    assertThat(getTestCaseNames(xml)).contains("successfulTest");
    assertThat(countTestCasesWithTag(xml, "error")).isEqualTo(1);
    assertThat(getOnlyTestCase(xml, "successfulTest").getElementsByTagName("error").getLength()).isEqualTo(0);
    assertThat(getOnlyTestCase(xml, "successfulTest").getElementsByTagName("skipped").getLength()).isEqualTo(0);
  }

  /** An interrupted run writes the tests that are not written yet. The descendants of a failed class are written already. */
  @Test
  void writesNoTestTwiceWhenTheRunIsInterrupted(@TempDir Path tempDir) throws Exception {
    List<String> closed = getTestCaseNames(executeAndReadXml(BeforeAllFailureWithNestedScenario.class, tempDir.resolve("closed.xml")));
    List<String> interrupted = getTestCaseNames(executeAndReadXml(BeforeAllFailureWithNestedScenario.class,
                                                                  tempDir.resolve("interrupted.xml"), true));

    assertThat(closed).isNotEmpty().doesNotHaveDuplicates();
    assertThat(interrupted).containsExactlyInAnyOrderElementsOf(closed);
  }

  @Test
  void includesParameterizedMethodNameInXmlReport(@TempDir Path tempDir) throws Exception {
    Document xml = executeAndReadXml(ParameterizedScenario.class, tempDir.resolve("parameterized.xml"));

    assertThat(getTestCaseNames(xml)).containsExactlyInAnyOrder(
      "firstParameterizedTest.FIRST",
      "firstParameterizedTest.SECOND",
      "secondParameterizedTest.FIRST",
      "secondParameterizedTest.SECOND"
    );
  }

  @Test
  void treatsAbortedTestsAsSkipped(@TempDir Path tempDir) throws Exception {
    Document xml = executeAndReadXml(AbortedScenario.class, tempDir.resolve("aborted.xml"));

    Element suite = getOnlyElement(xml, "testsuite");
    assertThat(suite.getAttribute("tests")).isEqualTo("2");
    assertThat(suite.getAttribute("failures")).isEqualTo("0");
    assertThat(suite.getAttribute("errors")).isEqualTo("0");
    assertThat(suite.getAttribute("skipped")).isEqualTo("1");
    assertThat(getOnlyTestCase(xml, "abortedTest").getElementsByTagName("skipped").getLength()).isEqualTo(1);
    assertThat(getOnlyTestCase(xml, "successfulTest").getElementsByTagName("skipped").getLength()).isEqualTo(0);
  }

  @Test
  void includesComparisonDiffForEachNestedFailureInAssertAll(@TempDir Path tempDir) throws Exception {
    Document xml = executeAndReadXml(MultipleComparisonFailuresScenario.class, tempDir.resolve("assertAll.xml"));

    Element testCase = getOnlyTestCase(xml, "assertAllWithTwoComparisonFailures");
    NodeList failures = testCase.getElementsByTagName("failure");
    assertThat(failures.getLength()).isEqualTo(1);

    String failureText = failures.item(0).getTextContent();
    assertThat(failureText).contains("expected-1", "actual-1", "expected-2", "actual-2");
  }

  private static Document executeAndReadXml(Class<?> scenarioRootClass, Path xmlOutputFile) throws Exception {
    return executeAndReadXml(scenarioRootClass, xmlOutputFile, false);
  }

  private static Document executeAndReadXml(Class<?> scenarioRootClass, Path xmlOutputFile, boolean interruptAfterJupiter)
    throws Exception {
    // Keep this helper isolated from globally auto-registered launcher session listeners:
    // they enable IDE-wide test extensions and first/last-in-suite leak checks that are not
    // part of the XML listener contract under test here.
    var launcher = LauncherFactory.create(LauncherConfig.builder()
                                         .enableLauncherSessionListenerAutoRegistration(false)
                                         .build());
    var request = LauncherDiscoveryRequestBuilder.request()
      .selectors(selectClass(scenarioRootClass))
      .configurationParameter("junit.jupiter.conditions.deactivate", "org.junit.*DisabledCondition")
      .build();

    try (var listener = new BazelJUnitOutputListener(xmlOutputFile)) {
      launcher.registerTestExecutionListeners(interruptAfterJupiter ? interruptedAfterJupiter(listener) : listener);
      launcher.execute(request);
    }

    var documentBuilderFactory = DocumentBuilderFactory.newInstance();
    documentBuilderFactory.setNamespaceAware(false);
    return documentBuilderFactory.newDocumentBuilder().parse(xmlOutputFile.toFile());
  }

  /**
   * As the shutdown hook of JUnit5BazelRunner after the Jupiter engine: its root is written, and the test plan has not
   * finished yet. Another engine, such as Vintage, can finish its root before Jupiter.
   */
  private static TestExecutionListener interruptedAfterJupiter(BazelJUnitOutputListener listener) {
    return new TestExecutionListener() {
      @Override
      public void testPlanExecutionStarted(TestPlan testPlan) {
        listener.testPlanExecutionStarted(testPlan);
      }

      @Override
      public void dynamicTestRegistered(TestIdentifier testIdentifier) {
        listener.dynamicTestRegistered(testIdentifier);
      }

      @Override
      public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        listener.executionSkipped(testIdentifier, reason);
      }

      @Override
      public void executionStarted(TestIdentifier testIdentifier) {
        listener.executionStarted(testIdentifier);
      }

      @Override
      public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        listener.executionFinished(testIdentifier, testExecutionResult);
        if (testIdentifier.getParentId().isEmpty() && testIdentifier.getUniqueIdObject().getEngineId().orElse("").equals("junit-jupiter")) {
          listener.closeForInterrupt();
        }
      }
    };
  }

  private static Element getOnlyElement(Document xml, String tagName) {
    NodeList elements = xml.getElementsByTagName(tagName);
    assertThat(elements.getLength()).isEqualTo(1);
    return (Element)elements.item(0);
  }

  private static Element getOnlyTestCase(Document xml, String name) {
    NodeList testCases = xml.getElementsByTagName("testcase");
    List<Element> matches = new ArrayList<>();
    for (int i = 0; i < testCases.getLength(); i++) {
      Element testCase = (Element)testCases.item(i);
      if (name.equals(testCase.getAttribute("name"))) {
        matches.add(testCase);
      }
    }
    assertThat(matches).hasSize(1);
    return matches.get(0);
  }

  private static List<String> getTestCaseNames(Document xml) {
    NodeList testCases = xml.getElementsByTagName("testcase");
    List<String> names = new ArrayList<>();
    for (int i = 0; i < testCases.getLength(); i++) {
      names.add(((Element)testCases.item(i)).getAttribute("name"));
    }
    return names;
  }

  private static int countTestCasesWithTag(Document xml, String tagName) {
    NodeList testCases = xml.getElementsByTagName("testcase");
    int count = 0;
    for (int i = 0; i < testCases.getLength(); i++) {
      if (((Element)testCases.item(i)).getElementsByTagName(tagName).getLength() > 0) {
        count++;
      }
    }
    return count;
  }

  @Disabled(SCENARIO_DISABLED_REASON)
  static class BeforeAllFailureScenario {
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class FailingBeforeAllLifecycle {
      @BeforeAll
      void beforeAll() {
        throw new IllegalStateException("beforeAll failure");
      }

      @Test
      void firstTest() {
      }

      @Test
      void secondTest() {
      }
    }
  }

  @Disabled(SCENARIO_DISABLED_REASON)
  static class AfterAllFailureScenario {
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class FailingAfterAllLifecycle {
      @Test
      void successfulTest() {
      }

      @AfterAll
      void afterAll() {
        throw new IllegalStateException("afterAll failure");
      }
    }
  }

  @Disabled(SCENARIO_DISABLED_REASON)
  static class BeforeAllFailureWithNestedScenario {
    @BeforeAll
    static void beforeAll() {
      throw new IllegalStateException("beforeAll failure");
    }

    @Test
    void test() {
    }

    @Nested
    class Inner {
      @Test
      void inner() {
      }
    }
  }

  @Disabled(SCENARIO_DISABLED_REASON)
  static class ParameterizedScenario {
    @ParameterizedTest(name = "{0}")
    @EnumSource(ParameterizedValue.class)
    void firstParameterizedTest(ParameterizedValue value) {
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ParameterizedValue.class)
    void secondParameterizedTest(ParameterizedValue value) {
    }
  }

  @Disabled(SCENARIO_DISABLED_REASON)
  static class AbortedScenario {
    @Test
    void abortedTest() {
      Assumptions.assumeTrue(false, "skipping due to failed assumption");
    }

    @Test
    void successfulTest() {
    }
  }

  @Disabled(SCENARIO_DISABLED_REASON)
  static class MultipleComparisonFailuresScenario {
    @Test
    void assertAllWithTwoComparisonFailures() {
      assertAll(
        () -> assertEquals("expected-1", "actual-1"),
        () -> assertEquals("expected-2", "actual-2")
      );
    }
  }

  private enum ParameterizedValue {
    FIRST,
    SECOND
  }
}
