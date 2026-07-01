// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SettingsApi")
package com.intellij.platform.settings.v2

// @spec ../../../../../../spec/layer-loading.spec.md
// @spec ../../../../../../spec/setting-keys.spec.md
// @spec ../../../../../../spec/settings-writes.spec.md

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus.Experimental

// Core read/write are the get/set operators on SettingsKey. These are convenience forms that build the
// context (the no-arg application level cannot be an operator), plus the context-side operators.

// --- Reads (synchronous) ---------------------------------------------------------------------------

/** Resolves this key at the application level. */
@Experimental
fun <T> SettingsKey<T>.getValue(): T = get(SettingsContext.app())

/** Resolves this key at the project level. */
@Experimental
fun <T> SettingsKey<T>.getValue(project: Project): T = get(SettingsContext.project(project))

/** Resolves this key at the level of [virtualFile]. A [VirtualFile] needs an explicit [project]. */
@Experimental
@RequiresReadLock
fun <T> SettingsKey<T>.getValue(virtualFile: VirtualFile, project: Project): T =
  get(SettingsContext.file(virtualFile, project))

/** Resolves this key at the level of [psiFile]. */
@Experimental
@RequiresReadLock
fun <T> SettingsKey<T>.getValue(psiFile: PsiFile): T = get(SettingsContext.file(psiFile))

// --- Writes (suspending) ---------------------------------------------------------------------------

/** Writes [value] at the application level. */
@Experimental
suspend fun <T> SettingsKey<T>.setValue(value: T): Unit = set(SettingsContext.app(), value)

/** Writes [value] starting at the project level. */
@Experimental
suspend fun <T> SettingsKey<T>.setValue(value: T, project: Project): Unit =
  set(SettingsContext.project(project), value)

// --- Context-side read/write, for code that already holds a context --------------------------------

/** Reads [key] against this context, as `context[key]`. See [SettingsKey.get]. */
@Experimental
operator fun <T> SettingsContext.get(key: SettingsKey<T>): T = key[this]

/** Writes [value] for [key] against this context, as `context[key] = value`. See [SettingsKey.set]. */
@Experimental
suspend operator fun <T> SettingsContext.set(key: SettingsKey<T>, value: T): Unit = key.set(this, value)

/**
 * Waits until this context is [ready][SettingsContext.awaitReady], then reads [key]. So the read never sees data in
 * the middle of a load. Use it for a read-then-decide flow, such as a migration, an import or a startup activity
 * (ADR 0131). A plain `context[key]` read stays synchronous and does not wait.
 */
@Experimental
suspend fun <T> SettingsContext.readWhenReady(key: SettingsKey<T>): T {
  awaitReady()
  return this[key]
}

/**
 * Starts an atomic [SettingsMutationBuilder] with this context as the default for its entries. Call
 * [SettingsMutationBuilder.apply] to write.
 */
@Experimental
fun SettingsContext.mutate(): SettingsMutationBuilder = SettingsVault.getInstance().mutation(this)
