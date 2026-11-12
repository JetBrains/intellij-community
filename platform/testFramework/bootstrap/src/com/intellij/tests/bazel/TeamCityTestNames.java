// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests.bazel;

import com.intellij.tests.ClassTemplateInvocations;
import junit.framework.TestSuite;
import org.junit.platform.commons.PreconditionViolationException;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The names of the tests on TeamCity. TeamCity keeps the history of a test by its name.
 *
 * <p>TeamCity reads the Bazel JUnit XML. It names a test case `<classname>.<name>`, or `<name>` when the name starts
 * with the classname. Then it puts the suite before the test name, as `<testsuite name>: <test name>`. It leaves out
 * the suite when the test name starts with the suite and a dot. TeamCity also removes an empty `()` at the end, so
 * `C.test()` is `C.test` on TeamCity.
 */
final class TeamCityTestNames {
  /** The test name of a failed container, the same as {@code com.intellij.rt.execution.TestListenerProtocol.CLASS_CONFIGURATION}. */
  static final String CLASS_CONFIGURATION = "Class Configuration";

  private TeamCityTestNames() {
  }

  /** The test name: the name of the node, or {@link #CLASS_CONFIGURATION} for a failed container. */
  static String getTestName(TestData test, TestPlan testPlan) {
    return isContainerFailure(test) ? CLASS_CONFIGURATION : getName(test.getId(), testPlan);
  }

  /** The `classname` of the test case: the class, or `""` when the test name does not start with the class. */
  static String getXmlClassName(TestData test, TestPlan testPlan) {
    String className = TestCaseXmlRenderer.getClassName(testPlan, test.getId());
    String testName = getTestName(test, testPlan);
    if (!testName.startsWith(className + ".")) return "";
    // TeamCity uses the name alone when it starts with the classname.
    return testName.startsWith(className, className.length() + 1) ? "" : className;
  }

  /** The `name` of the test case: the test name after the `classname` and a dot. */
  static String getXmlName(TestData test, TestPlan testPlan) {
    String className = getXmlClassName(test, testPlan);
    String testName = getTestName(test, testPlan);
    return className.isEmpty() ? testName : testName.substring(className.length() + 1);
  }

  /**
   * The name of the suite of the test case: the suites that hold the test, which TeamCity puts before the test name.
   * When no suite must go there, the suite is the class or the test name: TeamCity leaves it out.
   */
  static String getSuiteName(TestData test, TestPlan testPlan) {
    String testName = getTestName(test, testPlan);
    List<String> suites = new ArrayList<>();
    for (String suite : getSuites(test, testPlan)) {
      if (!testName.equals(suite) && !testName.startsWith(suite + ".")) {
        suites.add(suite);
      }
    }
    if (!suites.isEmpty()) return String.join(": ", suites);
    String className = getXmlClassName(test, testPlan);
    return className.isEmpty() ? testName : className;
  }

  /**
   * The name of a node.
   *
   * <p>A class gets its name and the display name. A method gets the display name, or the method name and the display
   * name in brackets. The class template invocations go after the name, before the display name in brackets.
   */
  static String getName(TestIdentifier id, TestPlan testPlan) {
    String displayName = id.getDisplayName();
    String invocations = ClassTemplateInvocations.qualifier(id, testPlan);
    TestSource source = id.getSource().orElse(null);
    if (source instanceof ClassSource classSource) {
      String className = classSource.getClassName();
      if (className.equals(TestSuite.class.getName()) || className.equals(displayName)) {
        return displayName + invocations;
      }
      String withDisplayName = "." + displayName;
      return className.endsWith(withDisplayName) ? className + invocations : className + invocations + withDisplayName;
    }
    if (source instanceof MethodSource methodSource) {
      String className = methodSource.getClassName();
      String methodName = methodSource.getMethodName();
      return displayName.startsWith(methodName) ? className + "." + displayName + invocations
                                                : className + "." + methodName + invocations + "[" + displayName + "]";
    }
    return displayName + invocations;
  }

  /** The suites that hold the node, root to leaf. A failed container is a suite of its own. */
  private static List<String> getSuites(TestData test, TestPlan testPlan) {
    TestIdentifier id = test.getId();
    List<String> suites = new ArrayList<>();
    for (TestIdentifier parent = testPlan.getParent(id).orElse(null);
         parent != null && testPlan.getParent(parent).isPresent();
         parent = testPlan.getParent(parent).orElse(null)) {
      if (isSuite(parent)) {
        suites.add(0, getName(parent, testPlan));
      }
    }
    if (isContainerFailure(test)) {
      suites.add(getName(id, testPlan));
    }
    return suites;
  }

  private static boolean isContainerFailure(TestData test) {
    TestExecutionResult result = test.getResult();
    return test.getId().isContainer() && result != null && result.getStatus() == TestExecutionResult.Status.FAILED;
  }

  /**
   * A JUnit 3 suite, a JUnit 4 suite or a JUnit Platform suite. Its name goes before the names of its tests.
   * A class that cannot be loaded is no suite: an exception here would lose the report of the whole engine.
   */
  static boolean isSuite(TestIdentifier id) {
    if (!(id.getSource().orElse(null) instanceof ClassSource classSource)) return false;
    Class<?> aClass;
    try {
      aClass = classSource.getJavaClass();
    }
    catch (PreconditionViolationException e) {
      return false;
    }
    if (TestSuite.class.isAssignableFrom(aClass)) return true;
    RunWith runWith = aClass.getAnnotation(RunWith.class);
    if (runWith != null && Suite.class.isAssignableFrom(runWith.value())) return true;
    return Arrays.stream(aClass.getAnnotations()).anyMatch(a -> "org.junit.platform.suite.api.Suite".equals(a.annotationType().getName()));
  }
}
