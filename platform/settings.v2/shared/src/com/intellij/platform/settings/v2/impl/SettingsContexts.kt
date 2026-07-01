// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl

// @spec ../../../../../../../../spec/settings-contexts.spec.md

import com.intellij.injected.editor.VirtualFileDelegate
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.SettingsContextKind
import com.intellij.testFramework.LightVirtualFile
import java.util.Collections
import java.util.IdentityHashMap

// Each context computes its own `parentContext` once, eagerly, in the constructor. So the property is a cheap
// lock-free snapshot, and the chain is stable. The derived `parentContext` stays out of `equals`.
// An identity holds no editing flag (ADR 0141).

// The `identity` of a context is also its persisted form. The snapshot cache stores it and rebuilds the context
// through `BaseSettingsContextFactory.deserialize`. So these id prefixes and the `identity` layouts below are an
// on-disk format. A change to any of them changes the cache format and must bump `VERSION` in
// `SettingsCacheSerializer`. The layouts: `app`, `project|<projectId>`, `file|<projectId>|<rootUrl>|<relativePath>`.
internal const val APP_CONTEXT_ID: String = "app"
internal const val PROJECT_CONTEXT_ID: String = "project"
internal const val FILE_CONTEXT_ID: String = "file"

/**
 * Marks a context that names a project: a project context, or the context of a file or a directory in a project.
 * The context resolves [project] on demand from a stored id and never holds it, so it does not leak the project. A
 * caller can ask which project a context belongs to without a match on each concrete context type.
 */
interface ProjectAware {
  /** Holds the open project that this context refers to, or `null` when no open project has its id. */
  val project: Project?
}

/** Represents the application-level context. It is the widest node of the chain and has no parent. */
internal data object AppSettingsContext : SettingsContext {
  override val parentContext: SettingsContext? = null
  override val kind: SettingsContextKind = SettingsContextKind.APPLICATION
  override val identity: String = APP_CONTEXT_ID
}

/**
 * Represents a project-level context. Its parent is the application context. It keeps the location hash of the
 * project, not the [Project], so a retained context never leaks a disposed project. [project] tells whether the
 * project is still open.
 */
internal class ProjectSettingsContext(
  /**
   * Holds the [Project.locationHash] of the project. It is not the platform `com.intellij.platform.project.ProjectId`,
   * which changes on every open (ADR 0119).
   */
  val projectId: String,
) : SettingsContext, ProjectAware {
  /** Builds the context for a live [project]. */
  constructor(project: Project) : this(project.locationHash)

  override val project: Project? get() = findProject(projectId)

  override val identity: String = "$PROJECT_CONTEXT_ID|$projectId"
  override val kind: SettingsContextKind = SettingsContextKind.PROJECT
  override val parentContext: SettingsContext = AppSettingsContext

  override fun equals(other: Any?): Boolean = other is ProjectSettingsContext && other.identity == identity
  override fun hashCode(): Int = identity.hashCode()
}

/**
 * Represents the context of a VFS node: a persistent (non-light) file or a directory under an anchor [rootUrl]. Today
 * the anchor is a content root. Later it can also be an external-dependency root.
 *
 * The context stores [rootUrl], the [relativePath] under it, and the [projectId]. It never holds the live
 * [VirtualFile] or [Project]. The whole [parentContext] chain (directory, …, root, project) derives from these strings
 * with no VFS access, so a context restores from its [identity] alone. Only the first build from a live file ([of])
 * reads the VFS, to resolve the anchor root once. The context does not know whether it is a file or a directory. Only
 * the storage needs that, and the file layer reads it from the disk (ADR 0121).
 *
 * A file under no content root has no file context. It resolves at the project (ADR 0125, see
 * [ContextFactory.file][com.intellij.platform.settings.v2.ContextFactory.file]).
 *
 * @property rootUrl the url of the anchor root
 * @property relativePath the path of this node under [rootUrl], or `""` for the root itself
 */
class VirtualFileSettingsContext(
  /** Holds the [ProjectSettingsContext.projectId] of the project that holds this file. */
  val projectId: String,
  val rootUrl: String,
  val relativePath: String,
) : SettingsContext, ProjectAware {
  /** Holds the VFS url of this node: the anchor [rootUrl] plus the [relativePath] under it. */
  val url: String get() = if (relativePath.isEmpty()) rootUrl else "$rootUrl/$relativePath"

  override val project: Project? get() = findProject(projectId)

  override val identity: String = "$FILE_CONTEXT_ID|$projectId|$rootUrl|$relativePath"
  override val kind: SettingsContextKind = SettingsContextKind.FILE

  /**
   * Holds the context of the enclosing directory. It derives from [rootUrl] and [relativePath] with no VFS access.
   * At the root itself ([relativePath] is empty), the parent is the project context. The property is eager and
   * recursive, so the whole chain builds up front.
   */
  override val parentContext: SettingsContext =
    if (relativePath.isEmpty()) ProjectSettingsContext(projectId)
    else VirtualFileSettingsContext(projectId, rootUrl, relativePath.substringBeforeLast('/', ""))

  override fun equals(other: Any?): Boolean = other is VirtualFileSettingsContext && other.identity == identity
  override fun hashCode(): Int = identity.hashCode()

  companion object {
    /**
     * Builds the context for a live [file] under [root]. It reads only the urls, so it needs no read lock.
     * [parentContext] and every later restore are string math.
     *
     * @param root the anchor root of [file], which the caller resolves
     * @param project the project that holds [file]
     * @throws IllegalArgumentException if [root] is not [file] itself or an ancestor of it
     */
    fun of(file: VirtualFile, root: VirtualFile, project: Project): VirtualFileSettingsContext {
      require(file.url == root.url || file.url.startsWith("${root.url}/")) { "root '${root.url}' is not an ancestor of '${file.url}'" }
      return VirtualFileSettingsContext(project.locationHash,
                                        root.url,
                                        file.url.removePrefix(root.url).trimStart('/'))
    }
  }
}

/**
 * Returns the persistent file that [file] stands for, [file] itself included (ADR 0151). A file is persistent when it
 * is not a [LightVirtualFile]. Until it reaches one, the walk takes the delegate of a [VirtualFileDelegate], the
 * delegate of a [VirtualFileWindow], and the parent of any other non-persistent file. Both checks of a delegate come
 * first, because a [VirtualFileWindow] is usually a [LightVirtualFile] too.
 *
 * @param file the file to resolve
 * @return the persistent file, or `null` when a step gives no file or the walk comes back to a file it visited
 */
fun nearestPersistentFile(file: VirtualFile): VirtualFile? {
  val visited = Collections.newSetFromMap(IdentityHashMap<VirtualFile, Boolean>())
  var current: VirtualFile? = file
  while (current != null && visited.add(current)) {
    current = when (current) {
      is VirtualFileDelegate -> current.delegate
      is VirtualFileWindow -> current.delegate
      is LightVirtualFile -> current.parent
      else -> return current
    }
  }
  return null
}

/** Returns the open project whose [Project.locationHash] is [projectId], or `null` if no such project is open. */
internal fun findProject(projectId: String): Project? =
  ProjectManager.getInstance().openProjects.find { !it.isDisposed && it.locationHash == projectId }

