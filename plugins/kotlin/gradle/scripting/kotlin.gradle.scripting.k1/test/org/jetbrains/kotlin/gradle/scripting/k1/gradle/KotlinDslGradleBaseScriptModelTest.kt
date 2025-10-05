// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting.k1.gradle

import org.gradle.api.internal.file.IdentityFileResolver
import org.gradle.groovy.scripts.TextResourceScriptSource
import org.gradle.internal.exceptions.LocationAwareException
import org.gradle.internal.resource.UriTextResource
import org.jetbrains.kotlin.gradle.scripting.shared.importing.parsePositionFromException
import org.junit.Test
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteExisting
import kotlin.io.path.div
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class KotlinDslScriptModelTest {
    @Test
    fun testExceptionPositionParsing() {
        val file = createTempDirectory("kotlinDslTest") / "build.gradle.kts"
        val line = 10

        val mockScriptSource = TextResourceScriptSource(UriTextResource("build file", file.toUri(), IdentityFileResolver()))
        val mockException = LocationAwareException(RuntimeException(), mockScriptSource, line)
        val fromException = parsePositionFromException(mockException.stackTraceToString())
        assertNotNull(fromException, "Position should be parsed")
        assertEquals(fromException.first, file.toAbsolutePath().toString(), "Wrong file name parsed")
        assertEquals(fromException.second.line, line, "Wrong line number parsed")

        file.parent.deleteExisting()
    }
}