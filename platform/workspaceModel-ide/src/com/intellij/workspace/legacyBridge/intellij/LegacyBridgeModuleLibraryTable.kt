package com.intellij.workspace.legacyBridge.intellij

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.libraries.LibraryTable

interface LegacyBridgeModuleLibraryTable: LibraryTable {
  val module: Module
}