// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.configuration.state

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "DontShowAgainKotlinAdditionPluginSuggestionService",
    reloadable = true,
    storages = [Storage("dontShowAgainKotlinAdditionPluginSuggestionService.xml")]
)
class DontShowAgainKotlinAdditionPluginSuggestionService: PersistentStateComponent<DontShowAgainKotlinAdditionPluginSuggestionState> {

    companion object {
        @JvmStatic
        fun getInstance(): DontShowAgainKotlinAdditionPluginSuggestionService {
            return ApplicationManager.getApplication().getService(DontShowAgainKotlinAdditionPluginSuggestionService::class.java)
        }
    }

    private var state: DontShowAgainKotlinAdditionPluginSuggestionState = DontShowAgainKotlinAdditionPluginSuggestionState()

    override fun getState(): DontShowAgainKotlinAdditionPluginSuggestionState {
        return state
    }

    override fun loadState(state: DontShowAgainKotlinAdditionPluginSuggestionState) {
        this.state = state
    }
}
