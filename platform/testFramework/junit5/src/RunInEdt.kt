// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5

import com.intellij.testFramework.junit5.impl.EdtInterceptorExtension
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtendWith
import java.lang.annotation.Inherited

/**
 * Instructs the framework to run methods in EDT.
 * If [allMethods] is set to `true` (default), then all test class methods will be run in EDT, including lifecycle methods.
 * If [allMethods] is set to `false`, then methods annotated with [RunMethodInEdt] will be run in EDT.
 * If [writeIntent] is set to `true`, then all test methods will be run with Write Intent Lock by default.
 * If [writeIntent] is set to `false` (default), then all test methods will be run without Write Intent Lock by default.
 */
@TestOnly
@Target(AnnotationTarget.CLASS)
@Inherited
@ExtendWith(EdtInterceptorExtension::class)
annotation class RunInEdt(val allMethods: Boolean = true, val writeIntent: Boolean = false)
