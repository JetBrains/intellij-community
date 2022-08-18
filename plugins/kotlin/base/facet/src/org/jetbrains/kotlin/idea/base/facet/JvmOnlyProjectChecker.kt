// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.facet

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager.checkCanceled
import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.util.caching.SynchronizedFineGrainedEntityCache
import org.jetbrains.kotlin.idea.base.util.caching.ModuleEntityChangeListener
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.platform.jvm.isJvm

@Service(Service.Level.PROJECT)
class JvmOnlyProjectChecker(project: Project) : SynchronizedFineGrainedEntityCache<Unit, Boolean>(project, cleanOnLowMemory = false) {
    override fun subscribe() {
        val busConnection = project.messageBus.connect(this)
        WorkspaceModelTopics.getInstance(project).subscribeImmediately(busConnection, ModelChangeListener(project))
    }

    override fun checkKeyValidity(key: Unit) {}

    override fun calculate(key: Unit): Boolean {
        return runReadAction { ModuleManager.getInstance(project).modules }.all { module ->
            checkCanceled()
            module.platform.isJvm()
        }
    }

    internal class ModelChangeListener(project: Project) : ModuleEntityChangeListener(project) {
        override fun entitiesChanged(outdated: List<Module>) = getInstance(project).invalidate()
    }

    companion object {
        fun getInstance(project: Project): JvmOnlyProjectChecker = project.service()
    }
}