// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic;

/**
 * A marker interface for exceptions that should never be logged.
 */
@SuppressWarnings("NonExceptionNameEndsWithException")
public interface ControlFlowException { }