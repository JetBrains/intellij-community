/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.testframework.PoolOfTestIcons;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestsUIUtil;
import com.intellij.execution.testframework.sm.SMTestsRunnerBundle;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;
import com.intellij.execution.testframework.ui.TestsProgressAnimator;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Set;

import static com.intellij.execution.testframework.sm.runner.ui.SMPoolOfTestIcons.*;

/**
 * @author Roman Chernyatchik
 */
public class TestsPresentationUtil {
  @NonNls private static final String DOUBLE_SPACE = "  ";
  @NonNls private static final String SECONDS_SUFFIX = " " + SMTestsRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.labels.seconds");
  @NonNls private static final String MILLISECONDS_SUFFIX = " " + SMTestsRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.labels.milliseconds");
  @NonNls private static final String WORLD_CREATION_TIME = "0" + SECONDS_SUFFIX;
  @NonNls private static final String DURATION_UNKNOWN = SMTestsRunnerBundle.message(
      "sm.test.runner.ui.tabs.statistics.columns.duration.unknown");
  @NonNls private static final String DURATION_NO_TESTS = SMTestsRunnerBundle.message(
      "sm.test.runner.ui.tabs.statistics.columns.duration.no.tests");
  @NonNls private static final String DURATION_NOT_RUN = SMTestsRunnerBundle.message(
      "sm.test.runner.ui.tabs.statistics.columns.duration.not.run");
  @NonNls private static final String DURATION_RUNNING_PREFIX = SMTestsRunnerBundle.message(
      "sm.test.runner.ui.tabs.statistics.columns.duration.prefix.running");
  @NonNls private static final String DURATION_TERMINATED_PREFIX = SMTestsRunnerBundle.message(
      "sm.test.runner.ui.tabs.statistics.columns.duration.prefix.terminated");
  @NonNls private static final String COLON = ": ";
  public static final SimpleTextAttributes PASSED_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, TestsUIUtil.PASSED_COLOR);
  public static final SimpleTextAttributes DEFFECT_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, Color.RED);
  public static final SimpleTextAttributes TERMINATED_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, Color.ORANGE);
  @NonNls private static final String RESULTS_NO_TESTS = SMTestsRunnerBundle.message(
      "sm.test.runner.ui.tabs.statistics.columns.results.no.tests");
  @NonNls private static final String UNKNOWN_TESTS_COUNT = "<...>";
  @NonNls static final String DEFAULT_TESTS_CATEGORY = "Tests";


  private TestsPresentationUtil() {
  }

  public static String getProgressStatus_Text(final long startTime,
                                              final long endTime,
                                              final int testsTotal,
                                              final int testsCount,
                                              final int failuresCount,
                                              @Nullable final Set<String> allCategories,
                                              final boolean isFinished) {
    final StringBuilder sb = new StringBuilder();
    if (endTime == 0) {
      sb.append(SMTestsRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.labels.running"));
    } else {
      sb.append(SMTestsRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.labels.done"));
    }

    if (allCategories != null) {
      // if all categories is just one default tests category - let's do not add prefixes

      if (allCategories.size() > 1
          || (allCategories.size() == 1 && !DEFAULT_TESTS_CATEGORY.equals(allCategories.iterator().next()))) {

        sb.append(' ');
        boolean first = true;
        for (String category : allCategories) {
          if (StringUtil.isEmpty(category)) {
            continue;
          }

          // separator
          if (!first) {
            sb.append(", ");

          }

          // first symbol - to lower case
          final char firstChar = category.charAt(0);
          sb.append(first ? firstChar : Character.toLowerCase(firstChar));

          sb.append(category.substring(1));
          first = false;
        }
      }
    }

    sb.append(' ').append(testsCount).append(' ');
    sb.append(SMTestsRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.labels.of"));
    sb.append(' ').append(testsTotal != 0 ? testsTotal
                                          : !isFinished ? UNKNOWN_TESTS_COUNT : 0);

    if (failuresCount > 0) {
      sb.append(DOUBLE_SPACE);
      sb.append(SMTestsRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.labels.failed"));
      sb.append(' ').append(failuresCount);
    }
    if (endTime != 0) {
      final long time = endTime - startTime;
      sb.append(DOUBLE_SPACE);
      sb.append('(').append(convertToSecondsOrMs(time)).append(')');
    }
    sb.append(DOUBLE_SPACE);

    return sb.toString();
  }

  public static void formatRootNodeWithChildren(final SMTestProxy testProxy,
                                          final TestTreeRenderer renderer) {
    renderer.setIcon(getIcon(testProxy, renderer.getConsoleProperties()));

    final TestStateInfo.Magnitude magnitude = testProxy.getMagnitudeInfo();

    final String text;
    if (magnitude == TestStateInfo.Magnitude.RUNNING_INDEX) {
      text = SMTestsRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.labels.running.tests");
    } else if (magnitude == TestStateInfo.Magnitude.TERMINATED_INDEX) {
      text = SMTestsRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.labels.was.terminated");
    } else {
      text = SMTestsRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.labels.test.results");
    }
    renderer.append(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  public static void formatRootNodeWithoutChildren(final SMTestProxy testProxy,
                                                   final TestTreeRenderer renderer) {
    final TestStateInfo.Magnitude magnitude = testProxy.getMagnitudeInfo();
    if (magnitude == TestStateInfo.Magnitude.RUNNING_INDEX) {
      renderer.setIcon(getIcon(testProxy, renderer.getConsoleProperties()));
      renderer.append(SMTestsRunnerBundle.message(
          "sm.test.runner.ui.tests.tree.presentation.labels.instantiating.tests"),
                      SimpleTextAttributes.REGULAR_ATTRIBUTES);
    } else if (magnitude == TestStateInfo.Magnitude.NOT_RUN_INDEX) {
      renderer.setIcon(PoolOfTestIcons.NOT_RAN);
      renderer.append(SMTestsRunnerBundle.message(
          "sm.test.runner.ui.tests.tree.presentation.labels.not.test.results"),
                      SimpleTextAttributes.ERROR_ATTRIBUTES);
    } else if (magnitude == TestStateInfo.Magnitude.TERMINATED_INDEX) {
      renderer.setIcon(PoolOfTestIcons.TERMINATED_ICON);
      renderer.append(SMTestsRunnerBundle.message(
          "sm.test.runner.ui.tests.tree.presentation.labels.was.terminated"),
                      SimpleTextAttributes.REGULAR_ATTRIBUTES);
    } else if (magnitude == TestStateInfo.Magnitude.PASSED_INDEX) {
      renderer.setIcon(PoolOfTestIcons.PASSED_ICON);
      renderer.append(SMTestsRunnerBundle.message(
          "sm.test.runner.ui.tests.tree.presentation.labels.all.tests.passed"),
                      SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
    else {
      renderer.setIcon(PoolOfTestIcons.NOT_RAN);
      renderer.append(SMTestsRunnerBundle.message(
          "sm.test.runner.ui.tests.tree.presentation.labels.no.tests.were.found"),
                      SimpleTextAttributes.ERROR_ATTRIBUTES);
    }
  }

  public static void formatTestProxy(final SMTestProxy testProxy,
                                     final TestTreeRenderer renderer) {
    renderer.setIcon(getIcon(testProxy, renderer.getConsoleProperties()));
    renderer.append(testProxy.getPresentableName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  @NotNull
  public static String getPresentableName(final SMTestProxy testProxy) {
    final SMTestProxy parent = testProxy.getParent();
    final String name = testProxy.getName();

    if (parent != null) {
      final String parentName = parent.getName();
      if (name.startsWith(parentName)) {
        final String presentationCandidate = name.substring(parentName.length());
        if (presentationCandidate.startsWith(".")) {
          return presentationCandidate.substring(1).trim();
        }
        return presentationCandidate.trim();
      }
    }

    return name.trim();

  }

  @Nullable
  private static Icon getIcon(final SMTestProxy testProxy,
                              final TestConsoleProperties consoleProperties) {
    final TestStateInfo.Magnitude magnitude = testProxy.getMagnitudeInfo();

    final boolean hasErrors = testProxy.hasErrors();

    switch (magnitude) {
      case ERROR_INDEX:
        return ERROR_ICON;
      case FAILED_INDEX:
        return hasErrors ? FAILED_E_ICON : FAILED_ICON;
      case IGNORED_INDEX:
        return hasErrors ? IGNORED_E_ICON : IGNORED_ICON;
      case NOT_RUN_INDEX:
        return NOT_RAN;
      case COMPLETE_INDEX:
      case PASSED_INDEX:
        return hasErrors ? PASSED_E_ICON : PASSED_ICON;
      case RUNNING_INDEX:
        if (consoleProperties.isPaused()) {
          return hasErrors ? PAUSED_E_ICON : TestsProgressAnimator.PAUSED_ICON;
        }
        else {
          final int frameIndex = TestsProgressAnimator.getCurrentFrameIndex();
          return hasErrors ? FRAMES_E[frameIndex] : TestsProgressAnimator.FRAMES[frameIndex];
        }
      case SKIPPED_INDEX:
        return hasErrors ? SKIPPED_E_ICON : SKIPPED_ICON;
      case TERMINATED_INDEX:
        return hasErrors ? TERMINATED_E_ICON : TERMINATED_ICON;
    }
    return null;
  }

  @Nullable
  public static String getTestStatusPresentation(final SMTestProxy proxy) {
    return proxy.getMagnitudeInfo().getTitle();
  }

  public static void appendSuiteStatusColorPresentation(final SMTestProxy proxy,
                                                        final ColoredTableCellRenderer renderer) {
    int passedCount = 0;
    int errorsCount = 0;
    int failedCount = 0;
    int ignoredCount = 0;

    if (proxy.isLeaf()) {
      // If suite is empty show <no tests> label and exit from method
      renderer.append(RESULTS_NO_TESTS, proxy.wasLaunched() ? PASSED_ATTRIBUTES : DEFFECT_ATTRIBUTES);
      return;
    }

    final List<SMTestProxy> allTestCases = proxy.getAllTests();
    for (SMTestProxy testOrSuite : allTestCases) {
      // we should ignore test suites
      if (testOrSuite.isSuite()) {
        continue;
      }
      // if test check it state
      switch (testOrSuite.getMagnitudeInfo()) {
        case COMPLETE_INDEX:
        case PASSED_INDEX:
          passedCount++;
          break;
        case ERROR_INDEX:
          errorsCount++;
          break;
        case FAILED_INDEX:
          failedCount++;
          break;
        case IGNORED_INDEX:
        case SKIPPED_INDEX:
          ignoredCount++;
          break;
        case NOT_RUN_INDEX:
        case TERMINATED_INDEX:
        case RUNNING_INDEX:
          //Do nothing
          break;
      }
    }

    final String separator = " ";

    if (failedCount > 0) {
      renderer.append(SMTestsRunnerBundle.message(
          "sm.test.runner.ui.tabs.statistics.columns.results.count.msg.failed",
                                      failedCount) + separator,
                      DEFFECT_ATTRIBUTES);
    }

    if (errorsCount > 0) {
      renderer.append(SMTestsRunnerBundle.message(
          "sm.test.runner.ui.tabs.statistics.columns.results.count.msg.errors",
                                      errorsCount) + separator,
                      DEFFECT_ATTRIBUTES);
    }

    if (ignoredCount > 0) {
      renderer.append(SMTestsRunnerBundle.message(
          "sm.test.runner.ui.tabs.statistics.columns.results.count.msg.ignored",
                                      ignoredCount) + separator,
                      SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
    }

    if (passedCount > 0) {
      renderer.append(SMTestsRunnerBundle.message(
          "sm.test.runner.ui.tabs.statistics.columns.results.count.msg.passed",
                                      passedCount),
                      PASSED_ATTRIBUTES);
    }
  }

  /**
   * @param proxy Test or Suite
   * @return Duration presentation for given proxy
   */
  @Nullable
  public static String getDurationPresentation(final SMTestProxy proxy) {
    switch (proxy.getMagnitudeInfo()) {
      case COMPLETE_INDEX:
      case PASSED_INDEX:
      case FAILED_INDEX:
      case ERROR_INDEX:
      case IGNORED_INDEX:
      case SKIPPED_INDEX:
        return getDurationTimePresentation(proxy);

      case NOT_RUN_INDEX:
        return DURATION_NOT_RUN;

      case RUNNING_INDEX:
        return getDurationWithPrefixPresentation(proxy, DURATION_RUNNING_PREFIX);

      case TERMINATED_INDEX:
        return getDurationWithPrefixPresentation(proxy, DURATION_TERMINATED_PREFIX);

      default:
        return DURATION_UNKNOWN;
    }
  }

  private static String getDurationWithPrefixPresentation(final SMTestProxy proxy,
                                                          final String prefix) {
    // If duration is known
    if (proxy.getDuration() != null) {
      return prefix + COLON + getDurationTimePresentation(proxy);
    }

    return '<' + prefix + '>';
  }

  private static String getDurationTimePresentation(final SMTestProxy proxy) {
    final Integer duration = proxy.getDuration();

    if (duration == null) {
      // if suite without children
      return proxy.isSuite() && proxy.isLeaf()
             ? DURATION_NO_TESTS
             : DURATION_UNKNOWN;
    } else {
      return convertToSecondsOrMs(duration.longValue());
    }
  }

  /**
   * @param duration In milliseconds
   * @return Value in seconds or millisecond depending on its value
   */
  private static String convertToSecondsOrMs(@NotNull final Long duration) {
    if (duration == 0) {
      return WORLD_CREATION_TIME;
    } else if (duration < 100) {
      return String.valueOf(duration) + MILLISECONDS_SUFFIX;
    } else {
      return String.valueOf(duration.floatValue() / 1000) + SECONDS_SUFFIX;
    }
  }

  public static void appendTestStatusColorPresentation(final SMTestProxy proxy,
                                                       final ColoredTableCellRenderer renderer) {
    final String title = getTestStatusPresentation(proxy);

    final TestStateInfo.Magnitude info = proxy.getMagnitudeInfo();
    switch (info) {
      case COMPLETE_INDEX:
      case PASSED_INDEX:
        renderer.append(title, PASSED_ATTRIBUTES);
        break;
      case RUNNING_INDEX:
        renderer.append(title, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        break;
      case NOT_RUN_INDEX:
        renderer.append(title, SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
        break;
      case IGNORED_INDEX:
      case SKIPPED_INDEX:
        renderer.append(title, SimpleTextAttributes.EXCLUDED_ATTRIBUTES);
        break;
      case ERROR_INDEX:
      case FAILED_INDEX:
        renderer.append(title, DEFFECT_ATTRIBUTES);
        break;
      case TERMINATED_INDEX:
        renderer.append(title, TERMINATED_ATTRIBUTES);
        break;
    }
  }
}
