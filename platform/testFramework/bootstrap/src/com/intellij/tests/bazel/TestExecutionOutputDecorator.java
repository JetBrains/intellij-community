// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests.bazel;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

import java.io.PrintStream;

public final class TestExecutionOutputDecorator implements TestExecutionListener {
  private final PrintStream printStream;

  public TestExecutionOutputDecorator(PrintStream printStream) {
    this.printStream = printStream;
  }

  @Override
  public void executionStarted(TestIdentifier testIdentifier) {
    if (testIdentifier.isTest()) {
      printStream.println("STARTED::" + describeTest(testIdentifier));
    }
  }

  @Override
  public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
    if (testIdentifier.isTest()) {
      printStream.println("FINISHED::" + describeTest(testIdentifier) + " (" + testExecutionResult.getStatus() + ")");
    }
  }

  private static String describeTest(TestIdentifier id) {
    return id.getSource().map(source -> {
        return switch (source) {
          case MethodSource ms -> ms.getClassName() + "." + ms.getMethodName();
          case ClassSource cs -> cs.getClassName();
          default -> source.toString();
        };
      })
      .orElse(id.getUniqueId());
  }
}
