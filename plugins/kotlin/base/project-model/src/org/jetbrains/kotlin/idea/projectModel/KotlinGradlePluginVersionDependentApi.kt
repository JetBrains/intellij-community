// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.projectModel

@RequiresOptIn(
    message = "This API behaves different depending on the imported Kotlin Gradle Plugin's version",
    level = RequiresOptIn.Level.WARNING
)
annotation class KotlinGradlePluginVersionDependentApi(val message: String = "")
