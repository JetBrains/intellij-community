// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.url

import com.intellij.workspaceModel.storage.WorkspaceEntity

interface VirtualFileUrlIndex {
  /**
   * Search [WorkspaceEntity] which contain required [VirtualFileUrl] and return mapping of entity to the property with VFU
   * @param fileUrl virtual file url which entity should contains
   * @return the sequence of pairs which contains entity and property name which contains required [VirtualFileUrl]
   */
  fun findEntitiesByUrl(fileUrl: VirtualFileUrl): Sequence<Pair<WorkspaceEntity, String>>
}

interface MutableVirtualFileUrlIndex : VirtualFileUrlIndex {
  /**
   * Manual add association of certain entity with VFU. In the usual case, appropriate property delegates should be
   * used e.g [VirtualFileUrlProperty]. But if it's needed to manually add an association for an entity to VFU it
   * can be done via this method.
   * @param entity which should be associated with this url
   * @param propertyName name of the property which contains this VFU
   * @param virtualFileUrl virtual file url which should be associated with the entity
   */
  fun index(entity: WorkspaceEntity, propertyName: String, virtualFileUrl: VirtualFileUrl? = null)
}