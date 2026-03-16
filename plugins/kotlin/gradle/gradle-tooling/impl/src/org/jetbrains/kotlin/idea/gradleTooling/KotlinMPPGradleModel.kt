// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleTooling

import org.jetbrains.kotlin.idea.projectModel.ExtraFeatures
import org.jetbrains.kotlin.idea.projectModel.KotlinDependencyId
import org.jetbrains.kotlin.idea.projectModel.KotlinGradlePluginVersionDependentApi
import org.jetbrains.kotlin.idea.projectModel.KotlinSourceSet
import org.jetbrains.kotlin.idea.projectModel.KotlinTarget
import org.jetbrains.kotlin.idea.projectModel.KotlinSwiftExportModel
import java.io.Serializable

interface KotlinMPPGradleModel : KotlinSourceSetContainer, Serializable {
    val dependencyMap: Map<KotlinDependencyId, KotlinDependency>
    val targets: Collection<KotlinTarget>
    val extraFeatures: ExtraFeatures
    val kotlinNativeHome: String

    @Deprecated("Use 'sourceSetsByName' instead", ReplaceWith("sourceSetsByName"), DeprecationLevel.ERROR)
    val sourceSets: Map<String, KotlinSourceSet>
        get() = sourceSetsByName

    override val sourceSetsByName: Map<String, KotlinSourceSet>

    @KotlinGradlePluginVersionDependentApi
    val dependencies: IdeaKotlinDependenciesContainer?

    val kotlinImportingDiagnostics: KotlinImportingDiagnosticsContainer
    val kotlinGradlePluginVersion: KotlinGradlePluginVersion?

    /**
     * Swift Export configuration if enabled in the project, or null if not configured.
     *
     * This field is populated by [KotlinMPPGradleModelBuilder.buildSwiftExportModel] during Gradle sync.
     *
     * ## KGP Reference
     * `org.jetbrains.kotlin.gradle.plugin.mpp.apple.swiftexport.SwiftExportExtension`
     *
     * @see KotlinSwiftExportModel
     * @see org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinSwiftExportReflection
     */
    val swiftExport: KotlinSwiftExportModel?

    companion object {
        const val NO_KOTLIN_NATIVE_HOME = ""
    }
}
