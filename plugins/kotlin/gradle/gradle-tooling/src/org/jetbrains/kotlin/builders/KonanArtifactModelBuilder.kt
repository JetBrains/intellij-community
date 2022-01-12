// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.builders

import org.jetbrains.kotlin.gradle.KonanArtifactModel
import org.jetbrains.kotlin.gradle.KonanArtifactModelImpl
import org.jetbrains.kotlin.gradle.KonanRunConfigurationModelImpl
import org.jetbrains.kotlin.gradle.MultiplatformModelImportingContext
import org.jetbrains.kotlin.reflect.KonanArtifactReflection

object KonanArtifactModelBuilder : KotlinMultiplatformComponentBuilderBase<KonanArtifactModel> {
    override fun buildComponent(origin: Any, importingContext: MultiplatformModelImportingContext): KonanArtifactModel? {
        val konanArtifactReflection = KonanArtifactReflection(origin)
        val executableName = konanArtifactReflection.executableName ?: ""
        val outputKind = konanArtifactReflection.outputKindType ?: return null
        val konanTargetName = konanArtifactReflection.konanTargetName ?: error("No arch target found")
        val outputFile = konanArtifactReflection.outputFile ?: return null
        val compilationTargetName = konanArtifactReflection.compilationTargetName ?: return null
        val isTests = konanArtifactReflection.isTests ?: return null
        val freeCompilerArgs = konanArtifactReflection.freeCompilerArgs.orEmpty().toTypedArray()
        val linkTaskPath = konanArtifactReflection.linkTaskPath ?: return null
        val runConfiguration = KonanRunConfigurationModelImpl(konanArtifactReflection.runTask)
        val exportDependencies = KonanArtifactDependenciesBuilder.buildComponent(origin, importingContext)
            .map { importingContext.dependencyMapper.getId(it) }.distinct().toTypedArray()

        return KonanArtifactModelImpl(
            compilationTargetName,
            executableName,
            outputKind,
            konanTargetName,
            outputFile,
            linkTaskPath,
            runConfiguration,
            isTests,
            freeCompilerArgs,
            exportDependencies
        )
    }

    private object KonanArtifactDependenciesBuilder : KotlinMultiplatformDependenciesBuilder() {
        override val configurationNameAccessor: String = "getExportConfigurationName"
        override val scope: String = "COMPILE"
    }
}