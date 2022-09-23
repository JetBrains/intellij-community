// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfoOrNull
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class CustomModuleInfoTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    fun testModuleInfoForMembersOfLightClassForDecompiledFile() {
        //NOTE: any class with methods from stdlib will do
        val tuplesKtClass = JavaPsiFacade.getInstance(project).findClass("kotlin.TuplesKt", GlobalSearchScope.allScope(project))!!
        val classModuleInfo = tuplesKtClass.moduleInfo
        Assert.assertTrue(classModuleInfo is LibraryInfo)
        val methods = tuplesKtClass.methods
        Assert.assertTrue(methods.isNotEmpty())
        methods.forEach {
            Assert.assertEquals("Members of decompiled class should have the same module info", classModuleInfo, it.moduleInfo)
        }
    }

    fun testModuleInfoForPsiCreatedByJavaPsiFactory() {
        val dummyClass = PsiElementFactory.getInstance(project).createClass("A")
        val moduleInfo = dummyClass.moduleInfoOrNull
        Assert.assertEquals("Should be null for psi created by factory", null, moduleInfo)
    }
}
