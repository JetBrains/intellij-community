// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.util

import com.intellij.concurrency.IntelliJContextElement
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import org.jetbrains.kotlin.idea.util.application.isDispatchThread
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

@Deprecated("Use Dispatchers.EDT instead", replaceWith = ReplaceWith("Dispatchers.EDT"))
object EDT : CoroutineDispatcher() {
    override fun isDispatchNeeded(context: CoroutineContext): Boolean = !isDispatchThread()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val modalityState = context[ModalityStateElement.Key]?.modalityState ?: ModalityState.defaultModalityState()
        ApplicationManager.getApplication().invokeLater(block, modalityState)
    }

    class ModalityStateElement(val modalityState: ModalityState) : AbstractCoroutineContextElement(Key), IntelliJContextElement {

        override fun produceChildElement(parentContext: CoroutineContext, isStructured: Boolean): IntelliJContextElement = this

        companion object Key : CoroutineContext.Key<ModalityStateElement>
    }

    operator fun invoke(project: Project) = this + project.cancelOnDisposal
}

// job that is cancelled when the project is disposed
val Project.cancelOnDisposal: Job
    get() = this.service<ProjectJob>().sharedJob

@Service(Service.Level.PROJECT)
internal class ProjectJob(project: Project) : Disposable {
    internal val sharedJob: Job = Job()

    override fun dispose() {
        sharedJob.cancel()
    }
}
