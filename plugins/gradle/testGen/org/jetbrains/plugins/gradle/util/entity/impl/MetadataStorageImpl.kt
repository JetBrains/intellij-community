// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util.entity.impl

import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.metadata.impl.MetadataStorageBase
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
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
        
        typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.plugins.gradle.util.entity.GradleTestBridgeEntitySource", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "phase", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase", subclasses = listOf(FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleDynamicSyncPhase", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "modelFetchPhase", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase", subclasses = listOf(FinalClassMetadata.ClassMetadata(fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleBuildFinishedModelFetchPhase", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),
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
        
        typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.plugins.gradle.util.entity.GradleTestEntitySource", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "phase", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase", subclasses = listOf(FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleDynamicSyncPhase", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "modelFetchPhase", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase", subclasses = listOf(FinalClassMetadata.ClassMetadata(fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleBuildFinishedModelFetchPhase", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),
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
"org.jetbrains.plugins.gradle.service.syncAction.GradleEntitySource"))
        
        addMetadata(typeMetadata)
        
        typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.plugins.gradle.util.entity.GradleTestEntityId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "phase", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase", subclasses = listOf(FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleDynamicSyncPhase", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "modelFetchPhase", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase", subclasses = listOf(FinalClassMetadata.ClassMetadata(fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleBuildFinishedModelFetchPhase", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),
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
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))
        
        addMetadata(typeMetadata)
        
        typeMetadata = EntityMetadata(fqName = "org.jetbrains.plugins.gradle.util.entity.GradleTestEntity", entityDataFqName = "org.jetbrains.plugins.gradle.util.entity.impl.GradleTestEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
"com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "phase", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase", subclasses = listOf(FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleDynamicSyncPhase", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "modelFetchPhase", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase", subclasses = listOf(FinalClassMetadata.ClassMetadata(fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleBuildFinishedModelFetchPhase", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),
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
OwnPropertyMetadata(isComputable = true, isKey = false, isOpen = false, name = "symbolicId", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.plugins.gradle.util.entity.GradleTestEntityId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "phase", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase", subclasses = listOf(FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleDynamicSyncPhase", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "modelFetchPhase", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase", subclasses = listOf(FinalClassMetadata.ClassMetadata(fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleBuildFinishedModelFetchPhase", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),
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
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))), withDefault = false)), extProperties = listOf(), isAbstract = false)
        
        addMetadata(typeMetadata)
    }

    override fun initializeMetadataHash() {
        addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.util.entity.GradleTestEntity", metadataHash = -886007182)
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
        addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.util.entity.GradleTestEntityId", metadataHash = 1637153649)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.EntitySource", metadataHash = 1683412587)
        addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.util.entity.GradleTestBridgeEntitySource", metadataHash = -920579396)
        addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.util.entity.GradleTestEntitySource", metadataHash = -24490201)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.SymbolicEntityId", metadataHash = 932153735)
    }

}
