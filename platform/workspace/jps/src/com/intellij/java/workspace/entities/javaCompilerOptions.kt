// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.workspace.entities

import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ProjectSettingsEntity
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Default
import com.intellij.platform.workspace.storage.annotations.Parent
import org.jetbrains.annotations.NonNls

/**
 * Stores per-module additional Java compiler options (for example `--add-exports`, `--add-modules`, `--patch-module`)
 * that are usually defined by a build tool (Gradle/Maven/Bazel) import.
 *
 * This is the workspace-model backed replacement for the per-module override map previously kept in
 * `JavacConfiguration` (`compiler.xml`). The entity is created with the [EntitySource][module] of its parent
 * [module] so that it is serialized together with the module configuration.
 *
 * Used only in LSP at the moment, see IDEA-307379 for more details
 */
interface JavaModuleCompilerOptionsEntity : WorkspaceEntity {
  @Parent
  val module: ModuleEntity

  val additionalOptions: List<@NonNls String>
}

val ModuleEntity.javaCompilerOptions: JavaModuleCompilerOptionsEntity?
  by WorkspaceEntity.extension()

/**
 * Stores project-wide Java compiler settings: the additional compiler options applied to every module that does not
 * have its own [JavaModuleCompilerOptionsEntity], together with the global javac flags.
 *
 * This is the workspace-model backed replacement for the project level part of `JavacConfiguration`
 * (`JpsJavaCompilerOptions` in `compiler.xml`). The entity is created with the [EntitySource][projectSettings] of its
 * parent [projectSettings].
 *
 *  Used only in LSP at the moment, see IDEA-307379 for more details
 */
interface JavaCompilerProjectSettingsEntity : WorkspaceEntity {
  @Parent
  val projectSettings: ProjectSettingsEntity

  val additionalOptions: List<@NonNls String>
  val preferTargetJdkCompiler: Boolean
    @Default get() = true
  val debuggingInfo: Boolean
    @Default get() = true
  val generateNoWarnings: Boolean
    @Default get() = false
  val deprecation: Boolean
    @Default get() = true
  val maximumHeapSize: Int
    @Default get() = 128
}

val ProjectSettingsEntity.javaCompilerSettings: JavaCompilerProjectSettingsEntity?
  by WorkspaceEntity.extension()
