// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.structureView

import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.structureView.KotlinFileStructureTestBase
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.test.util.invalidateCaches
import java.io.File

class KotlinFirFileStructureInLibrariesTest: KotlinFileStructureTestBase() {

    override val fileExtension: String
        get() = error("`configureDefault` should not be called")

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }

    override val testDataDirectory: File
        get() = IDEA_TEST_DATA_DIR.resolve("structureView/onLibrary")

    fun testPairOutput() {
        doTestForClass("kotlin.Pair")
    }

    fun testAbstractListOutput() {
        doTestForClass("kotlin.collections.AbstractList")
    }

    fun testSimpleDataClassOutput() {
        doTest {
            myFixture.configureByText("SimpleDataClass.kt", "data class SimpleDataClass(val key: Int, val value: String)")
        }
    }

    private fun doTestForClass(className: String) {
        doTest {
            val libraryElement = myFixture.findClass(className).navigationElement
            libraryElement.containingFile
        }
    }

    private fun doTest(configure: () -> PsiFile) {
        val psiFile = configure()
        myFixture.openFileInEditor(psiFile.virtualFile)
        popupFixture.update()
        checkResult()
    }
}