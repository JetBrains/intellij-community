package com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.libraries.LibraryTable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ModuleLibraryTableBridge : LibraryTable {
  val module: Module
}