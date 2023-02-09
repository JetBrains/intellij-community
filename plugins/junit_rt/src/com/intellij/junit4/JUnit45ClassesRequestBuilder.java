// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.junit4;

import org.junit.internal.AssumptionViolatedException;
import org.junit.internal.builders.AllDefaultPossibilitiesBuilder;
import org.junit.internal.builders.AnnotatedBuilder;
import org.junit.internal.builders.IgnoredBuilder;
import org.junit.internal.builders.JUnit4Builder;
import org.junit.internal.runners.model.EachTestNotifier;
import org.junit.runner.Description;
import org.junit.runner.Request;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;

import java.lang.reflect.Method;

public final class JUnit45ClassesRequestBuilder {
  public static Request getClassesRequest(String suiteName, Class<?>[] classes) {
    try {
      return Request.runner(new IdeaSuite(new AllDefaultPossibilitiesBuilder(true), classes, suiteName));
    }
    catch (Exception initializationError) {
      initializationError.printStackTrace();
      return null;
    }
  }


  static Request createIgnoreIgnoredClassRequest(final Class<?> clazz, final boolean recursively) throws ClassNotFoundException {
    Class.forName("org.junit.runners.BlockJUnit4ClassRunner"); //ignore IgnoreIgnored for junit4.4 and <
    return new Request() {
      @Override
      public Runner getRunner() {
        try {
          return new AllDefaultPossibilitiesBuilder(true) {
            @Override
            protected IgnoredBuilder ignoredBuilder() {
              return new IgnoredBuilder() {
                @Override
                public Runner runnerForClass(Class testClass) {
                  return null;
                }
              };
            }

            @Override
            protected JUnit4Builder junit4Builder() {
              return new JUnit4Builder() {
                @Override
                public Runner runnerForClass(Class testClass) throws Throwable {
                  if (!recursively) return super.runnerForClass(testClass);
                  try {
                    Method ignored = BlockJUnit4ClassRunner.class.getDeclaredMethod("isIgnored", FrameworkMethod.class);
                    return new BlockJUnit4ClassRunner(testClass) {
                      @Override
                      protected boolean isIgnored(FrameworkMethod child) {
                        return false;
                      }
                    };
                  }
                  catch (NoSuchMethodException ignored) {}
                  //older versions
                  return new BlockJUnit4ClassRunner(testClass) {
                    @Override
                    protected void runChild(FrameworkMethod method, RunNotifier notifier) {
                      final Description description = describeChild(method);
                      final EachTestNotifier eachNotifier = new EachTestNotifier(notifier, description);
                      eachNotifier.fireTestStarted();
                      try {
                        methodBlock(method).evaluate();
                      }
                      catch (AssumptionViolatedException e) {
                        eachNotifier.addFailedAssumption(e);
                      }
                      catch (Throwable e) {
                        eachNotifier.addFailure(e);
                      }
                      finally {
                        eachNotifier.fireTestFinished();
                      }
                    }
                  };
                }
              };
            }
          }.runnerForClass(clazz);
        }
        catch (Throwable throwable) {
          return Request.aClass(clazz).getRunner();
        }
      }
    };
  }

  static Runner createIgnoreAnnotationAndJUnit4ClassRunner(Class<?> clazz) throws Throwable {
    return new AllDefaultPossibilitiesBuilder(true) {
      @Override
      protected AnnotatedBuilder annotatedBuilder() {
        return new AnnotatedBuilder(this) {
          @Override
          public Runner runnerForClass(Class testClass) {
            return null;
          }
        };
      }

      @Override
      protected JUnit4Builder junit4Builder() {
        return new JUnit4Builder() {
          @Override
          public Runner runnerForClass(Class testClass) {
            return null;
          }
        };
      }
    }.runnerForClass(clazz);
  }
}