// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction.impl

import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.metadata.impl.MetadataStorageBase
import com.intellij.platform.workspace.storage.metadata.model.ExtendableClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.OwnPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata

@OptIn(WorkspaceEntityInternalApi::class)
internal object MetadataStorageImpl: MetadataStorageBase() {
    override fun initializeMetadata() {
        val primitiveTypeStringNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "String")
        val primitiveTypeIntNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Int")
        
        var typeMetadata: StorageTypeMetadata
        
        typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.plugins.gradle.service.syncAction.impl.contributors.GradleContentRootSyncContributor\$GradleContentRootEntitySource", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "phase", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase", subclasses = listOf(FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleDynamicSyncPhase", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "modelFetchPhase", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase", subclasses = listOf(FinalClassMetadata.ClassMetadata(fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleBuildFinishedModelFetchPhase", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "order", valueType = primitiveTypeIntNotNullable, withDefault = false)), supertypes = listOf("com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase",
"com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase\$BuildFinished",
"com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase.BuildFinished",
"java.io.Serializable",
"kotlin.Comparable")),
FinalClassMetadata.ClassMetadata(fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleProjectLoadedModelFetchPhase", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "order", valueType = primitiveTypeIntNotNullable, withDefault = false)), supertypes = listOf("com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase",
"com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase\$ProjectLoaded",
"com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase.ProjectLoaded",
"java.io.Serializable",
"kotlin.Comparable"))), supertypes = listOf("java.io.Serializable",
"java.lang.Comparable",
"kotlin.Comparable"))), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("kotlin.Comparable",
"org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
"org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Dynamic",
"org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Dynamic")),
FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleStaticSyncPhase", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "order", valueType = primitiveTypeIntNotNullable, withDefault = false)), supertypes = listOf("kotlin.Comparable",
"org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
"org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Static",
"org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Static"))), supertypes = listOf("java.lang.Comparable",
"kotlin.Comparable"))), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "projectPath", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource",
"org.jetbrains.plugins.gradle.service.syncAction.GradleEntitySource",
"org.jetbrains.plugins.gradle.service.syncAction.impl.bridge.GradleBridgeEntitySource"))
        
        addMetadata(typeMetadata)
        
        typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.plugins.gradle.service.syncAction.impl.contributors.GradleProjectRootSyncContributor\$GradleProjectRootEntitySource", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "phase", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase", subclasses = listOf(FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleDynamicSyncPhase", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "modelFetchPhase", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase", subclasses = listOf(FinalClassMetadata.ClassMetadata(fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleBuildFinishedModelFetchPhase", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "order", valueType = primitiveTypeIntNotNullable, withDefault = false)), supertypes = listOf("com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase",
"com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase\$BuildFinished",
"com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase.BuildFinished",
"java.io.Serializable",
"kotlin.Comparable")),
FinalClassMetadata.ClassMetadata(fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleProjectLoadedModelFetchPhase", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "order", valueType = primitiveTypeIntNotNullable, withDefault = false)), supertypes = listOf("com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase",
"com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase\$ProjectLoaded",
"com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase.ProjectLoaded",
"java.io.Serializable",
"kotlin.Comparable"))), supertypes = listOf("java.io.Serializable",
"java.lang.Comparable",
"kotlin.Comparable"))), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("kotlin.Comparable",
"org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
"org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Dynamic",
"org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Dynamic")),
FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleStaticSyncPhase", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "order", valueType = primitiveTypeIntNotNullable, withDefault = false)), supertypes = listOf("kotlin.Comparable",
"org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
"org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Static",
"org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Static"))), supertypes = listOf("java.lang.Comparable",
"kotlin.Comparable"))), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "projectPath", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource",
"org.jetbrains.plugins.gradle.service.syncAction.GradleEntitySource",
"org.jetbrains.plugins.gradle.service.syncAction.impl.bridge.GradleBridgeEntitySource"))
        
        addMetadata(typeMetadata)
    }

    override fun initializeMetadataHash() {
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.EntitySource", metadataHash = -189553328)
        addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.GradleEntitySource", metadataHash = -565169080)
        addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.impl.bridge.GradleBridgeEntitySource", metadataHash = 1893250484)
        addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.impl.contributors.GradleContentRootSyncContributor\$GradleContentRootEntitySource", metadataHash = -1866518801)
        addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase", metadataHash = 6939851)
        addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Dynamic", metadataHash = 896961146)
        addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.GradleDynamicSyncPhase", metadataHash = 739426399)
        addMetadataHash(typeFqn = "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase", metadataHash = 1617061511)
        addMetadataHash(typeFqn = "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase\$BuildFinished", metadataHash = -384742708)
        addMetadataHash(typeFqn = "com.intellij.gradle.toolingExtension.modelAction.GradleBuildFinishedModelFetchPhase", metadataHash = 1680822981)
        addMetadataHash(typeFqn = "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase\$ProjectLoaded", metadataHash = -38038196)
        addMetadataHash(typeFqn = "com.intellij.gradle.toolingExtension.modelAction.GradleProjectLoadedModelFetchPhase", metadataHash = 1467460995)
        addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Static", metadataHash = -1225530548)
        addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.GradleStaticSyncPhase", metadataHash = 79572120)
        addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.impl.contributors.GradleProjectRootSyncContributor\$GradleProjectRootEntitySource", metadataHash = -530336721)
    }

}
