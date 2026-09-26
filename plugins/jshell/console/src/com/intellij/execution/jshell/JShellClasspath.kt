// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.jshell

import com.intellij.openapi.application.PathManager
import org.jetbrains.annotations.ApiStatus

/**
 * Finds the classpath entry that the forked JShell process needs.
 */
@ApiStatus.Internal
object JShellClasspath {
  /**
   * The main class of the forked JShell process. The `jshell-frontend` library holds it.
   */
  const val FRONTEND_MAIN_CLASS: String = "com.intellij.execution.jshell.frontend.Main"

  /**
   * Returns the classpath entry that holds [FRONTEND_MAIN_CLASS], or `null` when the class is not on the classpath.
   * The lookup uses the class name, so a change of the jar name or of the plugin layout cannot break it.
   */
  @JvmStatic
  fun findFrontendJar(): String? {
    return PathManager.getResourceRoot(javaClass.classLoader, FRONTEND_MAIN_CLASS.replace('.', '/') + ".class")
  }
}
