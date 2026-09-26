// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

class JUnit5TeamCityRunnerForTestAllSuiteTest {
  //Disable test-purpose classes to prevent them from being executed as actual tests.
  private static final String SCENARIO_DISABLED_REASON = "Executed explicitly by JUnit5TeamCityRunnerForTestAllSuiteTest";

  @Test
  void testLimitedStacktraceSize() {
    final int limit = 3000; // full length is about 8k locally
    final IOException inner = new IOException("Cause inner");
    final IOException outer = new IOException("Cause outer", inner);
    final Exception head = new Exception("HEAD", outer);
    final String stacktrace = JUnit5TeamCityRunner.TCExecutionListener.getTrace(head, limit);

    assertThat(stacktrace)
      .contains("java.lang.Exception: HEAD")
      .doesNotContain("Caused by: java.io.IOException: Cause outer")
      .contains("Caused by: java.io.IOException: Cause inner")
      .hasSizeLessThan(limit + limit / 10)
    ;
  }

  @Test
  void testLimitedStacktraceSizeWithLongMessage() {
    final int limit = 1000; // full length is about 8k locally
    final IOException inner = new IOException("Cause inner");
    final IOException outer = new IOException("Cause outer", inner);

    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < limit; i++) {
      sb.append(i);
    }
    final Exception head = new Exception("HEAD: " + sb, outer); // exception with a very-very long message
    assertThat(head.getMessage()).hasSizeGreaterThan(limit);

    final String stacktrace = JUnit5TeamCityRunner.TCExecutionListener.getTrace(head, limit);

    assertThat(stacktrace)
      .contains("java.lang.Exception: HEAD")
      .doesNotContain("Caused by: java.io.IOException: Cause outer")
      .contains("Caused by: java.io.IOException: Cause inner")
      .hasSizeLessThan(limit + limit / 10)
    ;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Names reported to TeamCity. TeamCity has no tree: an occurrence is identified by (open suites, name), so nodes
  // that differ only in which @ParameterizedClass invocation they belong to must differ in the reported name.
  // The invocations of a class template only exist while it *executes* (they arrive via dynamicTestRegistered), so
  // these scenarios are executed rather than merely discovered - which also pins the real Jupiter tree, so a Jupiter
  // upgrade that renames a segment type fails here instead of silently re-gluing occurrences on TeamCity.
  // ---------------------------------------------------------------------------------------------------------------

  @Test
  void namesEveryClassTemplateInvocationDistinctly() {
    String scenario = ParameterizedClassScenario.class.getName();

    assertThat(execute(ParameterizedClassScenario.class).testNames).containsExactlyInAnyOrder(
      scenario + ".first()[alpha]",
      scenario + ".first()[beta]",
      scenario + ".second()[alpha]",
      scenario + ".second()[beta]"
    );
  }

  @Test
  void qualifiesNestedClassesInsideAClassTemplate() {
    String inner = ParameterizedClassWithNestedScenario.Inner.class.getName();
    Names names = execute(ParameterizedClassWithNestedScenario.class);

    assertThat(names.testNames).containsExactlyInAnyOrder(
      inner + ".inner()[alpha]",
      inner + ".inner()[beta]"
    );
    // the @Nested container itself is qualified too, otherwise the two suites would collide
    assertThat(names.allNames).contains(inner + "[alpha].Inner", inner + "[beta].Inner");
  }

  @Test
  void qualifiesAClassTemplateDeclaredOnANestedClass() {
    String owner = NestedParameterizedClassOwnerScenario.class.getName();
    String inner = NestedParameterizedClassOwnerScenario.Inner.class.getName();

    assertThat(execute(NestedParameterizedClassOwnerScenario.class).testNames).containsExactlyInAnyOrder(
      owner + ".outer()",  // no qualifier leaks onto a sibling that is not parameterized
      inner + ".inner()[alpha]",
      inner + ".inner()[beta]"
    );
  }

