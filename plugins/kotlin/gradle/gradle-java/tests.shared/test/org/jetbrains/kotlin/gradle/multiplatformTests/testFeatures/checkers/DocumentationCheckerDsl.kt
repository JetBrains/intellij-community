// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers

import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.writeAccess

interface DocumentationCheckerDsl {
    val TestConfigurationDslScope.configuration
        get() = writeAccess.getConfiguration(DocumentationChecker)

    var TestConfigurationDslScope.downloadSources: Boolean
        get() = configuration.downloadSources
        set(value) {
            configuration.downloadSources = value
        }
}