// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.script.configuration.utils

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * This cache is used by [org.jetbrains.kotlin.idea.core.script.configuration.DefaultScriptingSupport] only.
 * @see org.jetbrains.kotlin.idea.core.script.configuration.DefaultScriptingSupport.collectConfigurations
 *
 */
@Service(Service.Level.PROJECT)
@State(
    name = "ScriptClassRootsStorage",
    storages = [Storage(StoragePathMacros.CACHE_FILE)]
)
class ScriptClassRootsStorage : PersistentStateComponent<ScriptClassRootsStorage> {
    var classpath: Set<String> = hashSetOf()
    var sources: Set<String> = hashSetOf()
    var sdks: Set<String> = hashSetOf()
    var defaultSdkUsed: Boolean = false

    override fun getState(): ScriptClassRootsStorage = this

    override fun loadState(state: ScriptClassRootsStorage) {
        XmlSerializerUtil.copyBean(state, this)
    }

    fun clear() {
        classpath = hashSetOf()
        sources = hashSetOf()
        sdks = hashSetOf()
        defaultSdkUsed = false
    }

    companion object {
        fun getInstance(project: Project): ScriptClassRootsStorage = project.service()
    }
}