  @Test
  void ordersTheClassInvocationBeforeTheMethodParameters() {
    String scenario = ParameterizedClassWithParameterizedTestScenario.class.getName();

    assertThat(execute(ParameterizedClassWithParameterizedTestScenario.class).testNames).containsExactlyInAnyOrder(
      scenario + ".t[alpha][1]",  // root to leaf: the class invocation first, then the method parameters
      scenario + ".t[alpha][2]"
    );
  }

  @Test
  void ordersNestedClassTemplatesFromRootToLeaf() {
    String inner = NestedClassTemplatesScenario.Inner.class.getName();

    assertThat(execute(NestedClassTemplatesScenario.class).testNames).containsExactlyInAnyOrder(
      inner + ".t()[alpha][one]",
      inner + ".t()[alpha][two]"
    );
  }

  /** The fix must be inert outside class templates: these names are exactly what was reported before it. */
  @Test
  void leavesNamesWithoutAClassTemplateInvocationUntouched() {
    assertThat(execute(PlainScenario.class).testNames)
      .containsExactly(PlainScenario.class.getName() + ".t()");
    assertThat(execute(ParameterizedTestScenario.class).testNames).containsExactlyInAnyOrder(
      ParameterizedTestScenario.class.getName() + ".t[a]",
      ParameterizedTestScenario.class.getName() + ".t[b]"
    );
  }

  /** The invocation display name is Jupiter's, not ours - here with Jupiter's default pattern rather than {@code {0}}. */
  @Test
  void takesTheInvocationNameFromJupiter() {
    String scenario = DefaultPatternScenario.class.getName();
    List<String> names = execute(DefaultPatternScenario.class).testNames;

    assertThat(names).hasSize(2).doesNotHaveDuplicates();
    assertThat(names).allSatisfy(name -> assertThat(name).startsWith(scenario + ".t()["));
    assertThat(names.toString()).contains("alpha", "beta");
  }

  /** The actual contract, over every scenario at once. */
  @Test
  void neverReportsTheSameNameTwice() {
    List<Class<?>> scenarios = List.of(
      ParameterizedClassScenario.class,
      ParameterizedClassWithNestedScenario.class,
      NestedParameterizedClassOwnerScenario.class,
      ParameterizedClassWithParameterizedTestScenario.class,
      NestedClassTemplatesScenario.class,
      DefaultPatternScenario.class,
      PlainScenario.class,
      ParameterizedTestScenario.class
    );

    for (Class<?> scenario : scenarios) {
      assertThat(execute(scenario).testNames).describedAs(scenario.getSimpleName()).doesNotHaveDuplicates();
    }
  }

  /**
   * Without a test plan the invocation index takes over: still unique, if not pretty. This is the path taken when
   * something is reported before {@code testPlanExecutionStarted}.
   */
  @Test
  void fallsBackToTheInvocationIndexWithoutATestPlan() {
    String scenario = ParameterizedClassScenario.class.getName();
    Names names = execute(ParameterizedClassScenario.class);

    assertThat(names.namesWith(null)).containsExactlyInAnyOrder(
      scenario + ".first()[#1]",
      scenario + ".first()[#2]",
      scenario + ".second()[#1]",
      scenario + ".second()[#2]"
    );
  }

  /** A node the given plan never saw must degrade to the index, not throw out of the runner. */
  @Test
  void fallsBackToTheInvocationIndexForANodeMissingFromThePlan() {
    String inner = ParameterizedClassWithNestedScenario.Inner.class.getName();
    TestPlan foreignPlan = execute(ParameterizedClassScenario.class).plan;

    assertThat(execute(ParameterizedClassWithNestedScenario.class).namesWith(foreignPlan)).containsExactlyInAnyOrder(
      inner + ".inner()[#1]",
      inner + ".inner()[#2]"
    );
  }

