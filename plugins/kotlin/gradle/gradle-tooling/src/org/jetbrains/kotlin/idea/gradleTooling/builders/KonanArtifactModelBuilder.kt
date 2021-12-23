// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.builders

import org.jetbrains.kotlin.idea.gradleTooling.KonanArtifactModelImpl
import org.jetbrains.kotlin.idea.gradleTooling.KonanRunConfigurationModelImpl
import org.jetbrains.kotlin.idea.gradleTooling.MultiplatformModelImportingContext
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KonanArtifactReflection
import org.jetbrains.kotlin.idea.projectModel.KonanArtifactModel

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