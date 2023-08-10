// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.structureView

import com.intellij.ide.util.InheritedMembersNodeProvider
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.k2.codeinsight.structureView.KotlinFirInheritedMembersNodeProvider
import org.jetbrains.kotlin.idea.structureView.AbstractKotlinFileStructureTest
import org.jetbrains.kotlin.idea.test.runAll
import java.io.File

abstract class AbstractKotlinFirFileStructureTest : AbstractKotlinFileStructureTest() {
    override fun isFirPlugin(): Boolean {
        return true
    }

    override fun nodeProviderClass(): Class<out InheritedMembersNodeProvider<*>> {
        return KotlinFirInheritedMembersNodeProvider::class.java
    }

    override fun tearDown() {
        runAll(
          ThrowableRunnable { project.invalidateCaches() },
          ThrowableRunnable { super.tearDown() }
        )
    }

    override fun getFileName(ext: String): String {
        val firSpecificName = getTestName(false) + ".fir" + ( if (ext.isEmpty()) "" else ".$ext")
        if (File(testDataDirectory, firSpecificName).exists()) {
            return firSpecificName
        }
        return super.getFileName(ext)
    }
}