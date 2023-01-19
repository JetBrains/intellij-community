// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.projectModel

import java.io.File

interface KotlinSourceSet : KotlinComponent {
    val languageSettings: KotlinLanguageSettings
    val sourceDirs: Set<File>
    val resourceDirs: Set<File>
    val actualPlatforms: KotlinPlatformContainer

    /**
     * Special dependencies, that shall not be passed transitively to any dependee source sets
     */
    val intransitiveDependencies: Array<KotlinDependencyId>

    /**
     * Dependencies that can be forwarded transitively to any dependee source set
     */
    val regularDependencies: Array<KotlinDependencyId>

    /**
     * All dependencies ( [regularDependencies] + [intransitiveDependencies])
     */
    override val dependencies: Array<KotlinDependencyId>

    /**
     * All source sets that this source set explicitly declared a 'dependsOn' relation to
     */
    val declaredDependsOnSourceSets: Set<String>

    val allDependsOnSourceSets: Set<String>

    val additionalVisibleSourceSets: Set<String>

    @Deprecated(
        "Returns single target platform. Use actualPlatforms instead",
        level = DeprecationLevel.ERROR,
        replaceWith = ReplaceWith("actualPlatforms.platforms.singleOrNull() ?: KotlinPlatform.COMMON")
    )
    val platform: KotlinPlatform
        get() = actualPlatforms.platforms.singleOrNull() ?: KotlinPlatform.COMMON

    @KotlinGradlePluginVersionDependentApi("This field is only available for Kotlin Gradle Plugin 1.8 or higher")
    val androidSourceSetInfo: KotlinAndroidSourceSetInfo?

    companion object {
        const val COMMON_MAIN_SOURCE_SET_NAME = "commonMain"
        const val COMMON_TEST_SOURCE_SET_NAME = "commonTest"

        // Note. This method could not be deleted due to usage in KotlinAndroidGradleMPPModuleDataService from IDEA Core
        @Suppress("unused")
        fun commonName(forTests: Boolean) = if (forTests) COMMON_TEST_SOURCE_SET_NAME else COMMON_MAIN_SOURCE_SET_NAME
    }
}
