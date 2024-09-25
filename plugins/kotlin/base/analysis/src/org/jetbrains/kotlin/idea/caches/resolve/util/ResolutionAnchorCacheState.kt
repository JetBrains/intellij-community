// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.caches.resolve.util

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.annotations.TestOnly

@State(name = "KotlinIdeAnchorService", storages = [Storage("anchors.xml")])
@Service(Service.Level.PROJECT)
class ResolutionAnchorCacheState : PersistentStateComponent<ResolutionAnchorCacheState.State>  {
    data class State(
        val moduleNameToAnchorName: Map<String, String> = emptyMap()
    )

    @JvmField
    @Volatile
    var myState: State = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }


    @TestOnly
    fun setAnchors(mapping: Map<String, String>) {
        myState = State(mapping)
    }

    companion object {
        fun getInstance(project: Project): ResolutionAnchorCacheState = project.service()
    }
}