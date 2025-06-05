// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.configurations.impl

import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.metadata.impl.MetadataStorageBase
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.OwnPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata

@OptIn(WorkspaceEntityInternalApi::class)
internal object MetadataStorageImpl: MetadataStorageBase() {
    override fun initializeMetadata() {
        
        var typeMetadata: StorageTypeMetadata
        
        typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.kotlin.idea.core.script.k2.configurations.MainKtsScriptConfigurationProvider\$MainKtsKotlinScriptEntitySource", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource",
"org.jetbrains.kotlin.idea.core.script.KotlinScriptEntitySource"))
        
        addMetadata(typeMetadata)
        
        typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.kotlin.idea.core.script.k2.configurations.DefaultScriptConfigurationHandler\$DefaultScriptEntitySource", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource",
"org.jetbrains.kotlin.idea.core.script.KotlinScriptEntitySource"))
        
        addMetadata(typeMetadata)
    }

    override fun initializeMetadataHash() {
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.EntitySource", metadataHash = -1477682368)
        addMetadataHash(typeFqn = "org.jetbrains.kotlin.idea.core.script.k2.configurations.DefaultScriptConfigurationHandler\$DefaultScriptEntitySource", metadataHash = 268010847)
        addMetadataHash(typeFqn = "org.jetbrains.kotlin.idea.core.script.k2.configurations.MainKtsScriptConfigurationProvider\$MainKtsKotlinScriptEntitySource", metadataHash = 478257645)
    }

}
