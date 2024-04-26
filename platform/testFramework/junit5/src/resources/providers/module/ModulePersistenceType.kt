// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.resources.providers.module

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.testFramework.junit5.resources.providers.PathInfo

/**
 * Derives module path from project and module name
 */
typealias GetModulePath = (project: Project, moduleName: ModuleName) -> PathInfo

sealed class ModulePersistenceType {
  /**
   * Module that doesn't sit on disk (recommended)
   */
  data object NonPersistent : ModulePersistenceType()

  /**
   * Module sits on path, provided by [pathGetter]
   */
  class Persistent(internal val pathGetter: GetModulePath = { p, n -> PathInfo(p.guessProjectDir()!!.toNioPath().resolve(n.name), closeFsOnExit = false) })
    : ModulePersistenceType()
}