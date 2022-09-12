// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.fir.analysisApiProviders

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.impl.PsiModificationTrackerImpl
import com.intellij.psi.impl.PsiTreeChangeEventImpl
import com.intellij.psi.impl.PsiTreeChangePreprocessor
import org.jetbrains.kotlin.analysis.low.level.api.fir.element.builder.getNonLocalContainingInBodyDeclarationWith
import org.jetbrains.kotlin.analysis.low.level.api.fir.file.structure.isReanalyzableContainer
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.util.module

internal class FirIdeOutOfBlockPsiTreeChangePreprocessor(private val project: Project) : PsiTreeChangePreprocessor {
    override fun treeChanged(event: PsiTreeChangeEventImpl) {
        if (!PsiModificationTrackerImpl.canAffectPsi(event) ||
            event.isGenericChange ||
            event.code == PsiTreeChangeEventImpl.PsiEventType.BEFORE_CHILD_ADDITION ||
            event.code == PsiTreeChangeEventImpl.PsiEventType.BEFORE_CHILD_REPLACEMENT
        ) {
            return
        }
        if (event.isGlobalChange()) {
            project.getService(FirIdeModificationTrackerService::class.java).increaseModificationCountForAllModules()
            return
        }

        val rootElement = event.parent
        val child = if (event.code == PsiTreeChangeEventImpl.PsiEventType.CHILD_REMOVED) rootElement else event.child

        if (rootElement == null || !rootElement.isPhysical) {
            /**
             * Element which do not belong to a project should not cause OOBM
             */
            return
        }

        if (isOutOfCodeBlockChange(rootElement, child)) {
            project.getService(FirIdeModificationTrackerService::class.java)
                .increaseModificationCountForModuleAndProject(rootElement.module)
        }
    }

    private fun isOutOfCodeBlockChange(rootElement: PsiElement, child: PsiElement?): Boolean {
        return when (rootElement.language) {
            KotlinLanguage.INSTANCE -> {
                isOutOfBlockChange(child ?: rootElement)
            }

            JavaLanguage.INSTANCE -> {
                true // TODO improve for Java KTIJ-21684
            }

            else -> {
                // Any other language may cause OOBM in Kotlin too
                true
            }
        }

    }

    private fun isOutOfBlockChange(psi: PsiElement): Boolean {
        if (!psi.isValid) {
            /**
             * If PSI is not valid, well something bad happened, OOBM won't hurt
             */
            return true
        }
        val container = psi.getNonLocalContainingInBodyDeclarationWith() ?: return true
        return !isReanalyzableContainer(container)
    }

    // Copy logic from PsiModificationTrackerImpl.treeChanged(). Some out-of-code-block events are written to language modification
    // tracker in PsiModificationTrackerImpl but don't have correspondent PomModelEvent. Increase kotlinOutOfCodeBlockTracker
    // manually if needed.
    private fun PsiTreeChangeEventImpl.isGlobalChange() = when (code) {
        PsiTreeChangeEventImpl.PsiEventType.PROPERTY_CHANGED ->
            propertyName === PsiTreeChangeEvent.PROP_UNLOADED_PSI || propertyName === PsiTreeChangeEvent.PROP_ROOTS
        PsiTreeChangeEventImpl.PsiEventType.CHILD_MOVED -> oldParent is PsiDirectory || newParent is PsiDirectory
        else -> parent is PsiDirectory
    }
}