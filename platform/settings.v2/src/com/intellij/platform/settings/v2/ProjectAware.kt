// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

// @spec ../../../../../../spec/settings-contexts.spec.md

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.NonExtendable

/**
 * Marks a [SettingsContext] that names a project: a project context, or the context of a file or a directory in a
 * project. The context resolves [project] on demand from a stored id and never holds it, so it does not leak the
 * project. A caller can ask which project a context belongs to without a match on each concrete context type. An
 * access overlay is not marked: ask its [SettingsContext.realContext].
 */
@NonExtendable
@Experimental
interface ProjectAware {
  /** Holds the open project that this context refers to, or `null` when no open project has its id. */
  val project: Project?
}
