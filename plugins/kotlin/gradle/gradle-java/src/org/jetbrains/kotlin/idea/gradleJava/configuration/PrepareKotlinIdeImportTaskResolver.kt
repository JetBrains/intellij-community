// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.gradle.toolingExtension.modelProvider.GradleClassProjectModelProvider
import org.jetbrains.kotlin.idea.gradleTooling.PrepareKotlinIdeImportTaskModel
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

class PrepareKotlinIdeImportTaskResolver : AbstractProjectResolverExtension() {
    override fun getModelProviders() = listOf(
        GradleClassProjectModelProvider(
            PrepareKotlinIdeImportTaskModel::class.java,
            GradleModelFetchPhase.PROJECT_LOADED_PHASE
        )
    )
}
