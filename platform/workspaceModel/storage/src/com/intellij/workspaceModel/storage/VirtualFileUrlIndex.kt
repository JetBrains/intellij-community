// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

interface VirtualFileUrlIndex {
  /**
   * Search [WorkspaceEntity] which contain required [VirtualFileUrl] and return mapping of entity to the property with VFU
   * @param fileUrl virtual file url which entity should contains
   * @return the sequence of pairs which contains entity and property name which contains required [VirtualFileUrl]
   */
  fun findEntitiesByUrl(fileUrl: VirtualFileUrl): Sequence<Pair<WorkspaceEntity, String>>
}