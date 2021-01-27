// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradle.configuration

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData
import com.intellij.openapi.externalSystem.model.project.AbstractNamedData
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.util.Key
import com.intellij.serialization.PropertyMapping
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.config.ExternalSystemRunTask
import org.jetbrains.kotlin.idea.gradleTooling.KotlinPlatformContainerImpl
import org.jetbrains.kotlin.idea.projectModel.KonanArtifactModel
import org.jetbrains.kotlin.idea.projectModel.KotlinModule
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatformContainer
import org.jetbrains.kotlin.idea.util.CopyableDataNodeUserDataProperty
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
import java.io.Serializable
import com.intellij.openapi.externalSystem.model.Key as ExternalKey

@Deprecated(
    "This UserData property is deprecated and will be removed soon",
    ReplaceWith("kotlinSourceSetData?.sourceSetInfo"),
    DeprecationLevel.ERROR
)
var DataNode<out ModuleData>.kotlinSourceSet: KotlinSourceSetInfo?
        by CopyableDataNodeUserDataProperty(Key.create("KOTLIN_SOURCE_SET"))

val DataNode<out ModuleData>.kotlinSourceSetData: KotlinSourceSetData?
    get() = ExternalSystemApiUtil.getChildren(this, KotlinSourceSetData.KEY).firstOrNull()?.data

var DataNode<out ModuleData>.kotlinImportingDiagnosticsContainer: KotlinImportingDiagnosticsContainer?
        by CopyableDataNodeUserDataProperty(Key.create("KOTLIN_IMPORTING_DIAGNOSTICS_CONTAINER"))

val DataNode<out ModuleData>.kotlinAndroidSourceSets: List<KotlinSourceSetInfo>?
    get() = ExternalSystemApiUtil.getChildren(this, KotlinAndroidSourceSetData.KEY).firstOrNull()?.data?.sourceSetInfos

class KotlinSourceSetInfo @PropertyMapping("kotlinModule") constructor(val kotlinModule: KotlinModule) : Serializable {
    var moduleId: String? = null
    var gradleModuleId: String = ""

    var actualPlatforms: KotlinPlatformContainer = KotlinPlatformContainerImpl()

    @Deprecated("Returns only single TargetPlatform", ReplaceWith("actualPlatforms.actualPlatforms"), DeprecationLevel.ERROR)
    val platform: KotlinPlatform
        get() = actualPlatforms.platforms.singleOrNull() ?: KotlinPlatform.COMMON

    @Transient
    @Deprecated("Use lazyDefaultCompilerArguments instead!", ReplaceWith("lazyDefaultCompilerArguments"), DeprecationLevel.ERROR)
    var defaultCompilerArguments: CommonCompilerArguments? = null

    @Transient
    var lazyDefaultCompilerArguments: Lazy<CommonCompilerArguments>? = null

    @Transient
    @Deprecated("Use lazyCompilerArguments instead!", ReplaceWith("lazyCompilerArguments"), DeprecationLevel.ERROR)
    var compilerArguments: CommonCompilerArguments? = null

    @Transient
    var lazyCompilerArguments: Lazy<CommonCompilerArguments>? = null

    @Transient
    @Deprecated("Use lazyDependencyClasspath instead!", ReplaceWith("lazyDependencyClasspath"), DeprecationLevel.ERROR)
    var dependencyClasspath: List<String> = emptyList()

    @Transient
    var lazyDependencyClasspath: Lazy<List<String>> = lazy { emptyList() }
    var isTestModule: Boolean = false
    var sourceSetIdsByName: MutableMap<String, String> = LinkedHashMap()
    var dependsOn: List<String> = emptyList()
    var additionalVisible: Set<String> = emptySet()
    var externalSystemRunTasks: Collection<ExternalSystemRunTask> = emptyList()
}

class KotlinSourceSetData @PropertyMapping("sourceSetInfo") constructor(val sourceSetInfo: KotlinSourceSetInfo) :
    AbstractExternalEntityData(GradleConstants.SYSTEM_ID) {
    companion object {
        val KEY = ExternalKey.create(KotlinSourceSetData::class.java, KotlinTargetData.KEY.processingWeight + 1)
    }
}

class KotlinAndroidSourceSetData @PropertyMapping("sourceSetInfos") constructor(
    val sourceSetInfos: List<KotlinSourceSetInfo>
) : AbstractExternalEntityData(GradleConstants.SYSTEM_ID) {
    companion object {
        val KEY = ExternalKey.create(KotlinAndroidSourceSetData::class.java, KotlinTargetData.KEY.processingWeight + 1)
    }
}

class KotlinTargetData @PropertyMapping("externalName") constructor(externalName: String) :
    AbstractNamedData(GradleConstants.SYSTEM_ID, externalName) {
    var moduleIds: Set<String> = emptySet()
    var archiveFile: File? = null
    var konanArtifacts: Collection<KonanArtifactModel>? = null

    companion object {
        val KEY = ExternalKey.create(KotlinTargetData::class.java, ProjectKeys.MODULE.processingWeight + 1)
    }
}

class KotlinOutputPathsData @PropertyMapping("paths") constructor(val paths: MultiMap<ExternalSystemSourceType, String>) :
    AbstractExternalEntityData(GradleConstants.SYSTEM_ID) {
    companion object {
        val KEY = ExternalKey.create(KotlinOutputPathsData::class.java, ProjectKeys.CONTENT_ROOT.processingWeight + 1)
    }
}
