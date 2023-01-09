// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.util.application

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.CancellationCheck
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@Deprecated("use com.intellij.openapi.application.runReadAction", ReplaceWith("com.intellij.openapi.application.runReadAction"))
fun <T> runReadAction(action: () -> T): T {
    return ApplicationManager.getApplication().runReadAction<T>(action)
}

@Deprecated("use com.intellij.openapi.application.runWriteAction", ReplaceWith("com.intellij.openapi.application.runWriteAction"))
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

@Deprecated("use com.intellij.openapi.application.invokeLater", ReplaceWith("com.intellij.openapi.application.invokeLater"))
inline fun invokeLater(crossinline action: () -> Unit) =
    ApplicationManager.getApplication().invokeLater { action() }

inline fun invokeLater(expired: Condition<*>, crossinline action: () -> Unit) =
    ApplicationManager.getApplication().invokeLater({ action() }, expired)

@Suppress("NOTHING_TO_INLINE")
inline fun isUnitTestMode(): Boolean = ApplicationManager.getApplication().isUnitTestMode

inline fun isHeadlessEnvironment(): Boolean = ApplicationManager.getApplication().isHeadlessEnvironment

@Suppress("NOTHING_TO_INLINE")
inline fun isDispatchThread(): Boolean = ApplicationManager.getApplication().isDispatchThread

@Suppress("NOTHING_TO_INLINE")
inline fun isApplicationInternalMode(): Boolean = ApplicationManager.getApplication().isInternal

fun <T> executeInBackgroundWithProgress(project: Project? = null, @NlsContexts.ProgressTitle title: String, block: () -> T): T {
    assert(!ApplicationManager.getApplication().isWriteAccessAllowed) {
        "Rescheduling computation into the background is impossible under the write lock"
    }
    return ProgressManager.getInstance().runProcessWithProgressSynchronously(
        ThrowableComputable { block() }, title, true, project
    )
}

inline fun <T> runAction(runImmediately: Boolean, crossinline action: () -> T): T {
    if (runImmediately) {
        return action()
    }

    var result: T? = null
    ApplicationManager.getApplication().invokeAndWait {
        CommandProcessor.getInstance().runUndoTransparentAction {
            result = ApplicationManager.getApplication().runWriteAction<T> { action() }
        }
    }
    return result!!
}

@ApiStatus.Internal
fun <T: Any> underModalProgressOrUnderWriteActionWithNonCancellableProgressInDispatchThread(
    project: Project,
    @Nls progressTitle: String,
    computable: () -> T
): T {
    return if (CommandProcessor.getInstance().currentCommandName != null) {
        lateinit var result: T
        val application = ApplicationManager.getApplication() as ApplicationEx
        application.runWriteActionWithNonCancellableProgressInDispatchThread(progressTitle, project, null) {
            result = computable()
        }
        result
    } else {
        ActionUtil.underModalProgress(project, progressTitle, computable)
    }
}