// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.projectModel.library

import com.intellij.openapi.roots.libraries.CustomLibraryTableDescription
import com.intellij.openapi.roots.libraries.LibraryTablePresentation

@Suppress("HardCodedStringLiteral")
class MockCustomLibraryTableDescription : CustomLibraryTableDescription {
  override fun getPresentation(): LibraryTablePresentation {
    return object : LibraryTablePresentation() {
      override fun getLibraryTableEditorTitle(): String = "Mock"
      override fun getDescription(): String = "Mock"
      override fun getDisplayName(plural: Boolean): String = "Mock"
    }
  }

  override fun getTableLevel(): String {
    return "mock"
  }
}

class NewMockCustomLibraryTableDescription : CustomLibraryTableDescription {
  override fun getPresentation(): LibraryTablePresentation {
    return object : LibraryTablePresentation() {
      override fun getLibraryTableEditorTitle(): String = "NewMock"
      override fun getDescription(): String = "NewMock"
      override fun getDisplayName(plural: Boolean): String = "NewMock"
    }
  }

  override fun getTableLevel(): String {
    return "new_mock"
  }
}