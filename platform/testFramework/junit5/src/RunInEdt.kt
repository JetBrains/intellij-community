// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5

import com.intellij.testFramework.junit5.impl.EdtInterceptorExtension
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtendWith
import java.lang.annotation.Inherited

/**
 * Legacy JUnit5 extension that forces methods to run on EDT.
 *
 * Prefer coroutine-based tests, e.g. `timeoutRunBlocking(context = Dispatchers.UiWithModelAccess)`
 * for model access.
 */
@TestOnly
@Target(AnnotationTarget.CLASS)
@Inherited
@ExtendWith(EdtInterceptorExtension::class)
@Deprecated("Do not use. Consider using [com.intellij.testFramework.common.timeoutRunBlocking] with a proper context if needed.",
            ReplaceWith("RunMethodInEdt"))
annotation class RunInEdt(val allMethods: Boolean = true, val writeIntent: Boolean = false)
