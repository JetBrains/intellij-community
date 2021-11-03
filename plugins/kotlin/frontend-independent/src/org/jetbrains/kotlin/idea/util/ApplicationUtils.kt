// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.util.application

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.CancellationCheck
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.psiUtil.getElementTextWithContext
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments

fun <T> runReadAction(action: () -> T): T {
    return ApplicationManager.getApplication().runReadAction<T>(action)
}

fun <T> runWriteAction(action: () -> T): T {
    return ApplicationManager.getApplication().runWriteAction<T>(action)
}

/**
 * Run under the write action if the supplied element is physical; run normally otherwise.
 *
 * @param e context element
 * @param action action to execute
 * @return result of action execution
 */
fun <T> runWriteActionIfPhysical(e: PsiElement, action: () -> T): T {
    if (e.isPhysical) {
        return ApplicationManager.getApplication().runWriteAction<T>(action)
    }
    return action()
}

fun <T> runWriteActionInEdt(action: () -> T): T {
    return if (isDispatchThread()) {
        runWriteAction(action)
    } else {
        var result: T? = null
        ApplicationManager.getApplication().invokeLater {
            result = runWriteAction(action)
        }
        result!!
    }
}


fun Project.executeWriteCommand(@NlsContexts.Command name: String, command: () -> Unit) {
    CommandProcessor.getInstance().executeCommand(this, { runWriteAction(command) }, name, null)
}

fun <T> Project.executeWriteCommand(@NlsContexts.Command name: String, groupId: Any? = null, command: () -> T): T {
    return executeCommand<T>(name, groupId) { runWriteAction(command) }
}

fun <T> Project.executeCommand(@NlsContexts.Command name: String, groupId: Any? = null, command: () -> T): T {
    @Suppress("UNCHECKED_CAST") var result: T = null as T
    CommandProcessor.getInstance().executeCommand(this, { result = command() }, name, groupId)
    @Suppress("USELESS_CAST")
    return result as T
}

fun <T> runWithCancellationCheck(block: () -> T): T = CancellationCheck.runWithCancellationCheck(block)

inline fun executeOnPooledThread(crossinline action: () -> Unit) =
    ApplicationManager.getApplication().executeOnPooledThread { action() }

inline fun invokeLater(crossinline action: () -> Unit) =
    ApplicationManager.getApplication().invokeLater { action() }

inline fun invokeLater(expired: Condition<*>, crossinline action: () -> Unit) =
    ApplicationManager.getApplication().invokeLater({ action() }, expired)

inline fun isUnitTestMode(): Boolean = ApplicationManager.getApplication().isUnitTestMode

inline fun isDispatchThread(): Boolean = ApplicationManager.getApplication().isDispatchThread

inline fun isApplicationInternalMode(): Boolean = ApplicationManager.getApplication().isInternal

inline fun <reified T : Any> ComponentManager.getService(): T? = this.getService(T::class.java)

inline fun <reified T : Any> ComponentManager.getServiceSafe(): T =
    this.getService(T::class.java) ?: error("Unable to locate service ${T::class.java.name}")

fun <T> executeInBackgroundWithProgress(project: Project? = null, @NlsContexts.ProgressTitle title: String, block: () -> T): T {
    assert(!ApplicationManager.getApplication().isWriteAccessAllowed) {
        "Rescheduling computation into the background is impossible under the write lock"
    }
    return ProgressManager.getInstance().runProcessWithProgressSynchronously(
        ThrowableComputable { block() }, title, true, project
    )
}

fun KotlinExceptionWithAttachments.withPsiAttachment(name: String, element: PsiElement?): KotlinExceptionWithAttachments {
    kotlin.runCatching { element?.getElementTextWithContext() }.getOrNull()?.let { withAttachment(name, it) }
    return this
}