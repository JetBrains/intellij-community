package com.intellij.platform.workspace.jps.impl

import com.intellij.java.workspace.entities.impl.MetadataStorageImpl
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.metadata.MetadataStorageBridge

@OptIn(WorkspaceEntityInternalApi::class)
internal object MetadataStorageImpl: MetadataStorageBridge(MetadataStorageImpl)
