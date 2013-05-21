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

/*
 * User: anna
 * Date: 03-Jun-2009
 */
package com.intellij.junit4;

import com.intellij.rt.execution.junit.*;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes;
import junit.framework.ComparisonFailure;
import org.junit.Ignore;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class SMTestSender extends RunListener {
  private static final String JUNIT_FRAMEWORK_COMPARISON_NAME = ComparisonFailure.class.getName();
  private static final String ORG_JUNIT_COMPARISON_NAME = "org.junit.ComparisonFailure";

  private String myCurrentClassName;

  public void testRunStarted(Description description) throws Exception {
    myCurrentClassName = description.toString();
    System.out.println("##teamcity[testSuiteStarted name =\'" + myCurrentClassName + "\']");
  }

  public void testRunFinished(Result result) throws Exception {
    if (myCurrentClassName != null) {
      System.out.println("##teamcity[testSuiteFinished name=\'" + myCurrentClassName + "\']");
    }
  }

  public void testStarted(Description description) throws Exception {
    System.out.println("##teamcity[testStarted name=\'" + JUnit4ReflectionUtil.getMethodName(description) + "\']");
  }

  public void testFinished(Description description) throws Exception {
    System.out.println("##teamcity[testFinished name=\'" + JUnit4ReflectionUtil.getMethodName(description) + "\']");
  }

  public void testFailure(Failure failure) throws Exception {
    final String failureMessage = failure.getMessage();
    final String trace = failure.getTrace();
    final Map attrs = new HashMap();
    attrs.put("name", JUnit4ReflectionUtil.getMethodName(failure.getDescription()));
    attrs.put("message", failureMessage != null ? failureMessage : "");
    final ComparisonFailureData notification = createExceptionNotification(failure.getException());
    if (notification != null) {
      attrs.put("expected", notification.getExpected());
      attrs.put("actual", notification.getActual());

      final int failureIdx = trace.indexOf(failureMessage);
      attrs.put("details", failureIdx > -1 ? trace.substring(failureIdx + failureMessage.length()) : trace);
    } else {
      attrs.put("details", trace);
      attrs.put("error", "true");
    }

    System.out.println(ServiceMessage.asString(ServiceMessageTypes.TEST_FAILED, attrs));
  }

  public void testAssumptionFailure(Failure failure) {
    prepareIgnoreMessage(failure.getDescription(), false);
  }

  public synchronized void testIgnored(Description description) throws Exception {
    prepareIgnoreMessage(description, true);
  }

  private static void prepareIgnoreMessage(Description description, boolean commentMessage) {
    Map attrs = new HashMap();
    if (commentMessage) {
      try {
        final Ignore ignoredAnnotation = (Ignore)description.getAnnotation(Ignore.class);
        if (ignoredAnnotation != null) {
          final String val = ignoredAnnotation.value();
          if (val != null) {
            attrs.put("message", val);
          }
        }
      }
      catch (NoSuchMethodError ignored) {
        //junit < 4.4
      }
    }
    attrs.put("name", JUnit4ReflectionUtil.getMethodName(description));
    System.out.println(ServiceMessage.asString(ServiceMessageTypes.TEST_IGNORED, attrs));
  }

  private static boolean isComparisonFailure(Throwable throwable) {
    if (throwable == null) return false;
    return isComparisonFailure(throwable.getClass());
  }

  private static boolean isComparisonFailure(Class aClass) {
    if (aClass == null) return false;
    final String throwableClassName = aClass.getName();
    if (throwableClassName.equals(JUNIT_FRAMEWORK_COMPARISON_NAME) || throwableClassName.equals(ORG_JUNIT_COMPARISON_NAME)) return true;
    return isComparisonFailure(aClass.getSuperclass());
  }

  private static ComparisonFailureData createExceptionNotification(Throwable assertion) {
    if (isComparisonFailure(assertion)) {
      return ComparisonFailureData.create(assertion);
    }
    final Throwable cause = assertion.getCause();
    if (isComparisonFailure(cause)) {
      try {
        return ComparisonFailureData.create(assertion);
      }
      catch (Throwable ignore) {
      }
    }

    final String message = assertion.getMessage();
    if (message != null) {
      ComparisonFailureData notification = createExceptionNotification(message, "\nExpected: is \"(.*)\"\n\\s*got: \"(.*)\"\n");
      if (notification == null) {
        notification = createExceptionNotification(message, "\nExpected: is \"(.*)\"\n\\s*but: was \"(.*)\"");
      }
      if (notification == null) {
        notification = createExceptionNotification(message, "\nExpected: (.*)\n\\s*got: (.*)");
      }
      if (notification == null) {
        notification = createExceptionNotification(message, "\\s*expected same:<(.*)> was not:<(.*)>");
      }
      if (notification == null) {
        notification = createExceptionNotification(message, "\\s*expected:<(.*)> but was:<(.*)>");
      }
      if (notification == null) {
        notification = createExceptionNotification(message, "\nExpected: \"(.*)\"\n\\s*but: was \"(.*)\"");
      }
      if (notification != null) {
        return notification;
      }
    }
    return null;
  }

  private static ComparisonFailureData createExceptionNotification(String message, final String regex) {
    final Matcher matcher = Pattern.compile(regex, Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(message);
    if (matcher.matches()) {
      return new ComparisonFailureData(matcher.group(1).replaceAll("\\\\n", "\n"), matcher.group(2).replaceAll("\\\\n", "\n"));
    }
    return null;
  }
}