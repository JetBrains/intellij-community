// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests;

import com.intellij.tests.bazel.BazelJUnitOutputListener;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.ClassTemplate;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.BeforeClassTemplateInvocationCallback;
import org.junit.jupiter.api.extension.ClassTemplateInvocationContext;
import org.junit.jupiter.api.extension.ClassTemplateInvocationContextProvider;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * The Bazel JUnit XML gives each test the name that the JPS run reports to TeamCity, so a test keeps its history when it
 * moves to Bazel. Remove this test together with {@link JUnit5TeamCityRunner}.
 */
class BazelJpsTestNamesTest {
  private static final String SCENARIO_DISABLED_REASON = "Executed explicitly by BazelJpsTestNamesTest";
  private static final Pattern SERVICE_MESSAGE = Pattern.compile("^##teamcity\\[(\\w+) name='((?:\\|.|[^|'])*)'");

  /** The JUnit 3 and JUnit 4 scenarios run only in this test. `@Disabled` does not stop them. */
  private static volatile boolean ourRunning;

  @ParameterizedTest
  @ValueSource(classes = {
    PlainScenario.class,
    NestedScenario.class,
    ParameterizedTestScenario.class,
    TestFactoryScenario.class,
    FailingTestFactoryScenario.class,
    SimpleDisplayNamesScenario.class,
    BeforeAllFailureScenario.class,
    BeforeAllFailureWithChildrenScenario.class,
    AbortedClassScenario.class,
    SkippedClassScenario.class,
    SkippedMethodScenario.class,
    FailingDynamicTestScenario.class,
    AfterAllFailureScenario.class,
    NestedBeforeAllFailureScenario.class,
    ClassTemplateScenario.class,
    NestedClassTemplateScenario.class,
    FailedClassTemplateInvocationScenario.class,
    FailedNestedClassTemplateInvocationScenario.class,
    ClassTemplateNestedClassFailureScenario.class,
    ParameterizedClassScenario.class,
    DefaultPatternParameterizedClassScenario.class,
    NestedParameterizedClassOwnerScenario.class,
    NestedParameterizedClassesScenario.class,
    JUnit4Scenario.class,
    JUnit4ParameterizedScenario.class,
    JUnit4BeforeClassFailureScenario.class,
    JUnit4IgnoredClassScenario.class,
    JUnit4SuiteScenario.class,
    JUnit3Scenario.class,
    JUnit3SuiteScenario.class,
  })
  void givesEachTestTheJpsName(Class<?> scenario, @TempDir Path tempDir) throws Exception {
    Path xmlFile = tempDir.resolve("test.xml");
    ByteArrayOutputStream jpsOutput = new ByteArrayOutputStream();
    var jpsListener = new JUnit5TeamCityRunner.TCExecutionListener(new PrintStream(jpsOutput, true, StandardCharsets.UTF_8),
                                                                   (testIdentifier, status, testName) -> { });
    try (var bazelListener = new BazelJUnitOutputListener(xmlFile)) {
      execute(scenario, jpsListener, bazelListener);
    }

    List<String> jpsNames = getJpsNames(jpsOutput.toString(StandardCharsets.UTF_8));
    assertThat(jpsNames).isNotEmpty();
    assertThat(getBazelNames(xmlFile)).containsExactlyInAnyOrderElementsOf(jpsNames);
  }

  private static void execute(Class<?> scenario, TestExecutionListener... listeners) {
    // Keep this isolated from globally auto-registered launcher session listeners: they enable IDE-wide test
    // extensions and first/last-in-suite leak checks that have nothing to do with the names under test.
    var launcher = LauncherFactory.create(LauncherConfig.builder().enableLauncherSessionListenerAutoRegistration(false).build());
    var request = LauncherDiscoveryRequestBuilder.request()
      .selectors(selectClass(scenario))
      .configurationParameter("junit.jupiter.conditions.deactivate", "org.junit.*DisabledCondition")
      .build();
    ourRunning = true;
    try {
      launcher.execute(request, listeners);
    }
    finally {
      ourRunning = false;
    }
  }

  /** The names as TeamCity composes them from the service messages, see {@code TCExecutionListener.fullTestName}. */
  private static List<String> getJpsNames(String output) {
    List<String> names = new ArrayList<>();
    Deque<String> openSuites = new ArrayDeque<>();
    for (String line : output.split("\n")) {
      Matcher matcher = SERVICE_MESSAGE.matcher(line.strip());
      if (!matcher.find()) continue;
      String name = unescape(matcher.group(2));
      switch (matcher.group(1)) {
        case "testSuiteStarted" -> openSuites.addLast(name);
        case "testSuiteFinished" -> openSuites.pollLast();
        case "testStarted" -> names.add(withSuites(openSuites, name));
        default -> { }
      }
    }
    return names;
  }

