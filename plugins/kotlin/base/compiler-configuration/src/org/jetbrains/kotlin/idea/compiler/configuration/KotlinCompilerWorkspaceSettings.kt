// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.PROJECT)
@State(name = "KotlinCompilerWorkspaceSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class KotlinCompilerWorkspaceSettings : PersistentStateComponent<KotlinCompilerWorkspaceSettings> {
    /**
     * incrementalCompilationForJvmEnabled
     * (name `preciseIncrementalEnabled` is kept for workspace file compatibility)
     */
    var preciseIncrementalEnabled: Boolean = true
    var incrementalCompilationForJsEnabled: Boolean = true
    var enableDaemon: Boolean = true

    override fun getState(): KotlinCompilerWorkspaceSettings {
        return this
    }

    override fun loadState(state: KotlinCompilerWorkspaceSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): KotlinCompilerWorkspaceSettings = project.service()
    }
}
