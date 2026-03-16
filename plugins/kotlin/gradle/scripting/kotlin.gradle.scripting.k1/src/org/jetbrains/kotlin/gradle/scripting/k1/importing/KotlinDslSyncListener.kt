// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k1.importing

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.gradle.scripting.k1.GradleScriptDefinitionsContributor
import org.jetbrains.kotlin.gradle.scripting.shared.importing.AbstractKotlinDslSyncListener
import org.jetbrains.kotlin.gradle.scripting.shared.importing.KotlinDslGradleBuildSync

@K1Deprecation
class KotlinDslSyncListener : AbstractKotlinDslSyncListener() {
    override fun reloadDefinitions(
        project: Project,
        sync: KotlinDslGradleBuildSync
    ) {
        GradleScriptDefinitionsContributor.getInstance(project)?.reloadIfNeeded(sync.workingDir, sync.gradleHome, sync.javaHome)
    }
}