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

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

class SMTestSender extends RunListener {
  private String myCurrentClassName;

  public void testRunStarted(Description description) throws Exception {
    //System.out.println("##teamcity[testSuiteStarted name =\'" + description.toString() + "\']");
  }

  public void testRunFinished(Result result) throws Exception {
    if (myCurrentClassName != null) {
      System.out.println("##teamcity[testSuiteFinished name=\'" + myCurrentClassName + "\']");
    }
  }

  public void testStarted(Description description) throws Exception {
    final String className = JUnit4ReflectionUtil.getClassName(description);
    if (myCurrentClassName == null || !myCurrentClassName.equals(className)) {
      if (myCurrentClassName != null) {
        System.out.println("##teamcity[testSuiteFinished name=\'" + myCurrentClassName + "\']");
      }
      myCurrentClassName = className;
      System.out.println("##teamcity[testSuiteStarted name=\'" + className + "\']");
    }
    System.out.println("##teamcity[testStarted name=\'" + JUnit4ReflectionUtil.getMethodName(description) + "\']");
  }

  public void testFinished(Description description) throws Exception {
    System.out.println("##teamcity[testFinished name=\'" + JUnit4ReflectionUtil.getMethodName(description) + "\']");
  }

  public void testFailure(Failure failure) throws Exception {
    System.out.println("##teamcity[testFailed name=\'" +
                       JUnit4ReflectionUtil.getMethodName(failure.getDescription()) +
                       "\' message=\'" +
                       failure.getMessage() +
                       "\' details=\'" +
                       failure.getTrace().replaceAll("\n", "n").replaceAll("\r", "r") +
                       "\']");
  }

  public void testAssumptionFailure(Failure failure) {

  }

  public void testIgnored(Description description) throws Exception {
    System.out.println("##teamcity[testIgnored name=\'" + JUnit4ReflectionUtil.getMethodName(description) + "\']");
  }
}