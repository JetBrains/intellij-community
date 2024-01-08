// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.refactoring.suggested.startOffset
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestCase
import com.intellij.util.ThrowableRunnable
import junit.framework.ComparisonFailure
import org.jetbrains.kotlin.idea.codeInsight.hints.declarative.KotlinReferencesTypeHintsProvider.Companion.SHOW_FUNCTION_PARAMETER_TYPES
import org.jetbrains.kotlin.idea.codeInsight.hints.declarative.KotlinReferencesTypeHintsProvider.Companion.SHOW_FUNCTION_RETURN_TYPES
import org.jetbrains.kotlin.idea.codeInsight.hints.declarative.KotlinReferencesTypeHintsProvider.Companion.SHOW_LOCAL_VARIABLE_TYPES
import org.jetbrains.kotlin.idea.codeInsight.hints.declarative.KotlinReferencesTypeHintsProvider.Companion.SHOW_PROPERTY_TYPES
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import java.io.File

abstract class AbstractKotlinReferenceTypeHintsProviderTest :
    DeclarativeInlayHintsProviderTestCase() { // Abstract- prefix is just a convention for GenerateTests

    override fun setUp() {
        super.setUp()
        PresentationFactory.customToStringProvider = { element ->
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
            ThrowableRunnable { PresentationFactory.customToStringProvider = null },
            ThrowableRunnable { super.tearDown() },
        )
    }

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    fun doTest(testPath: String) { // named according to the convention imposed by GenerateTests
        assertThatActualHintsMatch(testPath)
    }

    private fun assertThatActualHintsMatch(fileName: String) {
        with(org.jetbrains.kotlin.idea.codeInsight.hints.declarative.KotlinReferencesTypeHintsProvider()) {
            val fileContents = FileUtil.loadFile(File(fileName), true)
            val options = buildMap<String, Boolean> {
                put(SHOW_PROPERTY_TYPES, false)
                put(SHOW_LOCAL_VARIABLE_TYPES, false)
                put(SHOW_FUNCTION_RETURN_TYPES, false)
                put(SHOW_FUNCTION_PARAMETER_TYPES, false)
                when (InTextDirectivesUtils.findStringWithPrefixes(fileContents, "// MODE: ")) {
                    "function_return" -> put(SHOW_FUNCTION_RETURN_TYPES, true)
                    "local_variable" -> put(SHOW_LOCAL_VARIABLE_TYPES, true)
                    "parameter" -> put(SHOW_FUNCTION_PARAMETER_TYPES, true)
                    "property" -> put(SHOW_PROPERTY_TYPES, true)
                    "all" -> {
                        put(SHOW_PROPERTY_TYPES, true)
                        put(SHOW_LOCAL_VARIABLE_TYPES, true)
                        put(SHOW_FUNCTION_RETURN_TYPES, true)
                        put(SHOW_FUNCTION_PARAMETER_TYPES, true)
                    }
                    else -> {}
                }
            }

            try {
                doTestProvider("KotlinReferencesTypeHintsProvider.kt", fileContents, this, options)
            } catch (e: ComparisonFailure) {
                throw FileComparisonFailedError(
                    e.message,
                    e.expected, e.actual, File(fileName).absolutePath, null
                )
            }
        }
    }
}