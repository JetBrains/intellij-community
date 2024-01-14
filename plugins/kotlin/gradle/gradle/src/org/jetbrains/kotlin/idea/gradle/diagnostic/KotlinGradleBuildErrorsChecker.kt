// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.diagnostic

import com.intellij.diagnostic.KotlinCompilerCrash
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.io.FileReader

const val BUILD_ERROR_REPORTS_FOLDER = ".gradle/kotlin/errors"
const val BUILD_ERROR_REPORTS_FILE_PREFIX = "errors-"

private const val ERROR_MESSAGE_PREFIX = "error message: "
private const val KOTLIN_VERSION = "kotlin version: "

@ApiStatus.Internal
fun readErrorFileAndProcessEvent(
    file: File,
    process: (KotlinCompilerCrash, String) -> Any
) {
    var message: String? = null
    var stackTrace = ArrayList<String>()
    val timeInMillis = file.nameWithoutExtension.substring(BUILD_ERROR_REPORTS_FILE_PREFIX.length)
    var kotlinVersion = "Unknown"
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

/**
 * Performs checks of Kotlin Compiler crashes which took place outside IDE, e.g. when compilation was executed from Gradle.
 */
@ApiStatus.Internal
class KotlinGradleBuildErrorsChecker(val project: Project) {
    @Volatile
    private var buildErrorsDir: File? = null

    fun init() {
        buildErrorsDir = project.basePath?.let { File(it).resolve(BUILD_ERROR_REPORTS_FOLDER) }
    }

    suspend fun run() {
        withContext(Dispatchers.IO) {
            buildErrorsDir?.listFiles()
                ?.filter { it.isFile && it.nameWithoutExtension.startsWith(BUILD_ERROR_REPORTS_FILE_PREFIX) }
                ?.forEach {
                    try {
                        readErrorFileAndProcessEvent(it) { crashException, rawException ->
                            thisLogger().error(
                                "Exception happen during Kotlin compilation", crashException,
                                Attachment("Kotlin version", crashException.version),
                                Attachment("raw exception", rawException)
                            )
                        }
                        it.delete()
                    } catch (e: Exception) {
                        throw Exception("Could not parse build error file", e)
                    }
                }
        }
    }
}