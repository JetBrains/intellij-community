// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.sources

import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.writeAccess

interface LibrarySourcesCheckDsl {
    var TestConfigurationDslScope.classifier: String?
        get() = configuration.classifier
        set(value) { configuration.classifier = value }
}

private val TestConfigurationDslScope.configuration
    get() = writeAccess.getConfiguration(LibrarySourcesChecker.Companion)
