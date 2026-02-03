// 2700-2200 BCE fake copyright for test
// another line of fake copyright
package com.intellij.workspaceModel.test.api

// imports are in wrong order purposefully
import java.nio.file.Path
import com.intellij.platform.workspace.storage.WorkspaceEntity
import java.io.File
import com.intellij.another.module.ClassToImport

interface SimpleEntity : WorkspaceEntity {
  val version: Int
  val name: String
  val isSimple: Boolean
  val imported: ClassToImport
}

data class UnrelatedToWsm(val name: String, val file: File, val path: Path)
