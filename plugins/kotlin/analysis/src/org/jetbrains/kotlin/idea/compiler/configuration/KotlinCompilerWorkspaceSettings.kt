// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.kotlin.idea.util.application.getServiceSafe

@State(
    name = "KotlinCompilerWorkspaceSettings",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
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
        fun getInstance(project: Project): KotlinCompilerWorkspaceSettings = project.getServiceSafe()
    }
}
