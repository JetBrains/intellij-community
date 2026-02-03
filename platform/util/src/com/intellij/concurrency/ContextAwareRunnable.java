// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency;

import org.jetbrains.annotations.ApiStatus;

/**
 * Represents a computation that handles thread contexts internally.
 * This class can be useful in the following cases:
 * <ul>
 *   <li>
 *     A runnable quickly returns control to the suspending environment
 *     (like {@link kotlin.coroutines.Continuation}), so there is no need for capturing and installing the thread context.
 *   </li>
 *   <li>
 *     A runnable represents a stack of wrappers over some {@link com.intellij.util.concurrency.ContextRunnable},
 *     so the presence of this class can help to avoid unnecessary capturing and pointless IDE assertions.
 *   </li>
 * </ul>
 *
 * {@link com.intellij.util.concurrency.ContextRunnable} is intentionally not marked as {@link ContextAwareRunnable}.
 * We would like to keep {@link com.intellij.util.concurrency.ContextRunnable} as an
 * implementation detail that should not be used by a client directly,
 * so the IDE assertions will point to double context capturing to prevent such cases.
 */
@ApiStatus.Internal
public interface ContextAwareRunnable extends Runnable {
}
