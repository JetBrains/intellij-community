// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea

import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.java.stubs.index.JavaFullClassNameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.codeInsight.hierarchy.HierarchyViewTestFixture
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import kotlin.collections.find

@RunWith(JUnit38ClassRunner::class)
abstract class KotlinHierarchyViewTestBase : KotlinLightCodeInsightFixtureTestCase() {

    @Throws(Exception::class)
    protected open fun doHierarchyTest(
        treeStructureComputable: Computable<out HierarchyTreeStructure>,
        vararg fileNames: String
    ) {
        myFixture.configureByFiles(*fileNames)
        val expectedStructure = loadExpectedStructure()
        val treeStructure = treeStructureComputable.compute()
        runWithModalProgressBlocking(myFixture.project, "") {
            readAction {
                HierarchyViewTestFixture.doHierarchyTest(treeStructure, expectedStructure)
            }
        }
    }

    protected fun findTargetLibraryClass(targetClass: String): PsiElement {
        return JavaFullClassNameIndex.getInstance().getClasses(targetClass, project, GlobalSearchScope.allScope(project))
            .find { it.qualifiedName == targetClass }
            ?: KotlinFullClassNameIndex.Helper.get(targetClass, project, GlobalSearchScope.allScope(project)).find { it.kotlinFqName?.asString() == targetClass }
            ?: error("Could not find java class: $targetClass")
    }

    @Throws(IOException::class)
    protected open fun loadExpectedStructure(): String {
        val verificationFile = File(testDataDirectory, getTestName(false) + "_verification.xml")
        return FileUtil.loadFile(verificationFile)
    }
}