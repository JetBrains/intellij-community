// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.psi.util.startOffset
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestCase
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import java.io.File

abstract class AbstractKotlinInlayHintsProviderTest : DeclarativeInlayHintsProviderTestCase(),
                                                      ExpectedPluginModeProvider {

    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }

        customToStringProvider = { element ->
            val virtualFile = element.containingFile.virtualFile
            val jarFileSystem = virtualFile.fileSystem as? JarFileSystem
            val path = jarFileSystem?.let {
                val root = VfsUtilCore.getRootFile(virtualFile)
                "${it.protocol}://${root.name}${JarFileSystem.JAR_SEPARATOR}${VfsUtilCore.getRelativeLocation(virtualFile, root)}"
            } ?: virtualFile.name
            "[$path:${if (jarFileSystem != null) "*" else element.startOffset.toString()}]"
        }
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { customToStringProvider = null },
            ThrowableRunnable { super.tearDown() },
        )
    }

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    fun doTest(testPath: String) {
        val defaultFile = File(testPath)
        val k2File = when (pluginMode) {
            KotlinPluginMode.K1 -> null
            KotlinPluginMode.K2 -> File(testPath.replace(".kt", ".k2.kt"))
                .takeIf(File::exists)
        }

        configureDependencies(defaultFile)
        val file = k2File ?: defaultFile
        IgnoreTests.runTestIfNotDisabledByFileDirective(file.toPath(), IgnoreTests.DIRECTIVES.of(pluginMode)) {
            doTestProviders(file = file)
        }
    }

    private val dependencySuffixes = listOf(".dependency.kt", ".dependency.java", ".dependency1.kt", ".dependency2.kt")

    protected fun configureDependencies(file: File) {
        val parentFile = file.parentFile
        File(parentFile, "_lib.kt").configureFixture()
        for (suffix in dependencySuffixes) {
            val dependency = file.name.replace(".kt", suffix)
            File(parentFile, dependency).configureFixture()
        }
    }

    private fun File.configureFixture() {
        if (this.exists()) {
            myFixture.configureByFile(this.absolutePath)
        }
    }

    protected abstract fun inlayHintsProvider(): InlayHintsProvider

    protected open fun calculateOptions(fileContents: String): Map<String, Boolean> =
        emptyMap()

    protected open fun doTestProviders(file: File) {
        val fileContents: String = FileUtil.loadFile(file, true)
        val inlayHintsProvider: InlayHintsProvider = inlayHintsProvider()
        val options: Map<String, Boolean> = calculateOptions(fileContents)

        try {
            doTestProvider("${file.name.substringBefore(".")}.kt", fileContents, inlayHintsProvider, options, file)
        } catch (e: FileComparisonFailedError) {
            throw FileComparisonFailedError(
                e.message,
                e.expectedStringPresentation,
                e.actualStringPresentation,
                file.absolutePath,
                null
            )
        }
    }
}