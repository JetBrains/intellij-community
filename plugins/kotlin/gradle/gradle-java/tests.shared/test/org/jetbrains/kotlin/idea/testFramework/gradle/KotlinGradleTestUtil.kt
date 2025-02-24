// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.testFramework.gradle

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.scripting.shared.minimal_gradle_version_supported
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatGradleIsAtLeast

internal fun assumeThatKotlinDslScriptsModelImportIsSupported(gradleVersion: GradleVersion) {
    assumeThatGradleIsAtLeast(gradleVersion, minimal_gradle_version_supported) {
        "Gradle ${gradleVersion.version} doesn't support Kotlin DSL Scripts Model import."
    }
}