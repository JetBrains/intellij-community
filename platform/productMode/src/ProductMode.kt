// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.productMode

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

/**
 * Describes a mode in which a product may be started.
 *
 * The mode of the current process is held by `CurrentProductMode`, and a process may move between
 * modes without a restart. Use `com.intellij.platform.ide.productMode.IdeProductMode` to read it
 * from product code.
 *
 * TODO: reuse inside [com.intellij.idea.AppMode]?
 */
@ApiStatus.Experimental
enum class ProductMode(val id: @NonNls String) {
  /**
   * Indicates that this process performs all necessary tasks to provide smart features itself.
   * This is the default mode for all IDEs.
   */
  MONOLITH("monolith"),

  /**
   * Indicates that this process is running in a frontend mode (JetBrains Client).
   * It doesn't perform heavy tasks like code analysis and takes necessary information from a separate backend process.
   */
  FRONTEND("frontend"),

  /**
   * Indicates that this process is running in a backend mode and serves as a remote development host.
   * It doesn't show the UI to the user directly, a separate process is responsible for this.
   */
  BACKEND("backend"),

  /**
   * Indicates that this process is running in a light mode - a minimalistic self-sufficient frontend IDE.
   */
  @ApiStatus.Internal
  LIGHT("light"),

  /**
   * Indicates that this process is running in a light mode with an established rd connection.
   * This is a temporary mode which appears during transition from the "light" to "frontend" mode.
   */
  @ApiStatus.Internal
  LIGHT_WITH_RD_CONNECTION("light_with_rd_connection"),

  /**
   * Indicates that this process is running in a language server mode.
   * This is a variant of [BACKEND] mode, but without the remote development connection.
   */
  @ApiStatus.Internal
  LANGUAGE_SERVER("language_server"),
  ;

  /**
   * Returns `true` in [LIGHT] and in [LIGHT_WITH_RD_CONNECTION].
   * It turns `false` once the process advances out of the light mode.
   */
  @get:ApiStatus.Internal
  val isLight: Boolean
    get() = this == LIGHT || this == LIGHT_WITH_RD_CONNECTION

  /**
   * Returns `true` when this process shows the UI to the user and does not compute smart features itself.
   * It covers [FRONTEND] and both light modes, and it is `false` in [MONOLITH].
   */
  @get:ApiStatus.Internal
  val isFrontendProcess: Boolean
    get() = this == FRONTEND || isLight

  @ApiStatus.Internal
  companion object {
    @JvmStatic
    fun findById(id: @NonNls String): ProductMode? = entries.firstOrNull { it.id == id }

    /** Returns the mode with the given [id], and fails when no mode has it. */
    @JvmStatic
    fun getById(id: @NonNls String): ProductMode =
      findById(id) ?: throw IllegalArgumentException("Unknown product mode '$id'")
  }
}
