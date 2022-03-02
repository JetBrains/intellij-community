// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("DEPRECATION")

package org.jetbrains.kotlin.idea.gradle.configuration

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.serialization.PropertyMapping
import org.jetbrains.kotlin.idea.gradleTooling.ArgsInfo
import org.jetbrains.kotlin.idea.gradleTooling.KotlinImportingDiagnostic
import org.jetbrains.kotlin.idea.gradleTooling.arguments.CachedExtractedArgsInfo
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.Serializable

interface ImplementedModulesAware : Serializable {
    var implementedModuleNames: List<String>
}

class KotlinGradleProjectData : AbstractExternalEntityData(GradleConstants.SYSTEM_ID), ImplementedModulesAware {
    var isResolved: Boolean = false
    var kotlinTarget: String? = null
    var hasKotlinPlugin: Boolean = false
    var coroutines: String? = null
    var isHmpp: Boolean = false
    var kotlinImportingDiagnosticsContainer: Set<KotlinImportingDiagnostic>? = null
    var platformPluginId: String? = null
    lateinit var kotlinNativeHome: String
    override var implementedModuleNames: List<String> = emptyList()

    @Transient
    val dependenciesCache: MutableMap<DataNode<ProjectData>, Collection<DataNode<out ModuleData>>> = mutableMapOf()
    val pureKotlinSourceFolders: MutableCollection<String> = hashSetOf()

    companion object {
        val KEY = Key.create(KotlinGradleProjectData::class.java, ProjectKeys.MODULE.processingWeight + 1)
    }
}

@IntellijInternalApi
val DataNode<KotlinGradleProjectData>.kotlinGradleSourceSetDataNodes: Collection<DataNode<KotlinGradleSourceSetData>>
    get() = ExternalSystemApiUtil.findAll(this, KotlinGradleSourceSetData.KEY)

class KotlinGradleSourceSetData @PropertyMapping("sourceSetName") constructor(val sourceSetName: String?) :
    AbstractExternalEntityData(GradleConstants.SYSTEM_ID), ImplementedModulesAware {

    lateinit var cachedArgsInfo: CachedExtractedArgsInfo

    var isProcessed: Boolean = false
    var kotlinPluginVersion: String? = null

    @Suppress("DEPRECATION")
    @Deprecated("Use cachedArgsInfo instead", level = DeprecationLevel.ERROR)
    lateinit var compilerArguments: ArgsInfo
    lateinit var additionalVisibleSourceSets: Set<String>
    override var implementedModuleNames: List<String> = emptyList()

    companion object {
        val KEY = Key.create(KotlinGradleSourceSetData::class.java, KotlinGradleProjectData.KEY.processingWeight + 1)
    }
}

