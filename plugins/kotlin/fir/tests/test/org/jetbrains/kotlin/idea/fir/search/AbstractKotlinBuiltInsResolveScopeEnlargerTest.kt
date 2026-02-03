// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.search

import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.test.KotlinLightMultiplatformCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.configureMultiPlatformModuleStructure
import org.jetbrains.kotlin.psi.KtElement

abstract class AbstractKotlinBuiltInsResolveScopeEnlargerTest : KotlinLightMultiplatformCodeInsightFixtureTestCase() {
    fun doTest(testPath: String) {
        val virtualFile = myFixture.configureMultiPlatformModuleStructure(testPath).mainFile
        require(virtualFile != null)
        myFixture.configureFromExistingVirtualFile(virtualFile)
        val element = myFixture.getFile().findElementAt(myFixture.editor.caretModel.offset)?.parentOfType<KtElement>()!!
        val resolved = element.mainReference?.resolve() as PsiNamedElement
        assert(element.resolveScope.contains(resolved.containingFile.virtualFile))
    }
}