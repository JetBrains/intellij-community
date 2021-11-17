// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.builders

import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Exec
import org.jetbrains.kotlin.idea.gradleTooling.KonanArtifactModelImpl
import org.jetbrains.kotlin.idea.gradleTooling.KonanRunConfigurationModelImpl
import org.jetbrains.kotlin.idea.gradleTooling.MultiplatformModelImportingContext
import org.jetbrains.kotlin.idea.gradleTooling.get
import org.jetbrains.kotlin.idea.projectModel.KonanArtifactModel
import org.jetbrains.kotlin.idea.projectModel.KonanRunConfigurationModel
import org.jetbrains.kotlin.idea.projectModel.KotlinDependencyId
import java.io.File

object KonanArtifactModelBuilder : KotlinMultiplatformComponentBuilder<KonanArtifactModel> {
    override fun buildComponent(origin: Any, importingContext: MultiplatformModelImportingContext): KonanArtifactModel? {
        val executableName = origin["getBaseName"] as? String ?: ""
        val linkTask = origin["getLinkTask"] as? Task ?: return null
        val runConfiguration = KonanRunConfigurationModelImpl(origin["getRunTask"] as? Exec)
        val exportDependencies = KonanArtifactDependenciesBuilder.buildComponent(origin, importingContext)
        return buildArtifact(
            executableName,
            linkTask,
            runConfiguration,
            exportDependencies.map { importingContext.dependencyMapper.getId(it) }.distinct().toTypedArray()
        )
    }

    private object KonanArtifactDependenciesBuilder : KotlinMultiplatformDependenciesBuilder() {
        override val configurationNameAccessor: String = "getExportConfigurationName"
        override val scope: String = "COMPILE"
    }

    private fun buildArtifact(
        executableName: String,
        linkTask: Task,
        runConfiguration: KonanRunConfigurationModel,
        exportDependencies: Array<KotlinDependencyId>
    ): KonanArtifactModel? {
        val outputKind = linkTask["getOutputKind"]["name"] as? String ?: return null
        val konanTargetName = linkTask["getTarget"] as? String ?: error("No arch target found")
        val outputFile = (linkTask["getOutputFile"] as? Provider<*>)?.orNull as? File ?: return null
        val compilation = if (linkTask["getCompilation"] is Provider<*>)
            (linkTask["getCompilation"] as Provider<*>).get()
        else
            linkTask["getCompilation"]
        val compilationTarget = compilation["getTarget"]
        val compilationTargetName = compilationTarget["getName"] as? String ?: return null
        val isTests = linkTask["getProcessTests"] as? Boolean ?: return null

        @Suppress("UNCHECKED_CAST")
        val freeCompilerArgs = (linkTask["getKotlinOptions"]["getFreeCompilerArgs"] as? List<String>).orEmpty().toTypedArray()

        return KonanArtifactModelImpl(
            compilationTargetName,
            executableName,
            outputKind,
            konanTargetName,
            outputFile,
            linkTask.path,
            runConfiguration,
            isTests,
            freeCompilerArgs,
            exportDependencies
        )
    }
}