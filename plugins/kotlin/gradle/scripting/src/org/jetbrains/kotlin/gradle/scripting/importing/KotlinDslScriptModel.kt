// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting.importing

import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gradle.model.GradleBuildScriptClasspathModel

data class KotlinDslScriptModel(
    val file: String,
    val inputs: String,
    val classPath: List<String>,
    val sourcePath: List<String>,
    val imports: List<String>,
    val messages: List<Message>,
    val classpathModel: GradleBuildScriptClasspathModel? = null
) {
    data class Message(
        val severity: Severity,
        @Nls val text: String,
        @Nls val details: String = "",
        val position: Position?
    )

    data class Position(val line: Int, val column: Int)

    enum class Severity { WARNING, ERROR }
}

/**
 * Reads a script location from the exception strings exposed by the
 * [Gradle script model](https://github.com/gradle/gradle/blob/v9.8.1/platforms/ide/tooling-api/src/main/java/org/gradle/tooling/model/kotlin/dsl/KotlinDslScriptModel.java#L63).
 */
fun parsePositionFromException(e: String): Pair<String, KotlinDslScriptModel.Position>? {
    if (e.startsWith("org.gradle.internal.exceptions.LocationAwareException:")) {
        val message = e.lineSequence().first()
        val file = message.substringAfter("'", "").substringBeforeLast("'", "")
        val line = message.substringAfterLast("line: ", "").toIntOrNull()
        if (file.isNotBlank() && line != null) {
            return file to KotlinDslScriptModel.Position(line, 0)
        }
    }
    return null
}
