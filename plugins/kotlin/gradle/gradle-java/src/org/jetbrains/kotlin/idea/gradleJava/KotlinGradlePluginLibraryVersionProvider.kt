// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava

import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.configuration.KotlinLibraryVersionProvider

/**
 * Returns the modules 'Kotlin Gradle Plugin version' as the version for libraries
 * which originate from the Kotlin monorepo. Libraries (such as the kotlin stdlib, kotlin-test, ...) are expected
 * to have the same version as the Kotlin Gradle Plugin.
 */
class KotlinGradlePluginLibraryVersionProvider : KotlinLibraryVersionProvider {
    companion object {
        const val STDLIB_GROUP_ID = "org.jetbrains.kotlin"
    }

    override fun getVersion(module: Module, groupId: String, artifactId: String): String? {
        if (groupId != STDLIB_GROUP_ID) return null
        return module.kotlinGradlePluginVersion?.versionString
    }
}
