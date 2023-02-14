// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter

import com.intellij.execution.TestStateStorage
import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.psi.PsiElement
import com.intellij.util.Function
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinBaseCodeInsightBundle
import org.jetbrains.kotlin.idea.base.codeInsight.PsiOnlyKotlinMainFunctionDetector
import org.jetbrains.kotlin.idea.base.codeInsight.hasMain
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.tooling
import org.jetbrains.kotlin.idea.base.util.isGradleModule
import org.jetbrains.kotlin.idea.base.util.isUnderKotlinSourceRootTypes
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinTestFramework
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.platform.SimplePlatform
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.idePlatformKind
import org.jetbrains.kotlin.platform.isMultiPlatform
import org.jetbrains.kotlin.platform.konan.NativePlatformWithTarget
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import javax.swing.Icon

class KotlinTestRunLineMarkerContributor : RunLineMarkerContributor() {
    companion object {
        /**
         * Users may want to try to run that individual test, for example to check if it still fails because of some third party problem,
         * but it's not executed when a whole class or test package run.
         *
         * On other side Gradle has its own built-in support for JUnit but doesn't allow fine-tuning behaviour.
         * As of now launching ignored tests (for Gradle) is impossible.
         */
        private fun KtNamedDeclaration.isIgnoredForGradleModule(includeSlowProviders: Boolean): Boolean {
            val ktNamedFunction = this.safeAs<KtNamedFunction>().takeIf { module?.isGradleModule == true } ?: return false
            val testFramework = KotlinTestFramework.getApplicableFor(this, includeSlowProviders)
            return testFramework?.isIgnoredMethod(ktNamedFunction) == true
        }

        fun getTestStateIcon(urls: List<String>, declaration: KtNamedDeclaration): Icon {
            val testStateStorage = TestStateStorage.getInstance(declaration.project)
            val isClass = declaration is KtClass
            val state: TestStateStorage.Record? = run {
                for (url in urls) {
                    testStateStorage.getState(url)?.let { return@run it }
                }
                null
            }
            return getTestStateIcon(state, isClass)
        }

        private fun SimplePlatform.providesRunnableTests(): Boolean {
            if (this is NativePlatformWithTarget) {
                return when {
                    HostManager.hostIsMac -> target in listOf(
                        KonanTarget.IOS_X64,
                        KonanTarget.MACOS_X64,
                        KonanTarget.WATCHOS_X64, KonanTarget.WATCHOS_X86,
                        KonanTarget.TVOS_X64
                    )
                    HostManager.hostIsLinux -> target == KonanTarget.LINUX_X64
                    HostManager.hostIsMingw -> target in listOf(KonanTarget.MINGW_X86, KonanTarget.MINGW_X64)
                    else -> false
                }
            }

            return true
        }

        fun TargetPlatform.providesRunnableTests(): Boolean = componentPlatforms.any { it.providesRunnableTests() }
    }

    override fun getInfo(element: PsiElement): Info? = calculateIcon(element, false)

    override fun getSlowInfo(element: PsiElement): Info? = calculateIcon(element, true)

    private fun calculateIcon(
        element: PsiElement,
        includeSlowProviders: Boolean
    ): Info? {
        val declaration = element.getStrictParentOfType<KtNamedDeclaration>()?.takeIf { it.nameIdentifier == element } ?: return null

        val targetPlatform = declaration.module?.platform ?: return null

        if (declaration is KtNamedFunction) {
            if (declaration.containingClassOrObject == null ||
                targetPlatform.isMultiPlatform() && declaration.containingClass() == null) return null
        } else {
            if (declaration !is KtClassOrObject ||
                targetPlatform.isMultiPlatform() && declaration !is KtClass
            ) return null
        }

        if (!targetPlatform.providesRunnableTests()) return null

        if (!declaration.isUnderKotlinSourceRootTypes()) return null

        val icon = targetPlatform.idePlatformKind.tooling.getTestIcon(declaration, includeSlowProviders)
            ?.takeUnless { declaration.isIgnoredForGradleModule(includeSlowProviders) }
            ?: return null

        return Info(
            icon,
            Function { KotlinBaseCodeInsightBundle.message("highlighter.tool.tip.text.run.test") },
            *ExecutorAction.getActions(getOrder(declaration))
        )
    }

    private fun getOrder(declaration: KtNamedDeclaration): Int {
        if (declaration is KtClass && declaration.companionObjects.any { PsiOnlyKotlinMainFunctionDetector.hasMain(it) }) {
            return 1
        }

        if (declaration is KtNamedFunction) {
            val containingClass = declaration.containingClassOrObject
            return if (containingClass != null && PsiOnlyKotlinMainFunctionDetector.hasMain(containingClass)) 1 else 0
        }

        return if (PsiOnlyKotlinMainFunctionDetector.hasMain(declaration.containingKtFile)) 1 else 0
    }
}
