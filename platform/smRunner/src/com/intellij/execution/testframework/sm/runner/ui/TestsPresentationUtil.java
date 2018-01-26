/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.execution.testframework.sm.SMTestsRunnerBundle;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;
import com.intellij.execution.testframework.ui.TestsProgressAnimator;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Set;

import static com.intellij.execution.testframework.sm.runner.ui.SMPoolOfTestIcons.*;

/**
 * @author Roman Chernyatchik
 */
public class TestsPresentationUtil {
  @NonNls private static final String DOUBLE_SPACE = "  ";
  @NonNls private static final String NO_NAME_TEST = SMTestsRunnerBundle.message(
      "sm.test.runner.ui.tests.tree.presentation.labels.test.noname");
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

      if (hasNonDefaultCategories(allCategories)) {

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
      sb.append('(').append(StringUtil.formatDuration(time, "\u2009")).append(')');
    }
    sb.append(DOUBLE_SPACE);

    return sb.toString();
  }

  public static boolean hasNonDefaultCategories(@Nullable Set<String> allCategories) {
    if (allCategories == null) {
      return false;
    }
    return allCategories.size() > 1 || (allCategories.size() == 1 && !DEFAULT_TESTS_CATEGORY.equals(allCategories.iterator().next()));
  }

  public static void formatRootNodeWithChildren(final SMTestProxy.SMRootTestProxy testProxy,
                                                final TestTreeRenderer renderer) {
    renderer.setIcon(getIcon(testProxy, renderer.getConsoleProperties()));

    final TestStateInfo.Magnitude magnitude = testProxy.getMagnitudeInfo();

    final String text;
    final String presentableName = testProxy.getPresentation();
    if (presentableName != null) {
      text = presentableName;
    } else if (magnitude == TestStateInfo.Magnitude.RUNNING_INDEX) {
      text = SMTestsRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.labels.running.tests");
    } else if (magnitude == TestStateInfo.Magnitude.TERMINATED_INDEX) {
      text = SMTestsRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.labels.was.terminated");
    } else {
      text = SMTestsRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.labels.test.results");
    }
    renderer.append(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    final String comment = testProxy.getComment();
    if (comment != null) {
      renderer.append(" (" + comment + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  public static void formatRootNodeWithoutChildren(final SMTestProxy.SMRootTestProxy testProxy,
                                                   final TestTreeRenderer renderer) {
    final TestStateInfo.Magnitude magnitude = testProxy.getMagnitudeInfo();
    if (magnitude == TestStateInfo.Magnitude.RUNNING_INDEX) {
      if (!testProxy.getChildren().isEmpty()) {
        formatRootNodeWithChildren(testProxy, renderer);
      } else {
        renderer.setIcon(getIcon(testProxy, renderer.getConsoleProperties()));
        renderer.append(SMTestsRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.labels.instantiating.tests"),
                        SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
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
    else if (magnitude == TestStateInfo.Magnitude.IGNORED_INDEX && !testProxy.hasErrors()) {
      renderer.setIcon(PoolOfTestIcons.IGNORED_ICON);
      renderer.append(SMTestsRunnerBundle.message(
        "sm.test.runner.ui.tests.tree.presentation.labels.all.but.ignored.passed"),
                      SimpleTextAttributes.REGULAR_ATTRIBUTES );
    }
    else {
      if (!testProxy.getChildren().isEmpty()) {
        // some times test proxy may be updated faster than tests tree
        // so let's process such situation correctly
        formatRootNodeWithChildren(testProxy, renderer);
      }
      else {
        renderer.setIcon(PoolOfTestIcons.NOT_RAN);
        renderer.append(testProxy.isTestsReporterAttached()
                        ? SMTestsRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.labels.no.tests.were.found")
                        : SMTestsRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.labels.test.reporter.not.attached"),
                        SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
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

    if (name == null) {
      return NO_NAME_TEST;
    }

    String presentationCandidate = name;
    if (parent != null) {
      String parentName = parent.getName();
      if (parentName != null) {
        boolean parentStartsWith = name.startsWith(parentName);
        if (!parentStartsWith && parent instanceof SMTestProxy.SMRootTestProxy) {
          final String presentation = ((SMTestProxy.SMRootTestProxy)parent).getPresentation();
          if (presentation != null) {
            parentName = presentation;
            parentStartsWith = name.startsWith(parentName);

            if (!parentStartsWith) {
              String comment = ((SMTestProxy.SMRootTestProxy)parent).getComment();
              if (comment != null) {
                parentName = StringUtil.getQualifiedName(comment, presentation);
                parentStartsWith = name.startsWith(parentName);
              }
            }
          }
        }
        if (parentStartsWith) {
          presentationCandidate = name.substring(parentName.length());

          // remove "." separator
          presentationCandidate = StringUtil.trimStart(presentationCandidate, ".");
        }
      }
    }

    // trim
    presentationCandidate = presentationCandidate.trim();

    // remove extra spaces
    presentationCandidate = presentationCandidate.replaceAll("\\s+", " ");

    if (StringUtil.isEmpty(presentationCandidate)) {
      return NO_NAME_TEST;
    }

    return presentationCandidate;
  }

  @NotNull
  public static String getPresentableNameTrimmedOnly(@NotNull SMTestProxy testProxy) {
    String name = testProxy.getName();
    if (name != null) {
      name = name.trim();
    }
    if (name == null || name.isEmpty()) {
      name = NO_NAME_TEST;
    }
    return name;
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
          return hasErrors ? PAUSED_E_ICON : AllIcons.RunConfigurations.TestPaused;
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
}
