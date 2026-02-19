// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.projectStructure.fixture

interface ModuleDependenciesBuilder {
  fun useSdk(name: String)

  fun module(name: String)

}