package org.jetbrains.plugins.feature.suggester

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectLifecycleListener
import org.jetbrains.plugins.feature.suggester.changes.UserAction
import org.jetbrains.plugins.feature.suggester.changes.UserAnAction

class FeatureSuggestersManager(val project: Project) : ProjectLifecycleListener {

    private val actionsList: List<UserAction> = ArrayList()
    private val anActionList: List<UserAnAction> = ArrayList()
    private val MAX_ACTIONS_NUMBER: Int = 100

    override fun beforeProjectLoaded(project: Project) {
        if (this.project.locationHash != project.locationHash) {
            return
        }

    }
}