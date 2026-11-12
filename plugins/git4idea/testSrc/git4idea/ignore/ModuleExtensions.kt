// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ignore

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil

fun Module.addExclude(outDir: VirtualFile) {
  ModuleRootModificationUtil.updateModel(this) { model ->
    runReadAction {
      findContentEntry(model, outDir)?.addExcludeFolder(outDir)
    }
  }
}

fun Module.addSourceFolder(sourceDir: VirtualFile, isTestSource: Boolean = false) {
  ModuleRootModificationUtil.updateModel(this) { model ->
    runReadAction {
      findContentEntry(model, sourceDir)?.addSourceFolder(sourceDir, isTestSource)
    }
  }
}

fun Module.addContentRoot(vDir: VirtualFile): ContentEntry? {
  ModuleRootModificationUtil.updateModel(this) { model -> model.addContentEntry(vDir) }
  for (entry in ModuleRootManager.getInstance(this).contentEntries) {
    if (Comparing.equal(entry.file, vDir)) {
      return entry
    }
  }
  return null
}

private fun findContentEntry(model: ModifiableRootModel, file: VirtualFile): ContentEntry? {
  return ContainerUtil.find(model.contentEntries) { contentEntry ->
    val entryRoot = contentEntry.file
    entryRoot != null && VfsUtilCore.isAncestor(entryRoot, file, false)
  }
}
