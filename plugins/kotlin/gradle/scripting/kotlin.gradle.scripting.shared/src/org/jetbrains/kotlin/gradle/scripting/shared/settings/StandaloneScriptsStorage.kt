// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting.shared.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.PROJECT)
@State(
    name = "StandaloneScriptsStorage",
    storages = [
        Storage(StoragePathMacros.CACHE_FILE, deprecated = true),
        Storage(StoragePathMacros.WORKSPACE_FILE)
    ]
)
class StandaloneScriptsStorage : PersistentStateComponent<StandaloneScriptsStorage> {
    var files: MutableSet<String> = hashSetOf()

    override fun getState(): StandaloneScriptsStorage = this

    override fun loadState(state: StandaloneScriptsStorage) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(project: Project): StandaloneScriptsStorage? = project.serviceOrNull()
    }
}