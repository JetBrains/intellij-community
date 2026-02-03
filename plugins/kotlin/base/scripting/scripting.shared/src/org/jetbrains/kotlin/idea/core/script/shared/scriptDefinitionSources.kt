// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.shared

import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource

val SCRIPT_DEFINITIONS_SOURCES: ProjectExtensionPointName<ScriptDefinitionsSource> =
  ProjectExtensionPointName("org.jetbrains.kotlin.scriptDefinitionsSource")

inline fun <reified T : ScriptDefinitionsSource> Project.scriptDefinitionsSourceOfType(): T? =
    SCRIPT_DEFINITIONS_SOURCES.getExtensions(this).filterIsInstance<T>().firstOrNull()