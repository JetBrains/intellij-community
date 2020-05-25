package org.jetbrains.plugins.feature.suggester

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectLifecycleListener
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import org.jetbrains.plugins.feature.suggester.cache.UserActionsCache
import org.jetbrains.plugins.feature.suggester.cache.UserAnActionsCache

class FeatureSuggestersManager(val project: Project) : ProjectLifecycleListener {

    private val MAX_ACTIONS_NUMBER: Int = 100
    private val actionsCache = UserActionsCache(MAX_ACTIONS_NUMBER)
    private val anActionsCache = UserAnActionsCache(MAX_ACTIONS_NUMBER)


    // TODO set PSI listeners in another phase of project lifecycle
    override fun beforeProjectLoaded(project: Project) {
        if (this.project.locationHash != project.locationHash) {
            return
        }

    }
}