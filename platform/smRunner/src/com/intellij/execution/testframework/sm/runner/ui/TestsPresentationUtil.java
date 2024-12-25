// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.testframework.PoolOfTestIcons;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestIconMapper;
import com.intellij.execution.testframework.sm.SmRunnerBundle;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;
import com.intellij.icons.AllIcons;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Set;
import java.util.function.BooleanSupplier;

import static com.intellij.execution.testframework.sm.runner.ui.SMPoolOfTestIcons.*;

/**
 * @author Roman Chernyatchik
 */
public final class TestsPresentationUtil {
  private static final @NonNls String DOUBLE_SPACE = "  ";
  private static class Holder {
    private static String getNoNameTest() {
      return SmRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.labels.test.noname");
    }
  }
  private static final @NonNls String UNKNOWN_TESTS_COUNT = "<...>";
  static final @NonNls String DEFAULT_TESTS_CATEGORY = "Tests";


  private TestsPresentationUtil() {
  }

  public static @Nls String getProgressStatus_Text(final long startTime,
                                                   final long endTime,
                                                   final int testsTotal,
                                                   final int testsCount,
                                                   final int failuresCount,
                                                   final @Nullable Set<String> allCategories,
                                                   final boolean isFinished) {
    final @Nls StringBuilder sb = new StringBuilder();
    if (endTime == 0) {
      sb.append(SmRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.labels.running"));
    } else {
      sb.append(SmRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.labels.done"));
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
    sb.append(SmRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.labels.of"));
    sb.append(' ').append(testsTotal != 0 ? testsTotal
                                          : !isFinished ? UNKNOWN_TESTS_COUNT : 0);

    if (failuresCount > 0) {
      sb.append(DOUBLE_SPACE);
      sb.append(SmRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.labels.failed"));
      sb.append(' ').append(failuresCount);
    }
    if (endTime != 0) {
      final long time = endTime - startTime;
      sb.append(DOUBLE_SPACE);
      sb.append('(').append(NlsMessages.formatDurationApproximateNarrow(time)).append(')');
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
    IconInfo iconInfo = getIcon(testProxy, renderer.getConsoleProperties());
    renderer.setIcon(iconInfo.getIcon());
    String accessibleStatusText = renderer.getAccessibleStatus();
    if (accessibleStatusText == null) accessibleStatusText = iconInfo.getStatusText();
    renderer.setAccessibleStatusText(accessibleStatusText);

    final TestStateInfo.Magnitude magnitude = testProxy.getMagnitudeInfo();

    final String text;
    final String presentableName = testProxy.getPresentation();
    if (presentableName != null) {
      text = presentableName;
    } else if (magnitude == TestStateInfo.Magnitude.RUNNING_INDEX) {
      text = SmRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.labels.running.tests");
    } else if (magnitude == TestStateInfo.Magnitude.TERMINATED_INDEX) {
      text = SmRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.labels.was.terminated");
    } else {
      text = SmRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.labels.test.results");
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
        IconInfo iconInfo = getIcon(testProxy, renderer.getConsoleProperties());
        renderer.setIcon(iconInfo.getIcon());
        renderer.setAccessibleStatusText(iconInfo.getStatusText());
        renderer.append(SmRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.labels.instantiating.tests"),
                        SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    } else if (magnitude == TestStateInfo.Magnitude.NOT_RUN_INDEX) {
      renderer.setIcon(PoolOfTestIcons.NOT_RAN);
      renderer.append(SmRunnerBundle.message(
          "sm.test.runner.ui.tests.tree.presentation.labels.not.test.results"),
                      SimpleTextAttributes.ERROR_ATTRIBUTES);
    } else if (magnitude == TestStateInfo.Magnitude.TERMINATED_INDEX) {
      renderer.setIcon(PoolOfTestIcons.TERMINATED_ICON);
      renderer.append(SmRunnerBundle.message(
          "sm.test.runner.ui.tests.tree.presentation.labels.was.terminated"),
                      SimpleTextAttributes.REGULAR_ATTRIBUTES);
    } else if (magnitude == TestStateInfo.Magnitude.PASSED_INDEX) {
      renderer.setIcon(PoolOfTestIcons.PASSED_ICON);
      renderer.append(SmRunnerBundle.message(
          "sm.test.runner.ui.tests.tree.presentation.labels.all.tests.passed"),
                      SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
    else if (magnitude == TestStateInfo.Magnitude.IGNORED_INDEX && !testProxy.hasErrors()) {
      renderer.setIcon(PoolOfTestIcons.IGNORED_ICON);
      renderer.append(SmRunnerBundle.message(
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
                        ? SmRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.labels.no.tests.were.found")
                        : SmRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.labels.test.reporter.not.attached"),
                        SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
    }
  }

  public static void formatTestProxy(final SMTestProxy testProxy,
                                     final TestTreeRenderer renderer) {
    IconInfo iconInfo = getIcon(testProxy, renderer.getConsoleProperties());
    renderer.setIcon(iconInfo.getIcon());
    renderer.setAccessibleStatusText(iconInfo.getStatusText());
    renderer.append(testProxy.getPresentableName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  public static @NotNull String getPresentableName(final @NotNull SMTestProxy testProxy) {
    return getPresentableName(testProxy, testProxy.getName());
  }

  public static @NotNull String getPresentableName(final @NotNull SMTestProxy testProxy, final @Nullable String name) {
    if (name == null) {
      return Holder.getNoNameTest();
    }

    final SMTestProxy parent = testProxy.getParent();
    String presentationCandidate = name;
    if (parent != null && !testProxy.isSuite()) {
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
      return Holder.getNoNameTest();
    }

    return presentationCandidate;
  }

  public static @NotNull String getPresentableNameTrimmedOnly(final @Nullable String name) {
    return (name == null || name.isBlank()) ? Holder.getNoNameTest()
                                            : name.trim();
  }

  /**
   * @see TestIconMapper#getToolbarIcon(TestStateInfo.Magnitude, boolean, BooleanSupplier)
   */
  private static @NotNull IconInfo getIcon(final SMTestProxy testProxy,
                                           final TestConsoleProperties consoleProperties) {
    final TestStateInfo.Magnitude magnitude = testProxy.getMagnitudeInfo();

    final boolean hasErrors = testProxy.hasErrors();
    final boolean hasPassedTests = testProxy.hasPassedTests();

    return switch (magnitude) {
      case ERROR_INDEX -> IconInfo.wrap(ERROR_ICON, SmRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.accessible.status.error"));
      case FAILED_INDEX -> hasErrors ? IconInfo.wrap(FAILED_E_ICON, SmRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.accessible.status.failed.with.errors"))
                                     : IconInfo.wrap(FAILED_ICON, SmRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.accessible.status.failed"));
      case IGNORED_INDEX -> hasErrors ? IconInfo.wrap(IGNORED_E_ICON, SmRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.accessible.status.ignored.with.errors"))
                                      : (hasPassedTests ? IconInfo.wrap(PASSED_IGNORED, SmRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.accessible.status.passed.with.ignored"))
                                                        : IconInfo.wrap(IGNORED_ICON, SmRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.accessible.status.ignored")));
      case NOT_RUN_INDEX -> IconInfo.wrap(NOT_RAN, SmRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.accessible.status.not.ran"));
      case COMPLETE_INDEX, PASSED_INDEX -> hasErrors ? IconInfo.wrap(PASSED_E_ICON, SmRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.accessible.status.passed.with.errors"))
                                                     : IconInfo.wrap(PASSED_ICON, SmRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.accessible.status.passed"));
      case RUNNING_INDEX -> {
        if (consoleProperties.isPaused()) {
          yield hasErrors ? IconInfo.wrap(PAUSED_E_ICON, SmRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.accessible.status.paused.with.errors"))
                          : IconInfo.wrap(AllIcons.RunConfigurations.TestPaused, SmRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.accessible.status.paused"));
        }
        else {
          yield hasErrors ? IconInfo.wrap(RUNNING_E_ICON, SmRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.accessible.status.running.with.errors"))
                          : IconInfo.wrap(RUNNING_ICON, SmRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.accessible.status.running"));
        }
      }
      case SKIPPED_INDEX -> hasErrors ? IconInfo.wrap(SKIPPED_E_ICON, SmRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.accessible.status.skipped.with.errors"))
                                      : IconInfo.wrap(SKIPPED_ICON, SmRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.accessible.status.skipped"));
      case TERMINATED_INDEX -> hasErrors ? IconInfo.wrap(TERMINATED_E_ICON, SmRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.accessible.status.terminated.with.errors"))
                                         : IconInfo.wrap(TERMINATED_ICON, SmRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.accessible.status.terminated"));
    };
  }

  public static @Nullable String getTestStatusPresentation(final SMTestProxy proxy) {
    return proxy.getMagnitudeInfo().getTitle();
  }

  private static class IconInfo {
    private final Icon myIcon;
    private final @Nls String myStatusText;

    private IconInfo(Icon icon, @Nls String statusText) {
      myIcon = icon;
      myStatusText = statusText;
    }

    private Icon getIcon() {
      return myIcon;
    }

    private @Nls String getStatusText() {
      return myStatusText;
    }

    static IconInfo wrap(Icon icon, @Nls String statusText) {
      return new IconInfo(icon, statusText);
    }
  }
}
