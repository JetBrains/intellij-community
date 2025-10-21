// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter

import com.intellij.execution.TestStateStorage
import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.psi.PsiElement
import com.intellij.testIntegration.TestFramework
import com.intellij.util.Function
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinBaseCodeInsightBundle
import org.jetbrains.kotlin.idea.base.codeInsight.PsiOnlyKotlinMainFunctionDetector
import org.jetbrains.kotlin.idea.base.codeInsight.hasMain
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.tooling
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.util.isUnderKotlinSourceRootTypes
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework
import org.jetbrains.kotlin.idea.util.RunConfigurationUtils
import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.platform.*
import org.jetbrains.kotlin.platform.konan.NativePlatformWithTarget
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
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
        private fun KtNamedDeclaration.isIgnoredForGradleConfiguration(includeSlowProviders: Boolean): Boolean {
            val ktNamedFunction = this.safeAs<KtNamedFunction>().takeIf {
                RunConfigurationUtils.isGradleRunConfiguration(this)
            } ?: return false
            val ktClassOrObject = ktNamedFunction.containingClassOrObject ?: return false
            
            return TestFramework.EXTENSION_NAME.extensionList.any {
                if (includeSlowProviders && it !is KotlinPsiBasedTestFramework) {
                    val lightMethod = ktNamedFunction.toLightMethods().firstOrNull()
                    it?.isIgnoredMethod(lightMethod) == true
                } else if (it is KotlinPsiBasedTestFramework) {
                    it.isTestClass(ktClassOrObject) && it.isIgnoredMethod(ktNamedFunction)
                } else {
                    false
                }
            }
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
            return when (this) {
                is NativePlatformWithTarget -> {
                    when {
                        HostManager.hostIsMac -> {
                            val testTargets = if (HostManager.host.architecture == Architecture.ARM64) {
                                listOf(
                                    KonanTarget.IOS_SIMULATOR_ARM64,
                                    KonanTarget.MACOS_ARM64,
                                    KonanTarget.WATCHOS_SIMULATOR_ARM64,
                                    KonanTarget.TVOS_SIMULATOR_ARM64
                                )
                            } else {
                                listOf(
                                    KonanTarget.IOS_X64,
                                    KonanTarget.MACOS_X64,
                                    KonanTarget.WATCHOS_X64,
                                    KonanTarget.TVOS_X64
                                )
                            }
                            target in testTargets
                        }

                        HostManager.hostIsLinux -> target == KonanTarget.LINUX_X64
                        HostManager.hostIsMingw -> target in listOf(KonanTarget.MINGW_X64)
                        else -> false
                    }
                }

                else -> true
            }
        }

        fun TargetPlatform.providesRunnableTests(): Boolean = componentPlatforms.any { it.providesRunnableTests() }
    }

    override fun getInfo(element: PsiElement): Info? = calculateIcon(element, false)

    override fun getSlowInfo(element: PsiElement): Info? = calculateIcon(element, true)

    private fun calculateIcon(
        element: PsiElement,
        includeSlowProviders: Boolean
    ): Info? {
        val declaration = (element.parent as? KtNamedDeclaration)?.takeIf { it.nameIdentifier == element } ?: return null

        val module = declaration.module
        val targetPlatform = module?.platform ?: return null

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

        if (declaration.isIgnoredForGradleConfiguration(includeSlowProviders)) return null

        return targetPlatform.idePlatformKind.tooling.getTestIcon(declaration, includeSlowProviders)?.let {
            Info(
                it,
                Function { KotlinBaseCodeInsightBundle.message("highlighter.tool.tip.text.run.test") },
                *ExecutorAction.getActions(getOrder(declaration))
            )
        }
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
