// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.debugger.evaluate

import com.intellij.debugger.engine.evaluation.CodeFragmentKind
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.VfsTestUtil
import org.jetbrains.kotlin.idea.debugger.evaluate.KotlinK2CodeFragmentFactory
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.idea.test.KotlinMultiPlatformProjectDescriptor
import org.jetbrains.kotlin.idea.test.configureMultiPlatformModuleStructure
import org.jetbrains.kotlin.idea.test.runAll
import java.io.File

abstract class AbstractK2MultiplatformCodeFragmentCompletionTest : AbstractK2CodeFragmentCompletionTest() {

    override fun configureFixture(testPath: String): Unit = myFixture.run {
        //configureByFile(File(testPath).name)
        val projectFiles = configureMultiPlatformModuleStructure(testPath)
        val mainFile = projectFiles.mainFile ?: error("Missing '// MAIN' file")
        configureFromExistingVirtualFile(mainFile)
        val elementAt = file?.findElementAt(caretOffset)
        val fragmentText = File("$testPath.fragment").readText()
        val textWithImports = TextWithImportsImpl(CodeFragmentKind.CODE_BLOCK, fragmentText)
        val file = KotlinK2CodeFragmentFactory().createPsiCodeFragment(textWithImports, elementAt, project)!!
        configureFromExistingVirtualFile(file.virtualFile!!)
    }

    override fun setUp() {
        super.setUp()
        // sync is necessary to detect unexpected disappearances of library files
        VfsTestUtil.syncRefresh()
    }

    override fun tearDown() {
        runAll(
            { KotlinMultiPlatformProjectDescriptor.cleanupSourceRoots() },
            { KotlinSdkType.removeKotlinSdkInTests() },
            { super.tearDown() },
        )
    }

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinMultiPlatformProjectDescriptor
    }
}