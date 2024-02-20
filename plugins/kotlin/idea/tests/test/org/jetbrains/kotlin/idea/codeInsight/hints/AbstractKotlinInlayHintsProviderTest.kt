// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.refactoring.suggested.startOffset
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestCase
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.idea.test.util.checkPluginIsCorrect
import java.io.File

abstract class AbstractKotlinInlayHintsProviderTest : DeclarativeInlayHintsProviderTestCase() {

    override fun setUp() {
        super.setUp()
        customToStringProvider = { element ->
            val virtualFile = element.containingFile.virtualFile
            val jarFileSystem = virtualFile.fileSystem as? JarFileSystem
            val path = jarFileSystem?.let {
                val root = VfsUtilCore.getRootFile(virtualFile)
                "${it.protocol}://${root.name}${JarFileSystem.JAR_SEPARATOR}${VfsUtilCore.getRelativeLocation(virtualFile, root)}"
            } ?: virtualFile.toString()
            "[$path:${if (jarFileSystem != null) "*" else element.startOffset.toString()}]"
        }
        checkPluginIsCorrect(isK2Plugin())
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { customToStringProvider = null },
            ThrowableRunnable { super.tearDown() },
        )
    }

    open fun isK2Plugin(): Boolean = false

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    fun doTest(testPath: String) {
        val defaultFile = File(testPath)
        val file = if (isK2Plugin()) {
            val file = File(testPath.replace(".kt", ".kt.k2"))
            file.takeIf(File::exists) ?: defaultFile
        } else {
            defaultFile
        }

        assertThatActualHintsMatch(file)
    }

    protected abstract fun inlayHintsProvider(): InlayHintsProvider

    abstract fun assertThatActualHintsMatch(file: File)
}