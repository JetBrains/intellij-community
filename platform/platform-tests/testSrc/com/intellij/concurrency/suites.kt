// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency

import com.intellij.openapi.application.impl.BackgroundWriteActionTest
import com.intellij.openapi.application.impl.BlockingSuspendingReadActionTest
import com.intellij.openapi.application.impl.CancellableReadActionWithIndicatorTest
import com.intellij.openapi.application.impl.CancellableReadActionWithJobTest
import com.intellij.openapi.application.impl.EdtCoroutineDispatcherTest
import com.intellij.openapi.application.impl.ImplicitReadTest
import com.intellij.openapi.application.impl.LaterInvocatorTest
import com.intellij.openapi.application.impl.LockDowngradingTest
import com.intellij.openapi.application.impl.ModalCoroutineTest
import com.intellij.openapi.application.impl.NonBlockingFlushQueueTest
import com.intellij.openapi.application.impl.NonBlockingReadActionTest
import com.intellij.openapi.application.impl.NonBlockingSuspendingReadActionTest
import com.intellij.openapi.application.impl.NonBlockingUndispatchedSuspendingReadActionTest
import com.intellij.openapi.application.impl.PlatformUtilitiesTest
import com.intellij.openapi.application.impl.ProgressRunnerTest
import com.intellij.openapi.application.impl.ReadWritePropagationTest
import com.intellij.openapi.application.impl.SuspendingReadAndWriteActionTest
import com.intellij.openapi.application.impl.SuspendingWriteActionTest
import com.intellij.openapi.application.impl.SwingThreadingTest
import com.intellij.openapi.application.impl.WriteIntentReadActionTest
import com.intellij.openapi.progress.BlockingContextTest
import com.intellij.openapi.progress.CancellableContextTest
import com.intellij.openapi.progress.ContextSwitchTest
import com.intellij.openapi.progress.CoroutineToIndicatorTest
import com.intellij.openapi.progress.ExistingThreadContextTest
import com.intellij.openapi.progress.IndicatorThreadContextTest
import com.intellij.openapi.progress.RunBlockingCancellableTest
import com.intellij.openapi.progress.RunWithModalProgressBlockingTest
import com.intellij.openapi.progress.WithModalProgressTest
import com.intellij.util.concurrency.CancellationPropagationTest
import com.intellij.util.concurrency.CurrentThreadCoroutineScopeTest
import com.intellij.util.concurrency.DocumentManagerPropagationTest
import com.intellij.util.concurrency.DumbServicePropagationTest
import com.intellij.util.concurrency.ImplicitBlockingContextTest
import com.intellij.util.concurrency.MergingUpdateQueuePropagationTest
import com.intellij.util.concurrency.ThreadContextPropagationTest
import org.junit.platform.suite.api.SelectClasses
import org.junit.platform.suite.api.Suite

@Suite
@SelectClasses(
  // base
  WithThreadLocalTest::class,
  ThreadContextTest::class,

  // general threading
  NonBlockingReadActionTest::class,
  ProgressRunnerTest::class,
  EdtCoroutineDispatcherTest::class,
  ImplicitReadTest::class,
  LaterInvocatorTest::class,
  ModalCoroutineTest::class,
  ReadWritePropagationTest::class,
  SwingThreadingTest::class,

  // contexts
  ContextSwitchTest::class,
  BlockingContextTest::class,
  ExistingThreadContextTest::class,
  IndicatorThreadContextTest::class,
  RunBlockingCancellableTest::class,
  RunWithModalProgressBlockingTest::class,
  WithModalProgressTest::class,
  CoroutineToIndicatorTest::class,
  CurrentThreadCoroutineScopeTest::class,
  ImplicitBlockingContextTest::class,
  CancellableContextTest::class,

  // rw
  CancellableReadActionWithJobTest::class,
  CancellableReadActionWithIndicatorTest::class,
  BlockingSuspendingReadActionTest::class,
  NonBlockingSuspendingReadActionTest::class,
  NonBlockingUndispatchedSuspendingReadActionTest::class,
  SuspendingWriteActionTest::class,
  SuspendingReadAndWriteActionTest::class,
  BackgroundWriteActionTest::class,
  LockDowngradingTest::class,
  PlatformUtilitiesTest::class,
  WriteIntentReadActionTest::class,
  NonBlockingFlushQueueTest::class,

  // propagation
  ThreadContextPropagationTest::class,
  CancellationPropagationTest::class,
  MergingUpdateQueuePropagationTest::class,
  DocumentManagerPropagationTest::class,
  DumbServicePropagationTest::class,
)
class ContextAndCoroutinesSuite
