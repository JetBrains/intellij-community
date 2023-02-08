// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.facet

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager.checkCanceled
import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.util.caching.ModuleEntityChangeListener
import org.jetbrains.kotlin.idea.base.util.caching.SynchronizedFineGrainedValueCache
import org.jetbrains.kotlin.platform.jvm.isJvm

@Service(Service.Level.PROJECT)
class JvmOnlyProjectChecker(project: Project) : SynchronizedFineGrainedValueCache<Boolean>(project) {
    override fun subscribe() {
        project.messageBus.connect(this).subscribe(WorkspaceModelTopics.CHANGED, ModelChangeListener(project))
    }

    override fun calculate(): Boolean = runReadAction {
        ModuleManager.getInstance(project).modules.all { module ->
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