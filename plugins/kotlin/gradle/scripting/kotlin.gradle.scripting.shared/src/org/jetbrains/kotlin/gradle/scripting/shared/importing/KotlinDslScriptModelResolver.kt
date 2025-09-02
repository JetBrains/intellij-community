// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting.shared.importing

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.gradle.toolingExtension.modelProvider.GradleClassBuildModelProvider
import org.jetbrains.kotlin.idea.gradleTooling.KotlinDslScriptAdditionalTask
import org.jetbrains.kotlin.idea.gradleTooling.KotlinDslScriptModelProvider
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

class KotlinDslScriptModelResolver : KotlinDslScriptModelResolverCommon() {

    override fun getModelProviders() = listOf(
        GradleClassBuildModelProvider(KotlinDslScriptAdditionalTask::class.java, GradleModelFetchPhase.PROJECT_LOADED_PHASE),
        KotlinDslScriptModelProvider()
    )

    override fun getExtraProjectModelClasses(): Set<Class<out Any>> {
        return mutableSetOf<Class<out Any>>(KotlinToolingVersion::class.java).also { it.addAll(super.getExtraProjectModelClasses())}
    }

    override fun getToolingExtensionsClasses(): Set<Class<out Any>> {
        return mutableSetOf<Class<out Any>>(KotlinToolingVersion::class.java).also { it.addAll(super.getToolingExtensionsClasses())}
    }
}

