// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.projectStructure.fixture.impl

import com.intellij.openapi.projectRoots.SdkType
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.DirectoryBuilder
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.ModuleBuilder
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.ProjectBuilder

internal open class DirectoryBuilderBase(
  override val path: String,
  protected val projectStructure: ProjectStructure,
) : DirectoryContainer, ProjectBuilder {
  private val _directories: MutableList<DirectoryBuilderImpl> = mutableListOf()
  private val _nestedModules: MutableList<ModuleBuilderImpl> = mutableListOf()
  private val _nestedSdks: MutableList<SdkBuilderImpl> = mutableListOf()
  private val _files: MutableList<FileBuilderImpl> = mutableListOf()

  override val directories: List<DirectoryBuilderImpl> get() = _directories
  override val modules: List<ModuleBuilderImpl> get() = _nestedModules
  override val files: List<FileBuilderImpl> get() = _files
  override val sdks: List<SdkBuilderImpl> get() = _nestedSdks

  override fun file(fileName: String, content: String) {
    _files.add(FileBuilderImplWithString(fileName, content))
  }

  override fun file(fileName: String, content: ByteArray) {
    _files.add(FileBuilderImplWithByteArray(fileName, content))
  }

  override fun dir(name: String, init: DirectoryBuilder.() -> Unit) {
    val directoryPath = subPath(name)
    val directory = DirectoryBuilderImpl(name, directoryPath, projectStructure)
    directory.init()
    _directories.add(directory)
  }

  override fun module(moduleName: String, init: ModuleBuilder.() -> Unit) {
    val nestedModulePath = subPath(moduleName)
    val nestedModule = ModuleBuilderImpl(moduleName, nestedModulePath, projectStructure).apply(init)
    _nestedModules.add(nestedModule)
    projectStructure.addModule(moduleName, nestedModule)
  }

  override fun sdk(
    name: String,
    type: SdkType,
    init: DirectoryBuilder.() -> Unit,
  ) {
    val sdkPath = subPath(name)
    val sdk = SdkBuilderImpl(name, type, sdkPath, projectStructure).apply(init)
    _nestedSdks.add(sdk)
    projectStructure.addSdk(name, sdk)
  }

  private fun subPath(name: String): String {
    if (path.isEmpty()) return name
    return "$path/$name"
  }
}