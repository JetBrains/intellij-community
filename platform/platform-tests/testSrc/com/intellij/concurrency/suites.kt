// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency

import com.intellij.openapi.application.impl.*
import com.intellij.openapi.progress.*
import com.intellij.util.concurrency.*
import org.junit.platform.suite.api.SelectClasses
import org.junit.platform.suite.api.Suite

@Suite
@SelectClasses(
  // base
  WithThreadLocalTest::class,
  ThreadContextTest::class,

  // contexts
  ContextSwitchTest::class,
  CurrentJobTest::class,
  ExistingThreadContextTest::class,
  IndicatorThreadContextTest::class,
  RunBlockingCancellableTest::class,
  RunWithModalProgressBlockingTest::class,
  WithModalProgressTest::class,
  CoroutineToIndicatorTest::class,

  // rw
  CancellableReadActionWithJobTest::class,
  CancellableReadActionWithIndicatorTest::class,
  BlockingSuspendingReadActionTest::class,
  NonBlockingSuspendingReadActionTest::class,
  NonBlockingUndispatchedSuspendingReadActionTest::class,
  SuspendingWriteActionTest::class,
  SuspendingReadAndWriteActionTest::class,

  // propagation
  ThreadContextPropagationTest::class,
  CancellationPropagationTest::class,
  AlarmContextPropagationTest::class,
  DocumentManagerPropagationTest::class,
  DumbServicePropagationTest::class,
)
class ContextAndCoroutinesSuite
