// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.url

import com.intellij.platform.workspace.storage.WorkspaceEntity

public interface VirtualFileUrlIndex {
  /**
   * Search [WorkspaceEntity] which contain required [VirtualFileUrl] and return mapping of entity to the property with VFU
   * @param fileUrl virtual file url which entity should contain
   * @return the sequence of entity which contains required [VirtualFileUrl]
   */
  public fun findEntitiesByUrl(fileUrl: VirtualFileUrl): Sequence<WorkspaceEntity>
}

public interface MutableVirtualFileUrlIndex : VirtualFileUrlIndex {
  /**
   * Manual add association of certain entity with VFU. In the usual case, appropriate property delegates should be
   * used e.g [VirtualFileUrlProperty]. But if it's needed to manually add an association for an entity to VFU it
   * can be done via this method.
   * @param entity which should be associated with this url
   * @param propertyName name of the property which contains this VFU
   * @param virtualFileUrl virtual file url which should be associated with the entity. Passing `null` as a value removes associated data from index
   */
  public fun index(entity: WorkspaceEntity.Builder<out WorkspaceEntity>, propertyName: String, virtualFileUrl: VirtualFileUrl?)
}