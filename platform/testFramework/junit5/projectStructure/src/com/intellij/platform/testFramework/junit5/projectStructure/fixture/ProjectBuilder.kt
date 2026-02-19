// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.projectStructure.fixture

import com.intellij.openapi.projectRoots.SdkType

interface ProjectBuilder : DirectoryBuilder {
  fun module(moduleName: String, init: ModuleBuilder.() -> Unit)

  /**
   * Beware that java files are not indexed when they are placed into an SDK!
   * Use class files instead.
   * You can produce it on demand with the help of [com.intellij.compiler.JavaInMemoryCompiler]
   */
  fun sdk(name: String, type: SdkType, init: DirectoryBuilder.() -> Unit)
}