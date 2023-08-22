// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.psi.reference

import com.intellij.openapi.vfs.VirtualFile

/**
 * Not that this descriptor might be somewhat resource-hungry
 * and should only be used locally inside a filtering function
 */
class EditorConfigVirtualFileDescriptor(val file: VirtualFile) {
  private val cachedChildMappings = mutableMapOf<VirtualFile, Int>()
  private val cachedParentMappings = mutableMapOf<VirtualFile, Int>()

  private fun distanceToChild(child: VirtualFile): Int {
    val cached = cachedChildMappings[child]
    if (cached != null) return cached
    val distance = calculateDistanceBetween(file, child)
    cachedChildMappings[child] = distance
    return distance
  }

  fun distanceToParent(parent: VirtualFile): Int {
    val cached = cachedParentMappings[parent]
    if (cached != null) return cached
    val distance = calculateDistanceBetween(parent, file)
    cachedParentMappings[parent] = distance
    return distance
  }

  fun isChildOf(parent: VirtualFile) = distanceToParent(parent) >= 0
  fun isParentOf(child: VirtualFile) = distanceToChild(child) >= 0
  fun isStrictParentOf(child: VirtualFile) = distanceToChild(child) > 0
  fun isStrictChildOf(parent: VirtualFile) = distanceToParent(parent) > 0

  private fun calculateDistanceBetween(parentFile: VirtualFile, initialFile: VirtualFile): Int {
    var result = 0
    val targetFolder = parentFile.parent
    var currentFolder = initialFile.parent

    while (currentFolder != null) {
      if (currentFolder == targetFolder) {
        return result
      }
      result += 1
      currentFolder = currentFolder.parent
    }

    return -1
  }
}
