package org.jetbrains.plugins.feature.suggester

import com.intellij.util.messages.Topic
import java.util.EventListener

interface FeatureSuggestersManagerListener : EventListener {

    companion object {
        val TOPIC = Topic(
            "FeatureSuggester events",
            FeatureSuggestersManagerListener::class.java,
            Topic.BroadcastDirection.TO_CHILDREN
        )
    }

    /**
     * This method is called after suggestion is shown
     */
    fun featureFound(suggestion: PopupSuggestion) {
    }
}
