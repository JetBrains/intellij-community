// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava

import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.base.plugin.KotlinCompilerVersionProvider
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.gradleJava.configuration.kotlinGradleProjectDataNodeOrNull
import org.jetbrains.kotlin.idea.gradleTooling.KotlinGradlePluginVersion
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder

/**
 * Returns the Kotlin Gradle Plugin version reported directly by the Kotlin Gradle Sync.
 * e.g.
 * ```kotlin
 * //build.gradle.kts
 *
 * plugins {
 *     kotlin("jvm") version "2.0.20-Beta01"
 * }
 * ```
 * Will return "2.0.20-Beta01" as
 * major: 2
 * minor: 0
 * patch: 20
 * versionString: ""2.0.20-Beta01"
 *
 * See [KotlinGradlePluginVersion].
 *
 *
 * Example Usage
 * ```kotlin
 * fun example(module: Module) {
 *     val version = module.kotlinGradlePluginVersion ?: return
 *     if(version >= "1.9.20-Beta01") {
 *         // Code
 *     }
 *
 *     if(version >= KotlinToolingVersion(1, 9, 20, "Beta01")) {
 *         // Code
 *     }
 * }
 * ```
 *
 */
val Module.kotlinGradlePluginVersion: KotlinGradlePluginVersion?
    get() {
        val dataNode = CachedModuleDataFinder.findMainModuleData(this) ?: return null
        val projectDataNode = dataNode.kotlinGradleProjectDataNodeOrNull ?: return null
        return projectDataNode.data.kotlinGradlePluginVersion
    }

private class GradleKotlinCompilerVersionProvider : KotlinCompilerVersionProvider {
    override fun getKotlinCompilerVersion(module: Module): IdeKotlinVersion? {
        return module.kotlinGradlePluginVersion?.versionString?.let(IdeKotlinVersion::opt)
    }

    override fun isAvailable(module: Module): Boolean {
        return module.kotlinGradlePluginVersion != null
    }
}