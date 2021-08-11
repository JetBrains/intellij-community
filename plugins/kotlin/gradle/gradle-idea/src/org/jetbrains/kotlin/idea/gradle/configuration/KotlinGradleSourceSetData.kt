// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradle.configuration

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.jetbrains.kotlin.gradle.AdditionalVisibleSourceSetsBySourceSet
import org.jetbrains.kotlin.gradle.CompilerArgumentsBySourceSet
import org.jetbrains.plugins.gradle.util.GradleConstants

val DataNode<out ModuleData>.kotlinGradleSourceSetData: KotlinGradleSourceSetData
    get() = (
            ExternalSystemApiUtil.getChildren(this, KotlinGradleSourceSetData.KEY).firstOrNull()
                ?: DataNode(KotlinGradleSourceSetData.KEY, KotlinGradleSourceSetData(), this)
                    .also { addChild(it) }
            ).data

class KotlinGradleSourceSetData : AbstractExternalEntityData(GradleConstants.SYSTEM_ID) {
    var isResolved: Boolean = false
    var hasKotlinPlugin: Boolean = false
    lateinit var compilerArgumentsBySourceSet: CompilerArgumentsBySourceSet
    lateinit var additionalVisibleSourceSets: AdditionalVisibleSourceSetsBySourceSet
    var coroutines: String? = null
    var isHmpp: Boolean = false
    var platformPluginId: String? = null
    lateinit var kotlinNativeHome: String
    val implementedModuleNames: MutableCollection<String> = hashSetOf()
    val dependenciesCache: MutableMap<DataNode<ProjectData>, Collection<DataNode<out ModuleData>>> = mutableMapOf()
    val pureKotlinSourceFolders: MutableCollection<String> = hashSetOf()

    companion object {
        val KEY = Key.create(KotlinGradleSourceSetData::class.java, ProjectKeys.MODULE.processingWeight + 1)
    }
}


