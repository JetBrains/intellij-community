// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.reporter

import com.intellij.diagnostic.IdeaReportingEvent
import com.intellij.diagnostic.ReportMessages
import com.intellij.ide.DataManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.SubmittedReportInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.util.Consumer
import com.intellij.util.ThreeState
import com.intellij.util.containers.map2Array
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.KotlinPluginReleaseDateProvider
import org.jetbrains.kotlin.idea.base.plugin.isK2Plugin
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinIdePlugin
import org.jetbrains.kotlin.idea.util.application.isApplicationInternalMode
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import java.awt.Component
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import javax.swing.Icon

/**
 * We need to wrap ITNReporter for force showing or errors from kotlin plugin even from released version of IDEA.
 */
class KotlinReportSubmitter : ITNReporterCompat() {
    companion object {
        private const val KOTLIN_FATAL_ERROR_NOTIFICATION_PROPERTY = "kotlin.fatal.error.notification"
        private const val IDEA_FATAL_ERROR_NOTIFICATION_PROPERTY = "idea.fatal.error.notification"
        private const val DISABLED_VALUE = "disabled"
        private const val ENABLED_VALUE = "enabled"

        private const val KOTLIN_PLUGIN_RELEASE_DATE = "kotlin.plugin.releaseDate"

        private const val KOTLIN_K2_MESSAGE = "This report is from the K2 Kotlin plugin."

        private val LOG = Logger.getInstance(KotlinReportSubmitter::class.java)

        @Volatile
        private var isFatalErrorReportingDisabledInRelease = ThreeState.UNSURE

        private val isIdeaAndKotlinRelease by lazy {
            // Disabled in released version of IDEA and Android Studio
            // Enabled in EAPs, Canary and Beta
            val isReleaseLikeIdea = DISABLED_VALUE == System.getProperty(IDEA_FATAL_ERROR_NOTIFICATION_PROPERTY, ENABLED_VALUE)
            isReleaseLikeIdea && KotlinIdePlugin.isRelease
        }

        private const val NUMBER_OF_REPORTING_DAYS_FROM_RELEASE = 7

        fun setupReportingFromRelease() {
            if (isUnitTestMode()) {
                return
            }

            if (!isIdeaAndKotlinRelease) {
                return
            }

            val currentPluginReleaseDate = readStoredPluginReleaseDate()
            if (currentPluginReleaseDate != null) {
                isFatalErrorReportingDisabledInRelease =
                    if (isFatalErrorReportingDisabled(currentPluginReleaseDate)) ThreeState.YES else ThreeState.NO
                return
            }

            ApplicationManager.getApplication().executeOnPooledThread {
                val releaseDate =
                    try {
                        KotlinPluginReleaseDateProvider.fetchPluginReleaseDate(KotlinIdePlugin.id, KotlinIdePlugin.version, null)
                    } catch (e: IOException) {
                        LOG.warn(e)
                        null
                    } catch (e: KotlinPluginReleaseDateProvider.ResponseParseException) {
                        // Exception won't be shown, but will be logged
                        LOG.error(e)
                        return@executeOnPooledThread
                    }

                if (releaseDate != null) {
                    writePluginReleaseValue(releaseDate)
                } else {
                    // Will try to fetch the same release date on IDE restart
                }

                isFatalErrorReportingDisabledInRelease = isFatalErrorReportingWithDefault(releaseDate)
            }
        }

        private fun isFatalErrorReportingWithDefault(releaseDate: LocalDate?): ThreeState {
            return if (releaseDate != null) {
                if (isFatalErrorReportingDisabled(releaseDate)) ThreeState.YES else ThreeState.NO
            } else {
                // Disable reporting by default until we obtain a valid release date.
                // We might fail reporting exceptions that happened before initialization but after successful release date fetching
                // such exceptions maybe be reported after restart.
                ThreeState.YES
            }
        }

        private fun isFatalErrorReportingDisabledWithUpdate(): Boolean {
            val currentPluginReleaseDate = readStoredPluginReleaseDate()
            isFatalErrorReportingDisabledInRelease = isFatalErrorReportingWithDefault(currentPluginReleaseDate)

            return isFatalErrorReportingDisabledInRelease == ThreeState.YES
        }

        private fun isFatalErrorReportingDisabled(releaseDate: LocalDate): Boolean {
            return ChronoUnit.DAYS.between(releaseDate, LocalDate.now()) > NUMBER_OF_REPORTING_DAYS_FROM_RELEASE
        }

        private val RELEASE_DATE_FORMATTER: DateTimeFormatter by lazy {
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
        }

        private fun readStoredPluginReleaseDate(): LocalDate? {
            val pluginVersionToReleaseDate = PropertiesComponent.getInstance().getValue(KOTLIN_PLUGIN_RELEASE_DATE) ?: return null

            val parsedDate = fun(): LocalDate? {
                val parts = pluginVersionToReleaseDate.split(":")
                if (parts.size != 2) {
                    return null
                }

                val pluginVersion = parts[0]
                if (pluginVersion != KotlinIdePlugin.version) {
                    // Stored for some other plugin version
                    return null
                }

                return try {
                    val dateString = parts[1]
                    LocalDate.parse(dateString, RELEASE_DATE_FORMATTER)
                } catch (e: DateTimeParseException) {
                    null
                }
            }.invoke()

            if (parsedDate == null) {
                PropertiesComponent.getInstance().setValue(KOTLIN_PLUGIN_RELEASE_DATE, null)
            }

            return parsedDate
        }

        private fun writePluginReleaseValue(date: LocalDate) {
            val currentKotlinVersion = KotlinIdePlugin.version
            val dateStr = RELEASE_DATE_FORMATTER.format(date)
            PropertiesComponent.getInstance().setValue(KOTLIN_PLUGIN_RELEASE_DATE, "$currentKotlinVersion:$dateStr")
        }
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

        return when (isFatalErrorReportingDisabledInRelease) {
            ThreeState.YES ->
                false

            ThreeState.NO -> {
                // Reiterate the check for the case when there was no restart for long and reporting decision might expire
                !isFatalErrorReportingDisabledWithUpdate()
            }

            ThreeState.UNSURE -> {
                // There might be an exception while initialization isn't complete.
                // Decide urgently based on previously stored release version if already fetched.
                !isFatalErrorReportingDisabledWithUpdate()
            }
        }
    }

    override fun submitCompat(
        events: Array<IdeaLoggingEvent>,
        additionalInfo: String?,
        parentComponent: Component?,
        consumer: Consumer<in SubmittedReportInfo>
    ): Boolean {
        val effectiveEvents = when {
            isK2Plugin() -> events.map2Array(::markEventForK2)
            else -> events
        }

        val project: Project? = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(parentComponent))
        if (KotlinIdePlugin.hasPatchedVersion) {
            ReportMessages.GROUP
                .createNotification(KotlinBundle.message("reporter.text.can.t.report.exception.from.patched.plugin"), NotificationType.INFORMATION)
                .setImportant(false)
                .notify(project)
            return true
        }

        return super.submitCompat(effectiveEvents, additionalInfo, parentComponent, consumer)
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
