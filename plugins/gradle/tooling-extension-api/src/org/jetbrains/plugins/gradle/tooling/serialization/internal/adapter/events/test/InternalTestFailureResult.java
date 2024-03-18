// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.test;

import org.gradle.tooling.Failure;
import org.gradle.tooling.events.test.TestFailureResult;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.InternalOperationFailureResult;

import java.util.List;

@ApiStatus.Internal
public class InternalTestFailureResult extends InternalOperationFailureResult implements TestFailureResult {
  public InternalTestFailureResult(long startTime, long endTime, List<? extends Failure> failures) {
    super(startTime, endTime, failures);
  }
}
