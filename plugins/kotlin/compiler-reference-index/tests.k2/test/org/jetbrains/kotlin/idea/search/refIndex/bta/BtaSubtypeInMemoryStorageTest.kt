// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.bta

import com.intellij.testFramework.rules.TempDirectory
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.cri.CriToolchain
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.div

@OptIn(ExperimentalBuildToolsApi::class)
class BtaSubtypeInMemoryStorageTest {
    @Rule
    @JvmField
    val tempDir = TempDirectory()

    @Test
    fun `test hasSubtypeData returns true when subtypes file exists`() {
        val criRoot = createCriRoot()
        (criRoot / CriToolchain.SUBTYPES_FILENAME).createFile()

        assertTrue(criRoot.hasSubtypeData())
    }

    @Test
    fun `test hasSubtypeData returns false when subtypes file missing`() {
        val criRoot = createCriRoot()

        assertFalse(criRoot.hasSubtypeData())
    }

    @Test
    fun `test create returns null when CRI root has no subtype data`() {
        val criRoot = createCriRoot()

        assertNull(BtaSubtypeInMemoryStorage.create(criRoot))
    }

    private fun createCriRoot(): Path = tempDir.newDirectoryPath(CriToolchain.DATA_PATH)
}
