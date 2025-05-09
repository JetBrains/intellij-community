// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.importing

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.gradle.scripting.k2.GradleScriptDefinitionsStorage
import org.jetbrains.kotlin.gradle.scripting.shared.importing.AbstractKotlinDslSyncListener
import org.jetbrains.kotlin.gradle.scripting.shared.importing.KotlinDslGradleBuildSync

class KotlinDslSyncListener : AbstractKotlinDslSyncListener() {
    override fun reloadDefinitions(
        project: Project,
        sync: KotlinDslGradleBuildSync
    ) {
        if (sync.models.isEmpty()) return
        GradleScriptDefinitionsStorage.getInstance(project).loadDefinitionsFromDisk(sync.workingDir, sync.gradleHome, sync.javaHome)
    }
}