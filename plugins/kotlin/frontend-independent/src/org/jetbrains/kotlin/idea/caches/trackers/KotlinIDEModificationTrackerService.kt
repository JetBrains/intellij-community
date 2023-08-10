// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.caches.trackers

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.impl.PsiModificationTrackerImpl
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.analyzer.KotlinModificationTrackerService
import org.jetbrains.kotlin.psi.KtFile

class KotlinIDEModificationTrackerService(project: Project) : KotlinModificationTrackerService() {
    override val modificationTracker: ModificationTracker = PsiModificationTracker.getInstance(project)

    override val outOfBlockModificationTracker: ModificationTracker =
        KotlinCodeBlockModificationListener.getInstance(project).kotlinOutOfCodeBlockTracker

    override fun fileModificationTracker(file: KtFile): ModificationTracker =
        file.perFileModificationTracker

    companion object {
        @TestOnly
        fun invalidateCaches(project: Project) {
            project.getService(KotlinModificationTrackerService::class.java).apply {
                (outOfBlockModificationTracker as SimpleModificationTracker).incModificationCount()

                @Suppress("DEPRECATION")
                (modificationTracker as PsiModificationTrackerImpl).incCounter()
            }
        }
    }
}