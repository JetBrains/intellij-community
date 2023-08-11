// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiProviders

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.analysis.providers.impl.KotlinGlobalModificationServiceBase

internal class FirIdeKotlinGlobalModificationService(private val project: Project) : KotlinGlobalModificationServiceBase(project) {
    @TestOnly
    override fun incrementModificationTrackers(includeBinaryTrackers: Boolean) {
        FirIdeKotlinModificationTrackerFactory.getInstance(project).incrementModificationsCount(includeBinaryTrackers)
    }
}
