// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.importing

import com.intellij.platform.workspace.storage.EntitySource
import org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleKotlinScriptEntitySource
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.gradleEntitySource


fun gradleKotlinScriptEntitySource(
    context: ProjectResolverContext,
    filter: (GradleKotlinScriptEntitySource) -> Boolean = { true }
): (EntitySource) -> Boolean =
    gradleEntitySource<GradleKotlinScriptEntitySource>(context, filter)
