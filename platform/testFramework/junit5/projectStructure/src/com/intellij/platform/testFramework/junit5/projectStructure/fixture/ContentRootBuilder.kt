// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.projectStructure.fixture

interface ContentRootBuilder : ProjectBuilder {
  fun sourceRoot(
    name: String,
    sourceRootId: String? = null,
    init: DirectoryBuilder.() -> Unit,
  )

  fun sharedSourceRoot(sourceRootId: String)
}