// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting.importing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Checks exception locations parsing emitted by Gradle 9.8.1.
 *
 * Script descriptions come from
 * * [ProjectFactory](https://github.com/gradle/gradle/blob/v9.8.1/subprojects/core/src/main/java/org/gradle/api/internal/project/ProjectFactory.java#L58),
 * * [ScriptEvaluatingSettingsProcessor](https://github.com/gradle/gradle/blob/v9.8.1/subprojects/core/src/main/java/org/gradle/initialization/ScriptEvaluatingSettingsProcessor.java#L66),
 * * [InitScriptHandler](https://github.com/gradle/gradle/blob/v9.8.1/subprojects/core/src/main/java/org/gradle/initialization/InitScriptHandler.java#L58), and
 * * [DefaultObjectConfigurationAction](https://github.com/gradle/gradle/blob/v9.8.1/subprojects/core/src/main/java/org/gradle/api/internal/plugins/DefaultObjectConfigurationAction.java#L144).
 *
 * Also,
 * * [UriTextResource](https://github.com/gradle/gradle/blob/v9.8.1/platforms/software/resources/src/main/java/org/gradle/internal/resource/UriTextResource.java#L102)
 *   quotes file paths and URIs.
 * * [LocationAwareException](https://github.com/gradle/gradle/blob/v9.8.1/subprojects/core/src/main/java/org/gradle/internal/exceptions/LocationAwareException.java#L57)
 *   adds the line number and the cause.
 *
 * And,
 * * [TextResourceScriptSourceTest](https://github.com/gradle/gradle/blob/v9.8.1/subprojects/core/src/test/groovy/org/gradle/groovy/scripts/TextResourceScriptSourceTest.java#L134)
 *   covers file paths, file URIs, remote URIs, and unquoted string resources.
 * 
 */
class KotlinDslScriptModelParserTest {
    @Test
    fun parsesInitScriptLifecycleFailure() {
        val exception = """
            org.gradle.internal.exceptions.LocationAwareException: Initialization script '/tmp/init.d/broken.init.gradle.kts' line: 8
            GradleLifecycle#beforeProject cannot be called after settings have been evaluated.
        """.trimIndent()

        assertEquals(
            "/tmp/init.d/broken.init.gradle.kts" to KotlinDslScriptModel.Position(8, 0),
            parsePositionFromException(exception),
        )
    }

    @Test
    fun parsesQuotedScriptLocations() {
        val locations = listOf(
            "Build file" to "/tmp/build.gradle.kts",
            "Settings file" to "/tmp/settings.gradle.kts",
            "Initialization script" to "/tmp/init.d/broken.init.gradle.kts",
            "Script" to "/tmp/includes/applied.gradle.kts",
            "Script" to "https://www.gradle.org/unknown.txt",
            "Script" to "jar:file:/tmp/test.jar!/build.script",
            "Build file" to "C:\\work\\build.gradle.kts",
            "Initialization script" to "C:\\Users\\Gradle User\\init.d\\broken.init.gradle.kts",
            "Initialization script" to "/tmp/owner's/init.gradle.kts",
            "Build file" to "/tmp/line: 42/build.gradle.kts",
        )

        locations.forEach { (kind, path) ->
            val exception = "org.gradle.internal.exceptions.LocationAwareException: $kind '$path' line: 21" +
                    System.lineSeparator() + "The actual cause"

            assertEquals(path to KotlinDslScriptModel.Position(21, 0), parsePositionFromException(exception), exception)
        }
    }

    @Test
    fun parsesExceptionWithEitherLineSeparator() {
        listOf("\n", "\r\n").forEach { separator ->
            val exception = "org.gradle.internal.exceptions.LocationAwareException: Build file '/tmp/build.gradle.kts' line: 21" +
                    separator + "The actual cause"

            assertEquals("/tmp/build.gradle.kts" to KotlinDslScriptModel.Position(21, 0), parsePositionFromException(exception))
        }
    }

    @Test
    fun preservesUnpositionedExceptionFallback() {
        val exceptions = listOf(
            "org.gradle.internal.exceptions.LocationAwareException: <string> line: 21",
            "org.gradle.internal.exceptions.LocationAwareException: Initialization script /tmp/init.gradle.kts line: 21",
            "org.gradle.internal.exceptions.LocationAwareException: Build file '' line: 21",
            "org.gradle.internal.exceptions.LocationAwareException: Build file '/tmp/build.gradle.kts'",
            "org.gradle.internal.exceptions.LocationAwareException: Initialization script '/tmp/init.gradle.kts line: 21",
            "org.gradle.internal.exceptions.LocationAwareException: Initialization script '/tmp/init.gradle.kts' line: invalid",
            "java.lang.IllegalStateException: Build file '/tmp/build.gradle.kts' line: 21",
        )

        exceptions.forEach { exception ->
            assertNull(parsePositionFromException(exception), exception)
        }
    }
}
