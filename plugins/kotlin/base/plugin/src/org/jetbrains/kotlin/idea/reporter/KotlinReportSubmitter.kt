// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.reporter

import com.intellij.diagnostic.ITNReporter
import com.intellij.diagnostic.IdeaReportingEvent
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

private const val KOTLIN_FATAL_ERROR_NOTIFICATION_PROPERTY = "kotlin.fatal.error.notification"
private const val IDEA_FATAL_ERROR_NOTIFICATION_PROPERTY = "idea.fatal.error.notification"
private const val DISABLED_VALUE = "disabled"
private const val ENABLED_VALUE = "enabled"

private const val KOTLIN_K2_MESSAGE = "This report is from the K2 Kotlin plugin."

/**
 * We need to wrap `ITNReporter` for force showing of errors from Kotlin plugin even from a released version of IDEA.
 */
class KotlinReportSubmitter : ITNReporter() {
    private val isIdeaAndKotlinRelease: Boolean by lazy {
        // Disabled in a released version of IDEA and Android Studio
        // Enabled in EAPs, Canary and Beta
        val isReleaseLikeIdea = DISABLED_VALUE == System.getProperty(IDEA_FATAL_ERROR_NOTIFICATION_PROPERTY, ENABLED_VALUE)
        isReleaseLikeIdea && KotlinIdePlugin.isRelease
    }

    override fun showErrorInRelease(event: IdeaLoggingEvent): Boolean {
        if (isApplicationInternalMode()) {
            // Reporting is always enabled for internal mode in the platform
            return true
        }

        if (isUnitTestMode()) {
            return true
        }

        val kotlinNotificationEnabled = DISABLED_VALUE != System.getProperty(KOTLIN_FATAL_ERROR_NOTIFICATION_PROPERTY, ENABLED_VALUE)
        if (!kotlinNotificationEnabled) {
            // Kotlin notifications are explicitly disabled
            return false
        }

        if (!isIdeaAndKotlinRelease) {
            return true
        }

        return true
    }

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
        fun patchMessage(message: String): String {
            return if (message.isBlank()) KOTLIN_K2_MESSAGE else "$message\n$KOTLIN_K2_MESSAGE"
        }

        if (event is IdeaReportingEvent) {
            return IdeaReportingEvent(event.data, patchMessage(event.message ?: ""), event.throwableText, event.plugin)
        }

        if (event.javaClass == IdeaLoggingEvent::class.java) {
            return IdeaLoggingEvent(patchMessage(event.message ?: ""), event.throwable, event.data)
        }

        // Leave foreign event as is (Android Studio, etc.)
        return event
    }

    fun showDialog(parent: Component?, @Nls message: String, @Nls title: String, options: Array<String>, defaultOptionIndex: Int, icon: Icon?): Int {
        return if (parent != null) {
            Messages.showDialog(parent, message, title, options, defaultOptionIndex, icon)
        } else {
            Messages.showDialog(message, title, options, defaultOptionIndex, icon)
        }
    }
}
