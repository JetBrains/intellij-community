// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.test.util

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.caches.trackers.KotlinCodeBlockModificationListener
import org.jetbrains.kotlin.idea.caches.trackers.KotlinModuleOutOfCodeBlockModificationTracker

fun Project.invalidateCaches() {
    runWriteAction {
        // see Fe10KotlinGlobalModificationService
        KotlinCodeBlockModificationListener.getInstance(this).incModificationCount()
        KotlinModuleOutOfCodeBlockModificationTracker.incrementModificationCountForAllModules(this)
    }
}