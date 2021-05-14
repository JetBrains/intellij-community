// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import junit.framework.Test;
import org.junit.Assume;
import org.junit.AssumptionViolatedException;
import org.junit.internal.runners.JUnit38ClassRunner;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runner.notification.StoppedByUserException;

/**
 * JUnit 3 tests don't support "ignore" state.
 * We extends the default junit 3 behaviour adding {@link Assume} support for such test.
 * 
 * After adding the runner using @RunWith(JSUnit38AssumeSupportRunner.class) for the test class 
 * you can use {@link Assume} to control test behaviour depends on some external conditions.
 * 
 * @apiNote it is required only for JUnit3 tests (e.g. void testMyTestName()) 
 * if you use JUnit4 tests style (@Test void testMyTestName()) you don't need it 
 */
public class JUnit38AssumeSupportRunner extends JUnit38ClassRunner {
  
  @SuppressWarnings("unused") //used by reflection
  public JUnit38AssumeSupportRunner(Class<?> klass) {
    super(klass);
  }

  @SuppressWarnings("unused") //used by reflection
  public JUnit38AssumeSupportRunner(Test test) {
    super(test);
  }

  @Override
  public void run(RunNotifier originalNotifier) {
    RunNotifier wrapperNotifier = new RunNotifier() {

      @Override
      public void addListener(RunListener listener) {
        originalNotifier.addListener(listener);
      }

      @Override
      public void removeListener(RunListener listener) {
        originalNotifier.removeListener(listener);
      }

      @Override
      public void fireTestRunStarted(Description description) {
        originalNotifier.fireTestRunStarted(description);
      }

      @Override
      public void fireTestRunFinished(Result result) {
        originalNotifier.fireTestRunFinished(result);
      }

      @Override
      public void fireTestStarted(Description description) throws StoppedByUserException {
        originalNotifier.fireTestStarted(description);
      }

      @Override
      public void fireTestFailure(Failure failure) {
        if (failure.getException() instanceof AssumptionViolatedException) {
          originalNotifier.fireTestAssumptionFailed(failure);
          return;
        }
        originalNotifier.fireTestFailure(failure);
      }

      @Override
      public void fireTestAssumptionFailed(Failure failure) {
        originalNotifier.fireTestAssumptionFailed(failure);
      }

      @Override
      public void fireTestIgnored(Description description) {
        originalNotifier.fireTestIgnored(description);
      }

      @Override
      public void fireTestFinished(Description description) {
        originalNotifier.fireTestFinished(description);
      }

      @Override
      public void pleaseStop() {
        originalNotifier.pleaseStop();
        super.pleaseStop();
      }
    };
    
    super.run(wrapperNotifier);
  }
  
}
