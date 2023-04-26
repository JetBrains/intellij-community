// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.refactoring.suggested.startOffset
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.utils.inlays.InlayHintsProviderTestCase
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import java.io.File

abstract class AbstractKotlinReferenceTypeHintsProviderTest :
    InlayHintsProviderTestCase() { // Abstract- prefix is just a convention for GenerateTests

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
        with(KotlinReferencesTypeHintsProvider()) {
            val fileContents = FileUtil.loadFile(File(fileName), true)
            val settings = createSettings()
            with(settings) {
                when (InTextDirectivesUtils.findStringWithPrefixes(fileContents, "// MODE: ")) {
                    "function_return" -> set(functionReturn = true)
                    "local_variable" -> set(localVariable = true)
                    "parameter" -> set(parameter = true)
                    "property" -> set(property = true)
                    "all" -> set(functionReturn = true, localVariable = true, parameter = true, property = true)
                    else -> set()
                }
            }

            doTestProvider("KotlinReferencesTypeHintsProvider.kt", fileContents, this, settings)
        }
    }

    private fun KotlinReferencesTypeHintsProvider.Settings.set(
        functionReturn: Boolean = false, localVariable: Boolean = false,
        parameter: Boolean = false, property: Boolean = false
    ) {
        this.functionReturnType = functionReturn
        this.localVariableType = localVariable
        this.parameterType = parameter
        this.propertyType = property
    }
}