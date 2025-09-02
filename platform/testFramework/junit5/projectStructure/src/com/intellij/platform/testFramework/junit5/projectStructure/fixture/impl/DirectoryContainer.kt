// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.projectStructure.fixture.impl

internal interface DirectoryContainer {
  val path: String

  val directories: List<DirectoryBuilderImpl>

  val files: List<FileBuilderImpl>

  val modules: List<ModuleBuilderImpl>

  val sdks: List<SdkBuilderImpl>
}