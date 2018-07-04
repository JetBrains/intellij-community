package org.jetbrains.plugins.cucumber.java.run;

import java.text.SimpleDateFormat;
import java.util.Date;

public class CucumberJvmSMFormatterUtil {
  public static final String TEAMCITY_PREFIX = "##teamcity";

  public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSSZ");

  public static final String FILE_RESOURCE_PREFIX = "file://";

  public static final String TEMPLATE_TEST_STARTED =
    TEAMCITY_PREFIX + "[testStarted timestamp = '%s' locationHint = '%s' captureStandardOutput = 'true' name = '%s']";
  public static final String TEMPLATE_TEST_FAILED =
    TEAMCITY_PREFIX + "[testFailed timestamp = '%s' details = '%s' message = '%s' name = '%s' %s]";
  public static final String TEMPLATE_SCENARIO_FAILED = TEAMCITY_PREFIX + "[customProgressStatus timestamp='%s' type='testFailed']";
  public static final String TEMPLATE_TEST_PENDING =
    TEAMCITY_PREFIX + "[testIgnored name = '%s' message = 'Skipped step' timestamp = '%s']";

  public static final String TEMPLATE_TEST_FINISHED =
    TEAMCITY_PREFIX +
    "[testFinished timestamp = '%s' duration = '%s' name = '%s']";

  public static final String TEMPLATE_ENTER_THE_MATRIX = TEAMCITY_PREFIX + "[enteredTheMatrix timestamp = '%s']";

  public static final String TEMPLATE_TEST_SUITE_STARTED =
    TEAMCITY_PREFIX + "[testSuiteStarted timestamp = '%s' locationHint = 'file://%s' name = '%s']";
  public static final String TEMPLATE_TEST_SUITE_FINISHED = TEAMCITY_PREFIX + "[testSuiteFinished timestamp = '%s' name = '%s']";

  public static final String TEMPLATE_SCENARIO_COUNTING_STARTED =
    TEAMCITY_PREFIX + "[customProgressStatus testsCategory = 'Scenarios' count = '%s' timestamp = '%s']";
  public static final String TEMPLATE_SCENARIO_COUNTING_FINISHED =
    TEAMCITY_PREFIX + "[customProgressStatus testsCategory = '' count = '0' timestamp = '%s']";
  public static final String TEMPLATE_SCENARIO_STARTED = TEAMCITY_PREFIX + "[customProgressStatus type = 'testStarted' timestamp = '%s']";
  public static final String TEMPLATE_SCENARIO_FINISHED = TEAMCITY_PREFIX + "[customProgressStatus type = 'testFinished' timestamp = '%s']";

  public static String getCurrentTime() {
    return DATE_FORMAT.format(new Date());
  }

  public static String escape(String source) {
    if (source == null) {
      return "";
    }
    return source.replace("|", "||").replace("\n", "|n").replace("\r", "|r").replace("'", "|'").replace("[", "|[").replace("]", "|]");
  }

  /**
   * Gets feature title from @param featureHeader
   * From code:
   * <pre>{@code
   *     @wip
   *     Feature: super puper
   *       my feature
   * }</pre>
   * should only line:
   * <pre>{@code
   *   Feature: super puper
   * }</pre> returned
   */
  public static String getFeatureName(String featureHeader) {
    int i = featureHeader.indexOf(":");
    if (i < 0) {
      return featureHeader;
    }

    int start = i;
    while (start >= 0 && !Character.isWhitespace(featureHeader.charAt(start))) {
      start--;
    }
    start++;

    int end = i;
    while (end < featureHeader.length() && featureHeader.charAt(end) != '\n') {
      end++;
    }

    return featureHeader.substring(start, end);
  }
}
