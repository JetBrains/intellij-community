// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.agents

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * Finds the open project that an agent works in, so that an agent event can carry the same `project` field as a
 * project state collector.
 *
 * The platform adds that field from a [Project] instance. See `FeatureUsageData.addProject`. The field is the join
 * key between an agent event and the project metrics, because the platform salts the value per recorder. So one
 * project gives the same value in every group of that recorder.
 *
 * An agent session holds a working directory, not a [Project]. The agent runtime is application level on purpose.
 * A path is not a substitute for the instance: the platform builds the field from `Project.getPresentableUrl`, which
 * is system dependent, while a path variant normalizes the separators. The two disagree on Windows. They also
 * disagree for a project that a `.ipr` file defines, because the presentable url is then the file, not the directory.
 * So the caller resolves the instance here, and the platform builds the field from it.
 *
 * Call this at the point that logs the event. Do not put the result in a telemetry payload type, and do not keep it
 * in a session: `plugins/air/spec/core/air-telemetry.spec.md` keeps a path and a raw id out of a lifecycle payload.
 */
@ApiStatus.Internal
object AgentProjectKey {
  /**
   * Returns the open project whose base directory is [workingDirectory], or `null` when no open project matches.
   *
   * The match is on the base directory only. An agent that runs in a subdirectory of a project gets `null`, because
   * a walk up the parents would attribute the session to a project the user did not open for it.
   *
   * The match tolerates a symlink. It compares the plain paths first, and it resolves both sides only when that
   * fails, so the usual case reads no disk.
   *
   * A `null` result is expected, not an error. A headless agent and a remote agent have no open project. The caller
   * then logs the event with no `project` field, and the absent field means "unknown".
   */
  @JvmStatic
  fun findOpenProject(workingDirectory: Path?): Project? {
    if (workingDirectory == null) return null
    val candidates = ProjectManager.getInstanceIfCreated()?.openProjects?.filterNot { it.isDisposed } ?: return null
    return candidates.firstOrNull { isProjectBaseDirectory(it.basePath, workingDirectory) }
  }

  /**
   * Reports whether [basePath] and [workingDirectory] name the same directory.
   *
   * This holds the whole decision, so a test drives it without an open project. Every mistake this guards is a
   * path mistake: a separator that differs by platform, a symlink that one side resolved, or a subdirectory that
   * must not match.
   *
   * @param basePath the value of `Project.getBasePath`, or `null` for a project with none
   */
  @JvmStatic
  fun isProjectBaseDirectory(@NonNls basePath: String?, workingDirectory: Path): Boolean {
    val base = basePath?.toPathOrNull() ?: return false
    val target = workingDirectory.normalize()

    // The plain comparison covers the usual case and reads no disk.
    if (base == target) return true

    // A working directory can arrive from a deeplink, a terminal, or a typed value, so one side can hold a symlink
    // that the other side already resolved. On macOS `/tmp` resolves to `/private/tmp`. Without this the paths
    // differ, the event omits the field, and the join reports nothing with no error to show why.
    val realTarget = target.toRealPathOrNull() ?: return false
    return base.toRealPathOrNull() == realTarget
  }

  private fun String.toPathOrNull(): Path? = try {
    Path.of(this).normalize()
  }
  catch (_: InvalidPathException) {
    null
  }

  /** Resolves the symlinks of a path, or returns `null` when the path does not exist or cannot be read. */
  private fun Path.toRealPathOrNull(): Path? = try {
    toRealPath()
  }
  catch (_: IOException) {
    null
  }
}
