/**
 * This file was originally part of [rules_jvm] (https://github.com/bazel-contrib/rules_jvm)
 * Original source:
 * https://github.com/bazel-contrib/rules_jvm/blob/201fa7198cfd50ae4d686715651500da656b368a/java/src/com/github/bazel_contrib/contrib_rules_jvm/junit5/TestData.java
 * Licensed under the Apache License, Version 2.0
 */
package com.intellij.tests.bazel;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.intellij.tests.IgnoreException;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.launcher.TestIdentifier;
import org.opentest4j.TestAbortedException;

class TestData {
  private final TestIdentifier id;
  private final List<ReportEntry> reportEntries = Collections.synchronizedList(new ArrayList<>());
  private Instant started = Instant.now();
  // Commented out to avoid pulling in the dependency, but present for documentation purposes.
  // @Nullable
  private Instant finished = null;
  private String reason;
  private TestExecutionResult result;
  private boolean dynamic;

  TestData(TestIdentifier id) {
    this.id = id;
  }

  public TestIdentifier getId() {
    return id;
  }

  public TestData started() {
    this.started = Instant.now();
    return this;
  }

  public TestData mark(TestExecutionResult result) {
    if (result.getStatus() == TestExecutionResult.Status.ABORTED) {
      Optional<Throwable> maybeThrowable = result.getThrowable();
      if (maybeThrowable.isPresent() && maybeThrowable.get() instanceof TestAbortedException) {
        skipReason(maybeThrowable.get().getMessage());
      }
    }

    this.finished = Instant.now();
    this.result = result;
    return this;
  }

  public TestData skipReason(String reason) {
    this.reason = reason;
    return this;
  }

  public String getSkipReason() {
    return reason;
  }

  public TestExecutionResult getResult() {
    return result;
  }

  /** Returns how long the test took to run - will be absent if the test has not yet completed. */
  // Commented out to avoid pulling in the dependency, but present for documentation purposes.
  // @Nullable
  public Duration getDuration() {
    if (finished == null) {
      return null;
    }
    return Duration.between(started, finished);
  }

  public boolean isError() {
    TestExecutionResult result = getResult();
    if (result == null
        || result.getStatus() == TestExecutionResult.Status.SUCCESSFUL
        || isSkipped()) {
      return false;
    }

    return result.getThrowable().map(thr -> thr instanceof AssertionError).orElse(false);
  }

  public boolean isFailure() {
    TestExecutionResult result = getResult();
    if (result == null
        || result.getStatus() == TestExecutionResult.Status.SUCCESSFUL
        || isSkipped()) {
      return false;
    }
    if (result.getStatus() == TestExecutionResult.Status.ABORTED) {
      return true;
    }

    return result.getThrowable().map(thr -> (!(thr instanceof AssertionError))).orElse(false);
  }

  public boolean isDisabled() {
    return getResult() == null;
  }

  public boolean isSkipped() {
    if (getResult() == null) {
      return false;
    }

    if (getSkipReason() != null) {
      return true;
    }

    return getResult().getThrowable().map(IgnoreException::isIgnoringThrowable).orElse(false);
  }

  public TestData addReportEntry(ReportEntry entry) {
    reportEntries.add(entry);
    return this;
  }

  public String getStdOut() {
    return reportEntries.stream()
      .map(EntryDetails::getStdOut)
      .filter(Objects::nonNull)
      .findFirst()
      .orElse(null);
  }

  public String getStdErr() {
    return reportEntries.stream()
      .map(EntryDetails::getStdErr)
      .filter(Objects::nonNull)
      .findFirst()
      .orElse(null);
  }

  public TestData setDynamic(boolean isDynamic) {
    this.dynamic = isDynamic;
    return this;
  }

  public boolean isDynamic() {
    return dynamic;
  }

  public Instant getStarted() {
    return this.started;
  }

  @Override
  public String toString() {
    return "TestData{"
           + "id="
           + id
           + ", reportEntries="
           + reportEntries
           + ", started="
           + started
           + ", finished="
           + finished
           + ", reason='"
           + reason
           + '\''
           + ", result="
           + result
           + ", dynamic="
           + dynamic
           + '}';
  }
}