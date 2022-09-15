// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.diagnostic

import com.intellij.diagnostic.KotlinCompilerCrash
import com.intellij.diagnostic.LogMessage
import com.intellij.diagnostic.MessagePool
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.util.concurrency.AppExecutorUtil
import java.io.File
import java.io.FileReader
import java.util.concurrent.TimeUnit

/**
 * This class schedules checks of Kotlin Compiler crashes which took place outside IDE, e.g. when compilation
 * was executed from Gradle.
 */
class KotlinGradleBuildErrorsChecker : StartupActivity.DumbAware, Runnable {

    companion object {
        const val EXECUTION_DELAY_MIN = 2L
        const val BUILD_ERROR_REPORTS_FOLDER = ".gradle/kotlin/errors"
        const val BUILD_ERROR_REPORTS_FILE_PREFIX = "errors-"
        private const val ERROR_MESSAGE_PREFIX = "error message: "
        private const val KOTLIN_VERSION = "kotlin version: "
        fun readErrorFileAndProcessEvent(
            file: File,
            process: (KotlinCompilerCrash, String) -> Any
        ) {
            var message: String? = null
            var stackTrace = ArrayList<String>()
            val timeInMillis = file.nameWithoutExtension.substring(BUILD_ERROR_REPORTS_FILE_PREFIX.length)
            var kotlinVersion = "Kotlin version does not set"
            fun crashException() {
                message?.also {
                    val logMessage = "$it: $timeInMillis"
                    CompilerInternalError.parseStack(stackTrace).forEach { internalError ->
                        val crashException = KotlinCompilerCrash(logMessage, internalError, kotlinVersion)
                        process(crashException, stackTrace.toString())
                    }
                }
                message = null
                stackTrace = ArrayList()
            }
            FileReader(file).readLines().forEach { str ->
                if (str.startsWith(KOTLIN_VERSION)) {
                    kotlinVersion = str.substring(KOTLIN_VERSION.length)
                } else if (str.startsWith(ERROR_MESSAGE_PREFIX)) {
                    crashException()
                    message = str.substring(ERROR_MESSAGE_PREFIX.length)
                } else {
                    stackTrace.add(str)
                }
            }
            crashException()
        }
    }

    private var buildErrorsDir: File? = null

    override fun runActivity(project: Project) {
        buildErrorsDir = project.basePath?.let { File(it).resolve(BUILD_ERROR_REPORTS_FOLDER) }

        AppExecutorUtil.getAppScheduledExecutorService()
            .scheduleWithFixedDelay(this, EXECUTION_DELAY_MIN, EXECUTION_DELAY_MIN, TimeUnit.SECONDS)
    }

    override fun run() {
        buildErrorsDir?.listFiles()?.filter { it.isFile && it.nameWithoutExtension.startsWith(BUILD_ERROR_REPORTS_FILE_PREFIX) }
            ?.forEach {
                try {
                    readErrorFileAndProcessEvent(it) { crashException, rawException ->
                        val logMessage = crashException.message!! // KotlinCompilerCrash guaranties that message is nonnull field
                        val logEvent = LogMessage.createEvent(
                            crashException,
                            logMessage,
                            Attachment(logMessage, rawException)
                        )
                        val ideaEvent =
                            IdeaLoggingEvent(logMessage, RuntimeException("Kotlin build exception: $it"), logEvent.data)

                        //ideaEvent.log(EventFields.PluginInfo.with(KotlinIdePlugin.getPluginInfo()), *eventPairs)
                        MessagePool.getInstance().addIdeFatalMessage(ideaEvent)
                    }
                    it.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

    }



}