// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros.WORKSPACE_FILE
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.PROJECT)
@State(name = "KotlinCodeInsightWorkspaceSettings", storages = [Storage(WORKSPACE_FILE)])
class KotlinCodeInsightWorkspaceSettings : PersistentStateComponent<KotlinCodeInsightWorkspaceSettings> {
    @JvmField
    var optimizeImportsOnTheFly: Boolean = false

    override fun getState(): KotlinCodeInsightWorkspaceSettings = this

    override fun loadState(state: KotlinCodeInsightWorkspaceSettings): Unit = XmlSerializerUtil.copyBean(state, this)

    companion object {
        fun getInstance(project: Project): KotlinCodeInsightWorkspaceSettings = project.service()
    }
}

@Service(Service.Level.APP)
@State(name = "KotlinCodeInsightSettings", storages = [Storage("editor.codeinsight.xml")], category = SettingsCategory.CODE)
class KotlinCodeInsightSettings : PersistentStateComponent<KotlinCodeInsightSettings> {
    @JvmField
    var addUnambiguousImportsOnTheFly: Boolean = false

    override fun getState(): KotlinCodeInsightSettings = this

    override fun loadState(state: KotlinCodeInsightSettings): Unit = XmlSerializerUtil.copyBean(state, this)

    companion object {
        fun getInstance(): KotlinCodeInsightSettings = service()
    }
}