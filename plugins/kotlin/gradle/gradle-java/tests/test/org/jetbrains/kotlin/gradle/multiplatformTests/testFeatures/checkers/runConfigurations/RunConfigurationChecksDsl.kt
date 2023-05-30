// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.runConfigurations

import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.writeAccess

interface RunConfigurationChecksDsl {
    fun TestConfigurationDslScope.executeRunConfiguration(functionFqName: String) {
        writeAccess.getConfiguration(ExecuteRunConfigurationsChecker).functionFqNames += functionFqName
    }
}