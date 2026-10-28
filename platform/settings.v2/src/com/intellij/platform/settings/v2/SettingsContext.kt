// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

// @spec ../../../../../../spec/layer-loading.spec.md
// @spec ../../../../../../spec/settings-contexts.spec.md

import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.settings.v2.SettingsValue.StringValue
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.NonExtendable

/**
 * Tells where the effective value of a setting is read or written: the application, a project, or a file. Settings
 * have file-level granularity, so the context decides which layers apply and in what order.
 *
 * A context comes from the [companion object][Companion] factories. An API consumer never constructs one, because
 * the platform builds the chain. Each context has a [parentContext], and they form a linear chain up to the
 * application. The layer stack follows this chain:
 *
 * - **A file under a content root** goes through its directories up to the topmost root:
 *   `file -> directory -> ... -> root -> project -> app`.
 * - **A file under no content root**, such as a library file or another path, gets the project context itself:
 *   `project -> app`. It has no file-level settings, and it takes no directory layers from where it lives
 *   (ADR 0125).
 *
 * A read goes through [SettingsKey.get] and a write through [SettingsKey.set]. The context-side operators [get] and
 * [set] do the same.
 *
 * A context is a **snapshot** taken at creation. It holds strings only, never a live project or file, and it has no
 * validity check. A stale context still resolves. A read finds the layers that exist, else the default, and a write
 * lands in a layer that may no longer matter. A stale context never makes a read throw. A read can still throw when
 * the default-value factory of the key fails (see [SettingsKey]).
 */
@NonExtendable
@Experimental
interface SettingsContext {
  /** Holds the next wider context in the chain, or `null` for the application context. */
  val parentContext: SettingsContext?

  /**
   * Holds the kind of target that this context describes: the application, a project, or a VFS node, which is a
   * file or a directory. An access overlay reports the kind of its base context. So a [SettingsKey.contextFilter]
   * can restrict a key to some granularities, such as an application-only setting, with no access to the internal
   * context classes.
   */
  val kind: SettingsContextKind

  /**
   * Holds a stable string identity of this context, independent of any live `Project` or `VirtualFile`. Two contexts
   * of the same target have equal identities. The vault caches and persists the data of a context under this key, so
   * it must be derivable without the VFS.
   */
  val identity: String

  /**
   * Holds the real context behind this one. A plain context is its own real context. A [SettingsAccessContext]
   * overlay returns the context that it wraps, without the pinned discriminator overrides. That context identifies
   * the layer. Use it when the structural context matters, such as for the target layer of a write, and the pinned
   * dimensions do not.
   */
  val realContext: SettingsContext get() = this

  /**
   * Returns whether this context is an ancestor of [other] in the resolution chain. An ancestor is a wider node that
   * [other] resolves *through*, such as the project of a file. The check compares the [identity] of the
   * [realContext]s, so an [access] overlay counts as its base.
   *
   * @param strict `false` counts a context as its own ancestor, `true` does not
   */
  fun isAncestorOf(other: SettingsContext, strict: Boolean): Boolean {
    val target = realContext.identity
    val from = if (strict) other.realContext.parentContext else other.realContext
    return generateSequence(from) { it.parentContext }.any { it.identity == target }
  }

  /** Returns whether this context is a descendant of [other], a narrower node that resolves through it. See [isAncestorOf]. */
  fun isDescendantOf(other: SettingsContext, strict: Boolean): Boolean = other.isAncestorOf(this, strict)

  /**
   * Returns whether the resolved value of this context may still be stale. It is stale while a reload of it or of a
   * wider scope has not installed the layers of its kind and of every wider kind (ADR 0160). Only the resolution
   * chain feeds the value of this context, so a reload of a *descendant* does not make it dirty. A read in this window may return a value that is about to change. So a read-then-write flow, such
   * as a migration or an import, checks it first.
   */
  fun isDirty(): Boolean = SettingsVault.getInstance().isDirty(this)

  /** Returns whether no reload keeps this context dirty, so its resolved value is settled. It is the negation of [isDirty]. */
  fun isReady(): Boolean = !isDirty()

  /**
   * Suspends until this context is [ready][isReady]. Then every reload that covers it, at it or at an ancestor, has
   * installed the layers that it reads, and a following read sees settled values. Use it before a read-then-decide flow that must not run on
   * data in the middle of a load, such as a migration, an import, or a startup activity that branches on a setting.
   * A plain `key[ctx]` read stays synchronous.
   */
  suspend fun awaitReady(): Unit = SettingsVault.getInstance().awaitReady(this)

  /**
   * Returns the value that this context pins for the [key] discriminator, or `null` when it pins none. A plain
   * context pins nothing. A [SettingsAccessContext] returns the dimensions that [access] collected. So a caller can
   * query the dimensions of any context in the same way.
   */
  fun <T> discriminator(key: SettingsKey<T>): T? = null

  /** Holds the pinned [LANGUAGE], or `null` when it is unspecified. It is a shorthand for `discriminator(LANGUAGE)`. */
  val language: Language? get() = discriminator(LANGUAGE)

  @Experimental
  companion object {
    /** Returns the application-level context. */
    fun app(): SettingsContext = ContextFactory.getInstance().app()

    /** Returns the project-level context of [project]. */
    fun project(project: Project): SettingsContext = ContextFactory.getInstance().project(project)

    /** Returns the file-level context of [virtualFile]. A [VirtualFile] needs an explicit [project]. */
    @RequiresReadLock
    fun file(virtualFile: VirtualFile, project: Project): SettingsContext =
      ContextFactory.getInstance().file(virtualFile, project)

    /** Returns the file-level context of [psiFile]. A [PsiFile] already carries its project. */
    @RequiresReadLock
    fun file(psiFile: PsiFile): SettingsContext = ContextFactory.getInstance().file(psiFile)
  }
}

/**
 * Tells the granularity of a [SettingsContext]. See [SettingsContext.kind]. The entries go from the widest kind to the
 * narrowest kind, so a [SettingsContextKindRange] compares them by their order.
 */
@Experimental
enum class SettingsContextKind {
  /** The application context, the widest node. */
  APPLICATION,

  /** A project context. */
  PROJECT,

  /** The context of a VFS node, which is a file or a directory. */
  FILE,
}

/**
 * Holds the platform-wide discriminator of the current language, which backs [SettingsContext.language]. It is
 * common enough to be built in. A setting that varies per language, such as code style, discriminates on it.
 */
@Experimental
val LANGUAGE: SettingsKey<Language> = SettingsKey("platform.language", default = Language.ANY, serializer = LanguageSerializer)

private object LanguageSerializer : Serializer<Language> {
  override fun serialize(value: Language): SettingsValue = StringValue(value.id)

  override fun deserialize(value: SettingsValue): Language =
    Language.findLanguageByID((value as StringValue).value) ?: Language.ANY
}
