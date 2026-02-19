// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.script.k2.impl

import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.metadata.impl.MetadataStorageBase
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata

@OptIn(WorkspaceEntityInternalApi::class)
internal object MetadataStorageImpl: MetadataStorageBase() {
    override fun initializeMetadata() {
        
        var typeMetadata: StorageTypeMetadata
    }

    override fun initializeMetadataHash() {
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.EntitySource", metadataHash = 1637225356)
        addMetadataHash(typeFqn = "org.jetbrains.kotlin.idea.script.k2.MainKtsScriptConfigurationsSource\$KotlinCustomScriptModuleEntitySource", metadataHash = -176266983)
    }

}
