// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.structureView

import com.intellij.ide.util.InheritedMembersNodeProvider
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.k2.codeinsight.structureView.KotlinFirInheritedMembersNodeProvider
import org.jetbrains.kotlin.idea.structureView.AbstractKotlinFileStructureTest
import org.jetbrains.kotlin.idea.test.runAll
import java.io.File

abstract class AbstractKotlinFirFileStructureTest : AbstractKotlinFileStructureTest() {

    override fun nodeProviderClass(): Class<out InheritedMembersNodeProvider<*>> {
        return KotlinFirInheritedMembersNodeProvider::class.java
    }

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }

    override fun getFileName(ext: String): String {
        val k2SpecificName = getTestName(false) + ".k2" + ( if (ext.isEmpty()) "" else ".$ext")
        return if (File(testDataDirectory, k2SpecificName).exists()) {
            k2SpecificName
        } else {
            super.getFileName(ext)
        }
    }
}