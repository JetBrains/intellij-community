// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.completion

import com.intellij.codeInsight.completion.CompletionService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.psi.PsiClass
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.registerOrReplaceServiceInstance
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinResolveScopeEnlarger
import org.jetbrains.kotlin.idea.fir.extensions.CompletionServiceForScopeEnlargerTest
import org.jetbrains.kotlin.idea.fir.extensions.KtResolveScopeEnlargerForTests
import org.jetbrains.kotlin.idea.fir.extensions.ShortNamesCacheForScopeEnlargerTests
import org.jetbrains.kotlin.idea.fir.extensions.TYPE_NAME_FOR_ENLARGED_SCOPE_TEST
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType

abstract class AbstractK2JvmBasicCompletionTestWithScopeEnlarger : AbstractK2JvmBasicCompletionTest() {
    override fun setUp() {
        super.setUp()

        val psiFile = myFixture.configureByText(
            "$TYPE_NAME_FOR_ENLARGED_SCOPE_TEST.java",
            """
                package test;
                public class $TYPE_NAME_FOR_ENLARGED_SCOPE_TEST {
                }
            """.trimIndent()
        )
        val psiClass = psiFile.getChildOfType<PsiClass>() ?: error("A PsiClass is expected")

        ApplicationManager.getApplication()
            .registerOrReplaceServiceInstance(CompletionService::class.java, CompletionServiceForScopeEnlargerTest, testRootDisposable)

        KotlinResolveScopeEnlarger.EP_NAME.point.registerExtension(KtResolveScopeEnlargerForTests(psiFile.virtualFile), testRootDisposable)
        if (!project.extensionArea.hasExtensionPoint(PsiShortNamesCache.EP_NAME)) {
            project.extensionArea.registerExtensionPoint(
                PsiShortNamesCache.EP_NAME.name, PsiShortNamesCache::class.java.name, ExtensionPoint.Kind.INTERFACE, true
            )
        }
        project.extensionArea.getExtensionPoint(PsiShortNamesCache.EP_NAME)
            .registerExtension(ShortNamesCacheForScopeEnlargerTests(psiClass), testRootDisposable)
    }

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() }
        )
    }
}