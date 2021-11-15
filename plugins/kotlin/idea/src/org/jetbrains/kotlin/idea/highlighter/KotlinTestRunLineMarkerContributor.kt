// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.execution.TestStateStorage
import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.psi.PsiElement
import com.intellij.util.Function
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.platform.tooling
import org.jetbrains.kotlin.idea.project.platform
import org.jetbrains.kotlin.idea.roots.isUnderKotlinSourceRootTypes
import org.jetbrains.kotlin.idea.util.projectStructure.module
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.platform.SimplePlatform
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.idePlatformKind
import org.jetbrains.kotlin.platform.konan.NativePlatformWithTarget
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import javax.swing.Icon

class KotlinTestRunLineMarkerContributor : RunLineMarkerContributor() {
    companion object {
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

        fun SimplePlatform.providesRunnableTests(): Boolean {
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

    override fun getInfo(element: PsiElement): Info? {
        val declaration = element.getStrictParentOfType<KtNamedDeclaration>() ?: return null
        if (declaration.nameIdentifier != element) return null

        if (declaration !is KtClass && declaration !is KtNamedFunction) return null

        if (declaration is KtNamedFunction && declaration.containingClass() == null) return null

        val targetPlatform = declaration.module?.platform ?: return null
        if (!targetPlatform.providesRunnableTests()) return null

        if (!isUnderKotlinSourceRootTypes(declaration.containingFile)) return null

        val icon = targetPlatform.idePlatformKind.tooling.getTestIcon(declaration) {
            declaration.resolveToDescriptorIfAny()
        } ?: return null
        return Info(icon, Function { KotlinBundle.message("highlighter.tool.tip.text.run.test") }, *ExecutorAction.getActions())
    }
}
