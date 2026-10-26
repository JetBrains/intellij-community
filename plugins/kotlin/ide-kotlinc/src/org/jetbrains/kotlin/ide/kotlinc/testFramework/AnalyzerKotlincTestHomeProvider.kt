// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.ide.kotlinc.testFramework

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/** Gives the kotlinc home in unit-test mode. The implementation is in the test framework, so a [java.util.ServiceLoader] loads it. */
@ApiStatus.Internal
interface AnalyzerKotlincTestHomeProvider {
    fun kotlincHome(): Path
}
