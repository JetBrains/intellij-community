// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.hierarchy

import com.intellij.ide.hierarchy.HierarchyBrowserBaseEx
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.codeInsight.hierarchy.HierarchyViewTestFixture
import org.jetbrains.kotlin.idea.k2.codeinsight.hierarchy.overrides.KotlinOverrideTreeStructure
import org.jetbrains.kotlin.idea.k2.codeinsight.hierarchy.types.KotlinSubtypesHierarchyTreeStructure
import org.jetbrains.kotlin.idea.k2.codeinsight.hierarchy.types.KotlinSupertypesHierarchyTreeStructure
import org.jetbrains.kotlin.idea.test.KotlinLightMultiplatformCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.configureMultiPlatformModuleStructure
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractHierarchyMultiplatformTest : KotlinLightMultiplatformCodeInsightFixtureTestCase() {
    private fun doTest(testPath: String, treeStructureComputable: (VirtualFile) -> HierarchyTreeStructure) {
        val (_, mainFile) = myFixture.configureMultiPlatformModuleStructure(testPath)

        val expectedStructure = FileUtilRt.loadFile(File(testDataDirectory, getTestName(true) + "_verification.xml"))
        val treeStructure = treeStructureComputable(mainFile!!)
        ActionUtil.underModalProgress(project, "") {
            HierarchyViewTestFixture.doHierarchyTest(treeStructure, expectedStructure)
        }
    }

    protected fun doSubClassHierarchyTest(folderName: String) = doTest(folderName) { mainFile ->
        KotlinSubtypesHierarchyTreeStructure(
            project,
            (PsiManager.getInstance(project).findFile(mainFile) as KtFile).declarations.filterIsInstance<KtClassOrObject>().first(),
            HierarchyBrowserBaseEx.SCOPE_PROJECT
        )
    }

    protected fun doSuperClassHierarchyTest(folderName: String) = doTest(folderName) { mainFile ->
        KotlinSupertypesHierarchyTreeStructure(
            project,
            (PsiManager.getInstance(project).findFile(mainFile) as KtFile).declarations.filterIsInstance<KtClassOrObject>().first()
        )
    }

    protected fun doMethodHierarchyTest(folderName: String) = doTest(folderName) { mainFile ->
        val ktFile = PsiManager.getInstance(project).findFile(mainFile) as KtFile
        val callableDeclaration = ktFile.declarations.filterIsInstance<KtClassOrObject>()
            .firstOrNull()?.declarations
            ?.filterIsInstance<KtCallableDeclaration>()
            ?.firstOrNull() ?: ktFile.declarations.filterIsInstance<KtCallableDeclaration>().firstOrNull()
        ?: error("No callable declaration found in file: ${ktFile.name}")
        KotlinOverrideTreeStructure(project, callableDeclaration)
    }
}