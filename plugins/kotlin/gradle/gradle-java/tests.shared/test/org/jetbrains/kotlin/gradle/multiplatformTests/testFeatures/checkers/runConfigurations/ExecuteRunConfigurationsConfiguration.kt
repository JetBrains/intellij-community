// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.runConfigurations

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class ExecuteRunConfigurationsConfiguration {
    /**
     * Fully qualified name of functions of which to execute the associated run gutters (run configurations)
     */
    val functionFqNames = mutableSetOf<String>()

    var executionTimeout: Duration = 2.minutes
}