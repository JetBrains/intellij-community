// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.asJava.classes

import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.asJava.KotlinAsJavaSupport
import org.jetbrains.kotlin.asJava.classes.AbstractUltraLightFacadeClassTest
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.executeOnPooledThreadInReadAction
import org.jetbrains.kotlin.idea.fir.findUsages.doTestWithFIRFlagsByPath
import org.jetbrains.kotlin.idea.perf.UltraLightChecker
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.idea.test.KotlinTestUtils

abstract class AbstractFirLightFacadeClassTest : AbstractUltraLightFacadeClassTest() {

    override fun isFirPlugin(): Boolean = true

    override fun doTest(testDataPath: String) = doTestWithFIRFlagsByPath(testDataPath) {
        super.doTest(testDataPath)
    }

    override fun checkLightFacades(testDataPath: String, facades: Collection<String>, scope: GlobalSearchScope) {

        val expectedTextFile = UltraLightChecker.getJavaFileForTest(testDataPath)

        val classFabric = KotlinAsJavaSupport.getInstance(project)

        val renderedResult = executeOnPooledThreadInReadAction {
            val lightClasses = facades.sorted().flatMap {
                classFabric.getFacadeClasses(FqName(it), scope)
            }.filterIsInstance<KtLightClass>()
            UltraLightChecker.renderLightClasses(testDataPath, lightClasses)
        }

        KotlinTestUtils.assertEqualsToFile(expectedTextFile, renderedResult)
    }
}