  /**
   * The names as TeamCity composes them from the Bazel JUnit XML: as {@code AntJUnitXmlReportParser} of the TeamCity XML
   * report plugin, then with the suite, as for the service messages.
   */
  private static List<String> getBazelNames(Path xmlFile) throws Exception {
    var factory = DocumentBuilderFactory.newInstance();
    var xml = factory.newDocumentBuilder().parse(xmlFile.toFile());
    List<String> names = new ArrayList<>();
    NodeList suites = xml.getElementsByTagName("testsuite");
    for (int i = 0; i < suites.getLength(); i++) {
      Element suite = (Element)suites.item(i);
      NodeList testCases = suite.getElementsByTagName("testcase");
      for (int j = 0; j < testCases.getLength(); j++) {
        Element testCase = (Element)testCases.item(j);
        String className = testCase.getAttribute("classname");
        String name = testCase.getAttribute("name");
        String testName = name.startsWith(className) ? name : className + "." + name;
        names.add(withSuites(List.of(suite.getAttribute("name")), testName));
      }
    }
    return names;
  }

  /** TeamCity leaves out a suite that the test name already starts with. */
  private static String withSuites(Collection<String> suites, String name) {
    StringBuilder result = new StringBuilder();
    for (String suite : suites) {
      if (!name.equals(suite) && !name.startsWith(suite + ".")) {
        result.append(suite).append(": ");
      }
    }
    return result.append(name).toString();
  }

