// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.externalSystem

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

/**
 * Intended to provide build system specific information about a given project, which is not relevant
 * during regular 'import' and therefore is not present in the project structure.
 *
 * For example, The Gradle implementation might reach into external system 'DataNodes'
 */
@IntellijInternalApi
interface KotlinBuildSystemFacade {
    fun findSourceSet(module: Module): KotlinBuildSystemSourceSet?

    /**
     * See [Module.kotlinToolingVersion]
     */
    fun getKotlinToolingVersion(module: Module): KotlinToolingVersion?

    companion object {

        @JvmStatic
        fun getInstance(): KotlinBuildSystemFacade = KotlinBuildSystemCompositeFacade(EP_NAME.extensionList)

        val EP_NAME = ExtensionPointName.create<KotlinBuildSystemFacade>(
            "org.jetbrains.kotlin.idea.base.externalSystem.kotlinBuildSystemFacade"
        )
    }
}

/**
 * Returns the Kotlin Tooling Version as imported by an external system (such as Gradle)
 *
 * ## e.g., Gradle
 * ```kotlin
 * //build.gradle.kts
 *
 * plugins {
 *     kotlin("jvm") version "2.0.20-Beta01"
 * }
 * ```
 *
 * Will return "2.0.20-Beta01" as
 * - major: 2
 * - minor: 0
 * - patch: 20
 * - classifier: "Beta01"
 * - toString: "2.0.20-Beta01"
 *
 * See [kotlinToolingVersion].
 *
 *
 * Example Usage
 * ```kotlin
 * fun example(module: Module) {
 *     val version = module.kotlinToolingVersion ?: return
 *     if(version >= "1.9.20-Beta01") {
 *         // Code
 *     }
 *
 *     if(version >= KotlinToolingVersion(1, 9, 20, "Beta01")) {
 *         // Code
 *     }
 * }
 * ```
 */
val Module.kotlinToolingVersion: KotlinToolingVersion?
    get() = KotlinBuildSystemFacade.getInstance().getKotlinToolingVersion(this)


private class KotlinBuildSystemCompositeFacade(
    private val instances: List<KotlinBuildSystemFacade>
) : KotlinBuildSystemFacade {
    override fun findSourceSet(module: Module): KotlinBuildSystemSourceSet? {
        return instances.firstNotNullOfOrNull { instance -> instance.findSourceSet(module) }
    }

    override fun getKotlinToolingVersion(module: Module): KotlinToolingVersion? {
        return instances.firstNotNullOfOrNull { instance -> instance.getKotlinToolingVersion(module) }
    }
}