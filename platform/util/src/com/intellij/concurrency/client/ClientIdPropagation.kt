// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("ClientIdPropagation")

package com.intellij.concurrency.client

import com.intellij.util.Processor
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.Callable
import java.util.function.BiConsumer
import java.util.function.Function
// TODO: cleanup code on the call site and remove this file
@ApiStatus.Internal
fun captureClientIdInRunnable(runnable: Runnable): Runnable = runnable

@ApiStatus.Internal
fun <T> captureClientIdInCallable(callable: Callable<T>): Callable<T> = callable

@ApiStatus.Internal
fun <T> captureClientIdInProcessor(processor: Processor<T>): Processor<T> = processor

@ApiStatus.Internal
fun <T> captureClientId(action: () -> T): () -> T = action

@ApiStatus.Internal
fun <T, R> captureClientIdInFunction(function: Function<T, R>): Function<T, R> = function

@ApiStatus.Internal
fun <T, U> captureClientIdInBiConsumer(biConsumer: BiConsumer<T, U>): BiConsumer<T, U> = biConsumer
