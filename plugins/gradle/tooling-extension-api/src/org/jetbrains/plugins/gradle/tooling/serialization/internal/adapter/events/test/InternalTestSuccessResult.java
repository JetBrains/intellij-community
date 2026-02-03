// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.test;

import org.gradle.tooling.events.test.TestSuccessResult;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.InternalOperationSuccessResult;

@ApiStatus.Internal
public class InternalTestSuccessResult extends InternalOperationSuccessResult implements TestSuccessResult {
  public InternalTestSuccessResult(long startTime, long endTime) {
    super(startTime, endTime);
  }
}
