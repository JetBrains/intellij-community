// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.structureView

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.structureView.KotlinFileStructureTestBase
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import java.io.File

class KotlinFirFileStructureInLibrariesTest: KotlinFileStructureTestBase() {
    override val fileExtension: String
        get() = error("`configureDefault` should not be called")

    override fun isFirPlugin(): Boolean {
        return true
    }

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { project.invalidateCaches() },
            ThrowableRunnable { super.tearDown() }
        )
    }

    override val testDataDirectory: File
        get() = IDEA_TEST_DATA_DIR.resolve("structureView/onLibrary")

    fun testPairOutput() {
        val libraryElement = myFixture.findClass("kotlin.Pair").navigationElement
        myFixture.openFileInEditor(libraryElement.containingFile.virtualFile)
        popupFixture.update()
        checkResult()
    }
}