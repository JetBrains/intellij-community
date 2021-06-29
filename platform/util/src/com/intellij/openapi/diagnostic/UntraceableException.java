// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diagnostic;

/**
 * A marker interface for exceptions that {@link Throwable#getStackTrace()} should never be invoked.
 */
@SuppressWarnings("NonExceptionNameEndsWithException")
public interface UntraceableException { }
