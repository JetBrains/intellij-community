// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.resolve

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNamedElement
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinAnchorModuleProvider
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.K2IdeKotlinAnchorModuleProvider
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils.findStringWithPrefixes
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.test.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.util.sourceRoots
import java.io.File

abstract class AbstractK2MultiModuleHighlightingTest : AbstractMultiModuleTest() {

    override fun getTestDataDirectory(): File {
        return KotlinRoot.DIR.resolve("fir/tests/testData/resolve/anchors")
    }

    override fun setUpModule() {
        //do not create top level module
    }

    override fun setUp() {
        super.setUp()

        val stdLibrary = TestKotlinArtifacts.kotlinStdlib
        val dependencyModule = module("dependencyModule")
        dependencyModule.addLibrary(stdLibrary)

        val anchorModule = module("anchor")
        anchorModule.addLibrary(stdLibrary)

        val sourceModule = module("sourceModule")
        sourceModule.addLibrary(stdLibrary)

        val libraryName = "aLibrary"
        PsiTestUtil.addLibrary(sourceModule, libraryName, "$testDataPath/", arrayOf(), arrayOf("_library"))
        anchorModule.addDependency(dependencyModule)

        val anchorMapping = mapOf(libraryName to anchorModule.name)

        val anchorModuleProvider = KotlinAnchorModuleProvider.getInstance(project) as K2IdeKotlinAnchorModuleProvider
        anchorModuleProvider.setAnchors(anchorMapping)
    }

    protected fun doTest(filePath: String) {
        val libraryFile = LocalFileSystem.getInstance().refreshAndFindFileByPath("$testDataPath/_library/lib/Elem.kt")!!
        configureByExistingFile(libraryFile)
        val offset = editor.caretModel.offset
        val psiReference = file.findReferenceAt(offset)!!
        val target = psiReference.resolve()
        assertNotNull(target)
        target as PsiNamedElement

        val testFile = LocalFileSystem.getInstance().refreshAndFindFileByPath("$filePath/sourceModule/test.txt")!!
        val text = VfsUtilCore.loadText(testFile)

        assertEquals(findStringWithPrefixes(text, "// expected class:"), target.name)

        val moduleName = findStringWithPrefixes(text, "// module:")!!
        val relativePath = findStringWithPrefixes(text, "// file:")!!
        val dep = ModuleManager.getInstance(project).findModuleByName(moduleName)!!
        val targetFileInTemp = dep.sourceRoots[0].findFileByRelativePath(relativePath)!!
        WriteCommandAction.writeCommandAction(project).run<RuntimeException> {
            // This should trigger cache invalidation.
            val psiFile = (PsiManager.getInstance(project).findFile(targetFileInTemp) as PsiClassOwner)
            val psiClassOrKtClass = psiFile.classes[0].unwrapped!! as PsiNamedElement
            psiClassOrKtClass.setName("Element2")
        }
        assertNull(psiReference.resolve())
    }
}