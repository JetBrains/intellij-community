// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.statistics

import com.intellij.testFramework.utils.io.createDirectory
import com.intellij.testFramework.utils.io.createFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.setLastModifiedTime

class KotlinGradleFUSLoggerTest {

    @Test
    fun testCleanOldFiles(@TempDir tempDir: Path) {
        val profileDir = tempDir.createDirectory("kotlin-profile")
        profileDir.createFile("some_file.finish-profile")
        val oldFile = profileDir.createFile("old.finish-profile")
        oldFile.setLastModifiedTime(FileTime.from(Instant.now().minus(32, ChronoUnit.DAYS)))

        assertEquals(2, profileDir.listDirectoryEntries().size)
        KotlinGradleFUSLogger.clearOldFiles(profileDir)
        assertEquals(1, profileDir.listDirectoryEntries().size)
    }

}