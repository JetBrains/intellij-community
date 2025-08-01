// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction.impl

import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.metadata.impl.MetadataStorageBase
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.OwnPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata

@OptIn(WorkspaceEntityInternalApi::class)
internal object MetadataStorageImpl: MetadataStorageBase() {
    override fun initializeMetadata() {
        val primitiveTypeStringNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "String")
        
        var typeMetadata: StorageTypeMetadata
        
        typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.plugins.gradle.service.syncContributor.GradleSourceRootSyncContributor\$GradleSourceRootEntitySource", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "projectPath", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource",
"org.jetbrains.plugins.gradle.service.syncAction.GradleEntitySource",
"org.jetbrains.plugins.gradle.service.syncContributor.bridge.GradleBridgeEntitySource"))
        
        addMetadata(typeMetadata)
        
        typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.plugins.gradle.service.syncContributor.GradleContentRootSyncContributor\$GradleContentRootEntitySource", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "projectPath", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource",
"org.jetbrains.plugins.gradle.service.syncAction.GradleEntitySource",
"org.jetbrains.plugins.gradle.service.syncContributor.bridge.GradleBridgeEntitySource"))
        
        addMetadata(typeMetadata)
        
        typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.plugins.gradle.service.syncContributor.GradleProjectRootEntitySource", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "projectPath", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource",
"org.jetbrains.plugins.gradle.service.syncAction.GradleEntitySource",
"org.jetbrains.plugins.gradle.service.syncContributor.bridge.GradleBridgeEntitySource"))
        
        addMetadata(typeMetadata)
    }

    override fun initializeMetadataHash() {
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.EntitySource", metadataHash = 1637225356)
        addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.GradleEntitySource", metadataHash = -565169080)
        addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncContributor.GradleContentRootSyncContributor\$GradleContentRootEntitySource", metadataHash = -59086515)
        addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncContributor.GradleProjectRootEntitySource", metadataHash = -1531457959)
        addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncContributor.GradleSourceRootSyncContributor\$GradleSourceRootEntitySource", metadataHash = 1491679397)
        addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncContributor.bridge.GradleBridgeEntitySource", metadataHash = -574106825)
    }

}
