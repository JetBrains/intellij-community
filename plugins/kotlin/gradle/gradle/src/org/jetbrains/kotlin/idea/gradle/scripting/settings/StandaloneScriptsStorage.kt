// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradle.scripting.settings

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

    fun getScripts(): List<String> = files.toList()

    override fun getState(): StandaloneScriptsStorage = this

    override fun loadState(state: StandaloneScriptsStorage) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(project: Project): StandaloneScriptsStorage? = project.serviceOrNull()
    }
}