  private static String unescape(String value) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c != '|' || i + 1 == value.length()) {
        result.append(c);
        continue;
      }
      char next = value.charAt(++i);
      switch (next) {
        case 'n' -> result.append('\n');
        case 'r' -> result.append('\r');
        case 'x' -> result.append('\u0085');
        case 'l' -> result.append(' ');
        case 'p' -> result.append(' ');
        case '0' -> {
          // |0xNNNN
          result.append((char)Integer.parseInt(value.substring(i + 2, i + 6), 16));
          i += 5;
        }
        default -> result.append(next);
      }
    }
    return result.toString();
  }

  // ---------------------------------------------------------------------------------------------------------------
  // JUnit 5 scenarios
  // ---------------------------------------------------------------------------------------------------------------

  @Disabled(SCENARIO_DISABLED_REASON)
  static class PlainScenario {
    @Test
    void test() {
    }

    @Test
    void test(TestInfo testInfo) {
    }

    @Test
    @DisplayName("custom name")
    void named() {
    }

    @Test
    void failed() {
      throw new AssertionError("test failure");
    }

    @Test
    void aborted() {
      Assumptions.assumeTrue(false, "assumption");
    }

    @RepeatedTest(2)
    void repeated() {
    }

    @Test
    @DisplayName("a: b.c")
    void punctuation() {
    }
  }

  @Disabled(SCENARIO_DISABLED_REASON)
  static class NestedScenario {
    @Test
    void outer() {
    }

    @Nested
    class Middle {
      @Test
      void middle() {
      }

      @Nested
      class Inner {
        @Test
        void inner() {
        }
      }
    }
  }

  @Disabled(SCENARIO_DISABLED_REASON)
  static class ParameterizedTestScenario {
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"a", "b.c"})
    void named(String value) {
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void defaultPattern(int value) {
    }
  }

  @Disabled(SCENARIO_DISABLED_REASON)
  static class TestFactoryScenario {
    @TestFactory
    Stream<DynamicNode> factory() {
      return Stream.of(DynamicTest.dynamicTest("first", () -> { }),
                       DynamicContainer.dynamicContainer("container", Stream.of(DynamicTest.dynamicTest("second", () -> { }))));
    }
  }

  @Disabled(SCENARIO_DISABLED_REASON)
  static class FailingTestFactoryScenario {
    @TestFactory
    Stream<DynamicTest> factory() {
      throw new IllegalStateException("factory failure");
    }
  }

  /** As `@RunInMonolithAndSplitMode`: the display name of a method has no `()`. */
  @DisplayNameGeneration(DisplayNameGenerator.Simple.class)
  @Disabled(SCENARIO_DISABLED_REASON)
  static class SimpleDisplayNamesScenario {
    @Test
    void test() {
    }

    @Test
    void withInfo(TestInfo testInfo) {
    }
  }

  @Disabled(SCENARIO_DISABLED_REASON)
  static class BeforeAllFailureScenario {
    @BeforeAll
    static void setUp() {
      throw new IllegalStateException("class setup failure");
    }

    @Test
    void first() {
    }

    @Test
    void second() {
    }
  }

  @Disabled(SCENARIO_DISABLED_REASON)
  static class BeforeAllFailureWithChildrenScenario {
    @BeforeAll
    static void setUp() {
      throw new IllegalStateException("class setup failure");
    }

    @Test
    void test() {
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = "a")
    void parameterized(String value) {
    }

    @Nested
    class Inner {
      @Test
      void inner() {
      }
    }
  }

  @Disabled(SCENARIO_DISABLED_REASON)
  static class AbortedClassScenario {
    @BeforeAll
    static void setUp() {
      Assumptions.assumeTrue(false, "class assumption");
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

  @ExtendWith(AlwaysDisabled.class)
  @Disabled(SCENARIO_DISABLED_REASON)
  static class SkippedClassScenario {
    @Test
    void test() {
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = "a")
    void parameterized(String value) {
    }

    @Nested
    class Inner {
      @Test
      void inner() {
      }
    }
  }

  @Disabled(SCENARIO_DISABLED_REASON)
  static class SkippedMethodScenario {
    @Test
    void test() {
    }

    @Test
    @ExtendWith(AlwaysDisabled.class)
    void skipped() {
    }
  }

  @Disabled(SCENARIO_DISABLED_REASON)
  static class FailingDynamicTestScenario {
    @TestFactory
    Stream<DynamicTest> factory() {
      return Stream.of(DynamicTest.dynamicTest("passed", () -> { }),
                       DynamicTest.dynamicTest("failed", () -> { throw new AssertionError("dynamic test failure"); }));
    }
  }

  static final class AlwaysDisabled implements ExecutionCondition {
    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
      return ConditionEvaluationResult.disabled("disabled by the scenario");
    }
  }

  @Disabled(SCENARIO_DISABLED_REASON)
  static class AfterAllFailureScenario {
    @AfterAll
    static void tearDown() {
      throw new IllegalStateException("class teardown failure");
    }

    @Test
    void test() {
    }
  }

  @Disabled(SCENARIO_DISABLED_REASON)
  static class NestedBeforeAllFailureScenario {
    @Test
    void outer() {
    }

    @Nested
    class Inner {
      @BeforeAll
      static void setUp() {
        throw new IllegalStateException("class setup failure");
      }

      @Test
      void inner() {
      }
    }
  }

  @ClassTemplate
  @ExtendWith(RunModeInvocations.class)
  @Disabled(SCENARIO_DISABLED_REASON)
  static class ClassTemplateScenario {
    @Test
    void test() {
    }

    @Test
    @DisplayName("custom name")
    void named() {
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = "a")
    void parameterized(String value) {
    }

    @Nested
    class Inner {
      @Test
      void inner() {
      }
    }
  }

  @ClassTemplate
  @ExtendWith(RunModeInvocations.class)
  @Disabled(SCENARIO_DISABLED_REASON)
  static class NestedClassTemplateScenario {
    @Nested
    @ClassTemplate
    @ExtendWith(RunModeInvocations.class)
    class Inner {
      @Test
      void test() {
      }
    }
  }

  @ClassTemplate
  @ExtendWith({RunModeInvocations.class, FailingInvocationSetup.class})
  @Disabled(SCENARIO_DISABLED_REASON)
  static class FailedClassTemplateInvocationScenario {
    @Test
    void test() {
    }
  }

  @ClassTemplate
  @ExtendWith(RunModeInvocations.class)
  @Disabled(SCENARIO_DISABLED_REASON)
  static class FailedNestedClassTemplateInvocationScenario {
    @Nested
    @ClassTemplate
    @ExtendWith({RunModeInvocations.class, FailingInvocationSetup.class})
    class Inner {
      @Test
      void test() {
      }
    }
  }

  @ClassTemplate
  @ExtendWith(RunModeInvocations.class)
  @Disabled(SCENARIO_DISABLED_REASON)
  static class ClassTemplateNestedClassFailureScenario {
    @Nested
    class Inner {
      @AfterAll
      static void tearDown() {
        throw new IllegalStateException("class teardown failure");
      }

      @Test
      void inner() {
      }
    }
  }

  @ParameterizedClass(name = "{0}")
  @ValueSource(strings = {"alpha", "a.b[c]"})
  @Disabled(SCENARIO_DISABLED_REASON)
  static class ParameterizedClassScenario {
    @Parameter
    String value;

    @Test
    void test() {
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(ints = {1, 2})
    void parameterized(int x) {
    }
  }

  @ParameterizedClass
  @ValueSource(strings = {"alpha", "beta"})
  @Disabled(SCENARIO_DISABLED_REASON)
  static class DefaultPatternParameterizedClassScenario {
    @Parameter
    String value;

    @Test
    void test() {
    }
  }

  @Disabled(SCENARIO_DISABLED_REASON)
  static class NestedParameterizedClassOwnerScenario {
    @Test
    void outer() {
    }

    @Nested
    @ParameterizedClass(name = "{0}")
    @ValueSource(strings = {"alpha", "beta"})
    class Inner {
      @Parameter
      String value;

      @Test
      void inner() {
      }
    }
  }

  @ParameterizedClass(name = "{0}")
  @ValueSource(strings = "alpha")
  @Disabled(SCENARIO_DISABLED_REASON)
  static class NestedParameterizedClassesScenario {
    @Parameter
    String value;

    @Nested
    @ParameterizedClass(name = "{0}")
    @ValueSource(strings = {"one", "two"})
    class Inner {
      @Parameter
      String innerValue;

      @ParameterizedTest(name = "{0}")
      @ValueSource(ints = {1, 2})
      void test(int x) {
      }
    }
  }

  /** Two invocations named as the invocations of `MonolithAndSplitModeClassTemplateProvider`. */
  static final class RunModeInvocations implements ClassTemplateInvocationContextProvider {
    @Override
    public boolean supportsClassTemplate(ExtensionContext context) {
      return true;
    }

    @Override
    public Stream<ClassTemplateInvocationContext> provideClassTemplateInvocationContexts(ExtensionContext context) {
      return Stream.of("MONOLITH", "SPLIT").map(mode -> new ClassTemplateInvocationContext() {
        @Override
        public String getDisplayName(int invocationIndex) {
          return mode;
        }
      });
    }
  }

  static final class FailingInvocationSetup implements BeforeClassTemplateInvocationCallback {
    @Override
    public void beforeClassTemplateInvocation(ExtensionContext context) {
      throw new IllegalStateException("invocation setup failure");
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // JUnit 4 and JUnit 3 scenarios. The class path scan of the Bazel runner finds them too, so they do nothing there.
  // ---------------------------------------------------------------------------------------------------------------

  public abstract static class JUnit4Base {
    @BeforeClass
    public static void runOnlyInBazelJpsTestNamesTest() {
      Assume.assumeTrue(ourRunning);
    }
  }

  public static class JUnit4Scenario extends JUnit4Base {
    @org.junit.Test
    public void test() {
    }

    @org.junit.Test
    public void failed() {
      throw new AssertionError("test failure");
    }

    @Ignore
    @org.junit.Test
    public void ignored() {
    }
  }

  @RunWith(Parameterized.class)
  public static class JUnit4ParameterizedScenario extends JUnit4Base {
    @Parameterized.Parameters(name = "{0}")
    public static List<String> parameters() {
      return List.of("a", "b");
    }

    @Parameterized.Parameter
    public String value;

    @org.junit.Test
    public void test() {
    }
  }

  public static class JUnit4BeforeClassFailureScenario extends JUnit4Base {
    @BeforeClass
    public static void setUp() {
      if (ourRunning) throw new IllegalStateException("class setup failure");
    }

    @org.junit.Test
    public void test() {
    }
  }

  @Ignore
  public static class JUnit4IgnoredClassScenario extends JUnit4Base {
    @org.junit.Test
    public void test() {
    }
  }

  @RunWith(Suite.class)
  @Suite.SuiteClasses({JUnit4Scenario.class, JUnit4ParameterizedScenario.class})
  public static class JUnit4SuiteScenario {
  }

  public abstract static class JUnit3Base extends TestCase {
    @Override
    public void runBare() throws Throwable {
      if (ourRunning) super.runBare();
    }
  }

  public static class JUnit3Scenario extends JUnit3Base {
    public void testFirst() {
    }

    public void testFailed() {
      fail("test failure");
    }
  }

  public static class JUnit3SuiteScenario {
    public static junit.framework.Test suite() {
      TestSuite suite = new TestSuite("JUnit 3 suite");
      if (ourRunning) suite.addTestSuite(JUnit3Scenario.class);
      return suite;
    }
  }
}
