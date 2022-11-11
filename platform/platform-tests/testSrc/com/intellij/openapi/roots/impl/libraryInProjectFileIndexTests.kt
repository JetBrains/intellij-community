// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.CustomLibraryTableDescription
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.platform.testFramework.projectModel.library.MockCustomLibraryTableDescription
import com.intellij.testFramework.junit5.TestDisposable
import org.junit.jupiter.api.BeforeEach

class ProjectLibraryInProjectFileIndexTest : LibraryInProjectFileIndexTestCase() {
  override val libraryTable: LibraryTable
    get() = projectModel.projectLibraryTable

  override fun createLibrary(name: String, setup: (LibraryEx.ModifiableModelEx) -> Unit): LibraryEx {
    return projectModel.addProjectLevelLibrary(name, setup)
  }
}

class ApplicationLibraryInProjectFileIndexTest : LibraryInProjectFileIndexTestCase() {
  override val libraryTable: LibraryTable
    get() = LibraryTablesRegistrar.getInstance().libraryTable

  override fun createLibrary(name: String, setup: (LibraryEx.ModifiableModelEx) -> Unit): LibraryEx {
    return projectModel.addApplicationLevelLibrary(name, setup)
  }
}

class CustomLibraryInProjectFileIndexTest : LibraryInProjectFileIndexTestCase() {
  @TestDisposable
  lateinit var disposable: Disposable
  
  override val libraryTable: LibraryTable
    get() = LibraryTablesRegistrar.getInstance().getCustomLibraryTableByLevel("mock")!!

  @BeforeEach
  fun registerCustomLibraryTable() {
    ExtensionPointName.create<CustomLibraryTableDescription>("com.intellij.customLibraryTable").point
      .registerExtension(MockCustomLibraryTableDescription(), disposable)
  }

  override fun createLibrary(name: String, setup: (LibraryEx.ModifiableModelEx) -> Unit): LibraryEx {
    return projectModel.addLibrary(name, libraryTable, setup)
  }
}
