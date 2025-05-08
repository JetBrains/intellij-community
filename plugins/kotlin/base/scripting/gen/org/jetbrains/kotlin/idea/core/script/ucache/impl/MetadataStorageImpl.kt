package org.jetbrains.kotlin.idea.core.script.ucache.impl

import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.metadata.MetadataStorageBridge
import org.jetbrains.kotlin.idea.core.script.impl.MetadataStorageImpl

@OptIn(WorkspaceEntityInternalApi::class)
internal object MetadataStorageImpl: MetadataStorageBridge(MetadataStorageImpl)
