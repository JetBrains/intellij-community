package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.impl

import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.metadata.MetadataStorageBridge
import com.intellij.platform.workspace.storage.testEntities.entities.impl.MetadataStorageImpl

@OptIn(WorkspaceEntityInternalApi::class)
internal object MetadataStorageImpl: MetadataStorageBridge(MetadataStorageImpl)
