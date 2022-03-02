// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.analysis.providers.ide.trackers

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.impl.PsiModificationTrackerImpl
import com.intellij.psi.impl.PsiTreeChangeEventImpl
import com.intellij.psi.impl.PsiTreeChangePreprocessor

internal class KotlinOutOfBlockPsiTreeChangePreprocessor(private val project: Project) : PsiTreeChangePreprocessor {
    override fun treeChanged(event: PsiTreeChangeEventImpl) {
        if (!PsiModificationTrackerImpl.canAffectPsi(event)) return
        if (event.isOutOfBlockChange()) {
            incrementModificationsCount()
        }
    }

    private fun incrementModificationsCount() {
        project.getService(KotlinFirModificationTrackerService::class.java).increaseModificationCountForAllModules()
    }

    // Copy logic from PsiModificationTrackerImpl.treeChanged(). Some out-of-code-block events are written to language modification
    // tracker in PsiModificationTrackerImpl but don't have correspondent PomModelEvent. Increase kotlinOutOfCodeBlockTracker
    // manually if needed.
    private fun PsiTreeChangeEventImpl.isOutOfBlockChange() = when (code) {
        PsiTreeChangeEventImpl.PsiEventType.PROPERTY_CHANGED ->
            propertyName === PsiTreeChangeEvent.PROP_UNLOADED_PSI || propertyName === PsiTreeChangeEvent.PROP_ROOTS
        PsiTreeChangeEventImpl.PsiEventType.CHILD_MOVED -> oldParent is PsiDirectory || newParent is PsiDirectory
        else -> parent is PsiDirectory
    }
}