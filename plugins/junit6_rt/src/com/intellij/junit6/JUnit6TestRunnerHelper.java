// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit6;

import com.intellij.junit5.JUnit5TestRunnerHelper;
import org.junit.platform.commons.support.ReflectionSupport;
import org.junit.platform.engine.CancellationToken;
import org.junit.platform.engine.discovery.MethodSelector;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.LauncherExecutionRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.core.LauncherExecutionRequestBuilder;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

final class JUnit6TestRunnerHelper extends JUnit5TestRunnerHelper {
  private static final int DEFAULT_SHUTDOWN_TIMEOUT = 600;
  private final CancellationToken myCancellationToken = CancellationToken.create();
  private final ReentrantLock myLock = new ReentrantLock();

  JUnit6TestRunnerHelper() {
    int shutdownTimeout = getIntProperty("idea.test.graceful.shutdown.timeout.seconds", DEFAULT_SHUTDOWN_TIMEOUT);

    // soft quit: call CancellationToken and wait for the tests to complete.
    // it works for SIGINT
    // for SIGKILL it will terminate immediately.
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        // send stop signal
        myCancellationToken.cancel();

        // wait for the tests to complete
        // we don't have to release the lock because new tests may start (repeat feature).
        if (shutdownTimeout >= 0) {
          myLock.tryLock(shutdownTimeout, TimeUnit.SECONDS);
        }
        else {
          myLock.lockInterruptibly();
        }
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }, "junit6-shutdown-hook"));
  }


  @Override
  protected boolean loadMethodByReflection(MethodSelector selector) {
    try {
      Class<?> aClass = Class.forName(selector.getClassName());
      return ReflectionSupport.findMethod(aClass, selector.getMethodName(), selector.getParameterTypes()).isPresent();
    }
    catch (ClassNotFoundException e) {
      return false;
    }
  }

  @Override
  public void execute(Launcher launcher, LauncherDiscoveryRequest request, TestExecutionListener[] listeners) {
    myLock.lock();
    try {
      LauncherExecutionRequest executionRequest = LauncherExecutionRequestBuilder.request(request)
        .cancellationToken(myCancellationToken)
        .listeners(listeners)
        .build();
      launcher.execute(executionRequest);
    }
    finally {
      // The tests are completed, the lock should be released for termination.
      myLock.unlock();
    }
  }

  @SuppressWarnings("SameParameterValue")
  private static int getIntProperty(String propertyName, int defaultValue) {
    String property = System.getProperty(propertyName);
    if (property == null) return defaultValue;
    try {
      return Integer.parseInt(property);
    }
    catch (NumberFormatException e) {
      return defaultValue;
    }
  }
}