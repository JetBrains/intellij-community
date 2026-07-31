// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5

import com.intellij.testFramework.junit5.impl.EdtInterceptorExtension
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtendWith
import java.lang.annotation.Inherited

/**
 * Legacy JUnit 5 extension that forces test and lifecycle methods to run on EDT.
 *
 * Prefer a bounded coroutine test with `timeoutRunBlocking`, keeping only Swing operations inside
 * `withContext(Dispatchers.UI)`. The strict UI dispatcher provides UI-thread affinity without
 * implicitly granting model access. Use `Dispatchers.EDT` only when an inseparable operation is
 * known to require IntelliJ model or lock access, and keep write actions explicit.
 *
 * Running the whole test on EDT slows the suite, moves assertions and lifecycle work onto the UI
 * thread, and can hide accidental model or write-intent access.
 */
@TestOnly
@Target(AnnotationTarget.CLASS)
@Inherited
@ExtendWith(EdtInterceptorExtension::class)
@Deprecated("Do not use. Consider using [com.intellij.testFramework.common.timeoutRunBlocking] with a proper context if needed.",
            ReplaceWith("RunMethodInEdt"))
annotation class RunInEdt(val allMethods: Boolean = true, val writeIntent: Boolean = false)
