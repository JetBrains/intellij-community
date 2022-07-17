// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.asJava.classes

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.asJava.KotlinAsJavaSupport
import org.jetbrains.kotlin.idea.perf.UltraLightChecker
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractUltraLightFacadeClassTest15 : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE

    open fun doTest(testDataPath: String) {
        val testDataFile = File(testDataPath)
        val sourceText = testDataFile.readText()
        myFixture.addFileToProject(testDataFile.name, sourceText) as KtFile

        UltraLightChecker.checkForReleaseCoroutine(sourceText, module)

        val additionalFile = File("$testDataPath.1")
        if (additionalFile.exists()) {
            myFixture.addFileToProject(additionalFile.name.replaceFirst(".kt.1", "1.kt"), additionalFile.readText())
        }

        val scope = GlobalSearchScope.allScope(project)
        val facades = KotlinAsJavaSupport.getInstance(project).getFacadeNames(FqName.ROOT, scope)

        checkLightFacades(testDataPath, facades, scope)
    }

    protected open fun checkLightFacades(testDataPath: String, facades: Collection<String>, scope: GlobalSearchScope) {
        for (facadeName in facades) {
            val ultraLightClass = KtLightClassForFacadeImpl.createForFacadeNoCache(FqName(facadeName), scope, project)
            if (ultraLightClass != null) {
                UltraLightChecker.checkDescriptorsLeak(ultraLightClass)
            }
        }
    }
}
