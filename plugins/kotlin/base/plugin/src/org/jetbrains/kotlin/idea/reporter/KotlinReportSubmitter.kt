// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.reporter

import com.intellij.diagnostic.ITNReporter
import com.intellij.ide.DataManager
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.SubmittedReportInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.util.Consumer
import com.intellij.util.containers.map2Array
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.base.plugin.KotlinBasePluginBundle
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinIdePlugin
import org.jetbrains.kotlin.idea.util.application.isApplicationInternalMode
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import java.awt.Component
import javax.swing.Icon

private const val KOTLIN_K2_MESSAGE = "This report is from the K2 Kotlin plugin."

/**
 * We need to wrap `ITNReporter` to distinguish K2 from K1 reports.
 */
class KotlinReportSubmitter : ITNReporter() {

    override fun submit(
        events: Array<IdeaLoggingEvent>,
        additionalInfo: String?,
        parentComponent: Component,
        consumer: Consumer<in SubmittedReportInfo>
    ): Boolean {
        val effectiveEvents = when {
            KotlinPluginModeProvider.isK2Mode() -> events.map2Array(::markEventForK2)
            else -> events
        }

        val project: Project? = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(parentComponent))
        if (KotlinIdePlugin.hasPatchedVersion) {
            Notification("Error Report", KotlinBasePluginBundle.message("reporter.text.can.t.report.exception.from.patched.plugin"), NotificationType.INFORMATION)
                .setImportant(false)
                .notify(project)
            return true
        }

        return super.submit(effectiveEvents, additionalInfo, parentComponent, consumer)
    }

    private fun markEventForK2(event: IdeaLoggingEvent): IdeaLoggingEvent {
        return if (event.javaClass == IdeaLoggingEvent::class.java) {
            val k2message = if (event.message.isNullOrBlank()) KOTLIN_K2_MESSAGE else "${event.message}\n${KOTLIN_K2_MESSAGE}"
            IdeaLoggingEvent(k2message, event.throwable, event.attachments, event.plugin, event.data)
        } else {
            // leave foreign events (Android Studio, etc.) intact
            event
        }
    }
}
