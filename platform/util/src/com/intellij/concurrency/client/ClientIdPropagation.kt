// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("ClientIdPropagation")

package com.intellij.concurrency.client

import com.intellij.util.Processor
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiConsumer
import java.util.function.Function

@Deprecated("ClientId propagation is handled by context propagation. You don't need to do it manually. The method will be removed soon.")
@ApiStatus.Internal
val captureClientIdInRunnableFun = AtomicReference<((Runnable) -> Runnable)>({ runnable -> runnable })

@Deprecated("ClientId propagation is handled by context propagation. You don't need to do it manually. The method will be removed soon.")
@ApiStatus.Internal
val captureClientIdInCallableFun = AtomicReference<((Callable<*>) -> Callable<*>)>({ callable -> callable })

@Deprecated("ClientId propagation is handled by context propagation. You don't need to do it manually. The method will be removed soon.")
@ApiStatus.Internal
val captureClientIdInFunctionFun = AtomicReference<((Function<*, *>) -> Function<*, *>)>({ function -> function })

@Deprecated("ClientId propagation is handled by context propagation. You don't need to do it manually. The method will be removed soon.")
@ApiStatus.Internal
val captureClientIdInBiConsumerFun = AtomicReference<((BiConsumer<*, *>) -> BiConsumer<*, *>)>({ biConsumer -> biConsumer })

// TODO: cleanup code on the call site and remove this file
@Deprecated("ClientId propagation is handled by context propagation. You don't need to do it manually. The method will be removed soon.")
@ApiStatus.Internal
fun captureClientIdInRunnable(runnable: Runnable): Runnable = captureClientIdInRunnableFun.get()(runnable)

@Deprecated("ClientId propagation is handled by context propagation. You don't need to do it manually. The method will be removed soon.")
@ApiStatus.Internal
fun <T> captureClientIdInCallable(callable: Callable<T>): Callable<T> = captureClientIdInCallableFun.get()(callable) as Callable<T>

@Deprecated("ClientId propagation is handled by context propagation. You don't need to do it manually. The method will be removed soon.")
@ApiStatus.Internal
fun <T> captureClientIdInProcessor(processor: Processor<T>): Processor<T> = processor

@Deprecated("ClientId propagation is handled by context propagation. You don't need to do it manually. The method will be removed soon.")
@ApiStatus.Internal
fun <T> captureClientId(action: () -> T): () -> T = action

@Deprecated("ClientId propagation is handled by context propagation. You don't need to do it manually. The method will be removed soon.")
@ApiStatus.Internal
fun <T, R> captureClientIdInFunction(function: Function<T, R>): Function<T, R> = captureClientIdInFunctionFun.get()(function) as Function<T, R>

@Deprecated("ClientId propagation is handled by context propagation. You don't need to do it manually. The method will be removed soon.")
@ApiStatus.Internal
fun <T, U> captureClientIdInBiConsumer(biConsumer: BiConsumer<T, U>): BiConsumer<T, U> = captureClientIdInBiConsumerFun.get()(biConsumer) as BiConsumer<T, U>
