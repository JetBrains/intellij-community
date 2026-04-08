// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests.bazel;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
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
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

class BazelJUnitOutputListenerTest {
  @Test
  void reportsClassConfigurationFailureWhenBeforeAllFails(@TempDir Path tempDir) throws Exception {
    Document xml = executeAndReadXml(BeforeAllFailureScenario.class, tempDir.resolve("beforeAllFailure.xml"));

    Element suite = getOnlyElement(xml, "testsuite");
    assertThat(suite.getAttribute("tests")).isEqualTo("3");
    assertThat(suite.getAttribute("failures")).isEqualTo("1");
    assertThat(suite.getAttribute("skipped")).isEqualTo("2");
    assertThat(getTestCaseNames(xml)).contains("firstTest", "secondTest");
    assertThat(countTestCasesWithTag(xml, "failure")).isEqualTo(1);
    assertThat(getOnlyTestCase(xml, "firstTest").getElementsByTagName("skipped").getLength()).isEqualTo(1);
    assertThat(getOnlyTestCase(xml, "secondTest").getElementsByTagName("skipped").getLength()).isEqualTo(1);
  }

  @Test
  void preservesExecutedTestsWhenAfterAllFails(@TempDir Path tempDir) throws Exception {
    Document xml = executeAndReadXml(AfterAllFailureScenario.class, tempDir.resolve("afterAllFailure.xml"));

    Element suite = getOnlyElement(xml, "testsuite");
    assertThat(suite.getAttribute("tests")).isEqualTo("2");
    assertThat(suite.getAttribute("failures")).isEqualTo("1");
    assertThat(suite.getAttribute("skipped")).isEqualTo("0");
    assertThat(getTestCaseNames(xml)).contains("successfulTest");
    assertThat(countTestCasesWithTag(xml, "failure")).isEqualTo(1);
    assertThat(getOnlyTestCase(xml, "successfulTest").getElementsByTagName("failure").getLength()).isEqualTo(0);
    assertThat(getOnlyTestCase(xml, "successfulTest").getElementsByTagName("skipped").getLength()).isEqualTo(0);
  }

  private static Document executeAndReadXml(Class<?> scenarioRootClass, Path xmlOutputFile) throws Exception {
    // Keep this helper isolated from globally auto-registered launcher session listeners:
    // they enable IDE-wide test extensions and first/last-in-suite leak checks that are not
    // part of the XML listener contract under test here.
    var launcher = LauncherFactory.create(LauncherConfig.builder()
                                         .enableLauncherSessionListenerAutoRegistration(false)
                                         .build());
    var request = LauncherDiscoveryRequestBuilder.request()
      .selectors(selectClass(scenarioRootClass))
      .build();

    try (var listener = new BazelJUnitOutputListener(xmlOutputFile)) {
      launcher.registerTestExecutionListeners(listener);
      launcher.execute(request);
    }

    var documentBuilderFactory = DocumentBuilderFactory.newInstance();
    documentBuilderFactory.setNamespaceAware(false);
    return documentBuilderFactory.newDocumentBuilder().parse(xmlOutputFile.toFile());
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
}