  private static Names execute(Class<?> scenarioRootClass) {
    // Keep this isolated from globally auto-registered launcher session listeners: they enable IDE-wide test
    // extensions and first/last-in-suite leak checks that have nothing to do with the naming under test.
    var launcher = LauncherFactory.create(LauncherConfig.builder()
                                            .enableLauncherSessionListenerAutoRegistration(false)
                                            .build());
    var request = LauncherDiscoveryRequestBuilder.request()
      .selectors(selectClass(scenarioRootClass))
      .configurationParameter("junit.jupiter.conditions.deactivate", "org.junit.*DisabledCondition")
      .build();

    Names names = new Names();
    launcher.execute(request, names);
    return names;
  }

  private static final class Names implements TestExecutionListener {
    private TestPlan plan;
    private final List<TestIdentifier> tests = new ArrayList<>();
    private final List<String> testNames = new ArrayList<>();
    private final List<String> allNames = new ArrayList<>();

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
      plan = testPlan;
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
      String name = JUnit5TeamCityRunner.TCExecutionListener.getName(testIdentifier, plan);
      allNames.add(name);
      if (testIdentifier.isTest()) {
        tests.add(testIdentifier);
        testNames.add(name);
      }
    }

    /** The names of the very same tests, but resolved against another (or no) test plan. */
    private List<String> namesWith(TestPlan otherPlan) {
      List<String> result = new ArrayList<>();
      for (TestIdentifier test : tests) {
        result.add(JUnit5TeamCityRunner.TCExecutionListener.getName(test, otherPlan));
      }
      return result;
    }
  }

  @ParameterizedClass(name = "{0}")
  @ValueSource(strings = {"alpha", "beta"})
  @Disabled(SCENARIO_DISABLED_REASON)
  static class ParameterizedClassScenario {
    @Parameter
    String value;

    @Test
    void first() { }

    @Test
    void second() { }
  }

  @ParameterizedClass(name = "{0}")
  @ValueSource(strings = {"alpha", "beta"})
  @Disabled(SCENARIO_DISABLED_REASON)
  static class ParameterizedClassWithNestedScenario {
    @Parameter
    String value;

    @Nested
    class Inner {
      @Test
      void inner() { }
    }
  }

  @Disabled(SCENARIO_DISABLED_REASON)
  static class NestedParameterizedClassOwnerScenario {
    @Test
    void outer() { }

    @Nested
    @ParameterizedClass(name = "{0}")
    @ValueSource(strings = {"alpha", "beta"})
    class Inner {
      @Parameter
      String value;

      @Test
      void inner() { }
    }
  }

  @ParameterizedClass(name = "{0}")
  @ValueSource(strings = {"alpha"})
  @Disabled(SCENARIO_DISABLED_REASON)
  static class ParameterizedClassWithParameterizedTestScenario {
    @Parameter
    String value;

    @ParameterizedTest(name = "{0}")
    @ValueSource(ints = {1, 2})
    void t(int x) { }
  }

  @ParameterizedClass(name = "{0}")
  @ValueSource(strings = {"alpha"})
  @Disabled(SCENARIO_DISABLED_REASON)
  static class NestedClassTemplatesScenario {
    @Parameter
    String value;

    @Nested
    @ParameterizedClass(name = "{0}")
    @ValueSource(strings = {"one", "two"})
    class Inner {
      @Parameter
      String innerValue;

      @Test
      void t() { }
    }
  }

  @ParameterizedClass
  @ValueSource(strings = {"alpha", "beta"})
  @Disabled(SCENARIO_DISABLED_REASON)
  static class DefaultPatternScenario {
    @Parameter
    String value;

    @Test
    void t() { }
  }

  @Disabled(SCENARIO_DISABLED_REASON)
  static class PlainScenario {
    @Test
    void t() { }
  }

  @Disabled(SCENARIO_DISABLED_REASON)
  static class ParameterizedTestScenario {
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"a", "b"})
    void t(String s) { }
  }
}
