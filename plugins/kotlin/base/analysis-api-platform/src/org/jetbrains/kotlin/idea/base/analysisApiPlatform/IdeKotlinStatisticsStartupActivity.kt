// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysisApiPlatform

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.jetbrains.kotlin.analysis.api.platform.statistics.KaStatisticsService

/**
 * Automatically starts Analysis API statistics collection if it is enabled via the registry key.
 */
internal class IdeKotlinStatisticsStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        KaStatisticsService.getInstance(project)?.start()
    }
}
