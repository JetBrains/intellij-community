// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.contentRoots

import org.jetbrains.kotlin.gradle.newTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.newTests.writeAccess

interface ContentRootsChecksDsl {
    var TestConfigurationDslScope.hideTestSourceRoots: Boolean
        get() = config.hideTestSourceRoots
        set(value) { config.hideTestSourceRoots = value }

    var TestConfigurationDslScope.hideResourceRoots: Boolean
        get() = config.hideResourceRoots
        set(value) { config.hideResourceRoots = value }

    // Add more if necessary, see `PrinterRootType`
}

private val TestConfigurationDslScope.config: ContentRootsChecksConfiguration
    get() = writeAccess.getConfiguration(ContentRootsChecker)
