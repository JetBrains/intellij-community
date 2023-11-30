// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.stubindex.resolve

import com.intellij.ide.actions.cache.ProjectRecoveryScope
import com.intellij.ide.actions.cache.Saul
import com.intellij.idea.ActionsBundle
import com.intellij.notification.NotificationAction.createSimpleExpiring
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.analysis.KotlinBaseAnalysisBundle
import java.util.concurrent.atomic.AtomicBoolean

internal class KotlinRepairIdeAfterCorruptionNotification(private val project: Project) : KotlinCorruptedIndexListener {
    private val pendingNotificationFlag = AtomicBoolean(false)

    override fun corruptionDetected() {
        if (pendingNotificationFlag.get()) return

        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("Recover Kotlin Indices")
            .createNotification(KotlinBaseAnalysisBundle.message("kotlin.indices.corrupted"), NotificationType.ERROR)
            .setSuggestionType(true)
            .setImportantSuggestion(true)
            .addAction(createSimpleExpiring(ActionsBundle.message("action.CallSaul.text"), KotlinRepairIdeAction(project)))
            .whenExpired {
                pendingNotificationFlag.set(false)
            }

        if (pendingNotificationFlag.compareAndSet(/* expectedValue = */ false, /* newValue = */ true)) {
            notification.notify(project)
        }
    }
}

private class KotlinRepairIdeAction(private val project: Project) : Runnable {
    override fun run() {
        service<Saul>().sortThingsOut(ProjectRecoveryScope(project))
    }
}
