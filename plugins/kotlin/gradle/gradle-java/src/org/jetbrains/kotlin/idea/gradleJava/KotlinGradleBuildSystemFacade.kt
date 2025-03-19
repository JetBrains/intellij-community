// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava

import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.base.externalSystem.KotlinBuildSystemFacade
import org.jetbrains.kotlin.idea.base.externalSystem.KotlinBuildSystemSourceSet
import org.jetbrains.kotlin.idea.gradle.configuration.kotlinSourceSetData
import org.jetbrains.kotlin.idea.gradleTooling.toKotlinToolingVersion
import org.jetbrains.kotlin.idea.projectModel.KotlinCompilation
import org.jetbrains.kotlin.idea.projectModel.KotlinSourceSet
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder

internal class KotlinGradleBuildSystemFacade : KotlinBuildSystemFacade {
    override fun findSourceSet(module: Module): KotlinBuildSystemSourceSet? {
        val kotlinSourceSetData = CachedModuleDataFinder.findModuleData(module)?.kotlinSourceSetData ?: return null

        val kotlinSourceSet = when (val component = kotlinSourceSetData.sourceSetInfo.kotlinComponent) {
            is KotlinCompilation -> component.declaredSourceSets.firstOrNull() ?: return null
            is KotlinSourceSet -> component
        }

        return KotlinBuildSystemSourceSet(
            name = kotlinSourceSet.name,
            sourceDirectories = kotlinSourceSet.sourceDirs.map { file -> file.toPath() }
        )
    }

    override fun getKotlinToolingVersion(module: Module): KotlinToolingVersion? {
        return module.kotlinGradlePluginVersion?.toKotlinToolingVersion()
    }
}