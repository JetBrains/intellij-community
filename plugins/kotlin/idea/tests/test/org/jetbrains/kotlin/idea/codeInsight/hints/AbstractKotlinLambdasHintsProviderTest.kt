// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestCase
import com.intellij.util.ThrowableRunnable
import junit.framework.ComparisonFailure
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.codeInsight.hints.declarative.SHOW_IMPLICIT_RECEIVERS_AND_PARAMS
import org.jetbrains.kotlin.idea.codeInsight.hints.declarative.SHOW_RETURN_EXPRESSIONS
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import java.io.File

abstract class AbstractKotlinLambdasHintsProvider : DeclarativeInlayHintsProviderTestCase() {

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    override fun setUp() {
        super.setUp()
        customToStringProvider = { element ->
            val virtualFile = element.containingFile.virtualFile
            val jarFileSystem = virtualFile.fileSystem as? JarFileSystem
            val path = jarFileSystem?.let {
                val root = VfsUtilCore.getRootFile(virtualFile)
                "${it.protocol}://${root.name}${JarFileSystem.JAR_SEPARATOR}${VfsUtilCore.getRelativeLocation(virtualFile, root)}"
            } ?: virtualFile.toString()
            "$path:${if (jarFileSystem != null) "*" else element.startOffset.toString()}"
        }
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { customToStringProvider = null },
            ThrowableRunnable { super.tearDown() },
        )
    }

    fun doTest(testPath: String) { // named according to the convention imposed by GenerateTests
        try {
            assertThatActualHintsMatch(testPath)
        } finally {
            customToStringProvider = null
        }
    }

    private fun assertThatActualHintsMatch(fileName: String) {
        with(org.jetbrains.kotlin.idea.codeInsight.hints.declarative.KotlinLambdasHintsProvider()) {
            val fileContents = FileUtil.loadFile(File(fileName), true)
            val options = buildMap<String, Boolean> {
                put(SHOW_RETURN_EXPRESSIONS.name, false)
                put(SHOW_IMPLICIT_RECEIVERS_AND_PARAMS.name, false)

                when (InTextDirectivesUtils.findStringWithPrefixes(fileContents, "// MODE: ")) {
                    "return" -> put(SHOW_RETURN_EXPRESSIONS.name, true)
                    "receivers_params" -> put(SHOW_IMPLICIT_RECEIVERS_AND_PARAMS.name, true)
                    "return-&-receivers_params" -> {
                        put(SHOW_IMPLICIT_RECEIVERS_AND_PARAMS.name, true)
                        put(SHOW_RETURN_EXPRESSIONS.name, true)
                    }
                }
            }

            try {
                doTestProvider("KotlinLambdasHintsProvider.kt", fileContents, this, options)
            } catch (e: ComparisonFailure) {
                throw FileComparisonFailedError(
                    e.message,
                    e.expected, e.actual, File(fileName).absolutePath, null
                )
            }
        }
    }

}