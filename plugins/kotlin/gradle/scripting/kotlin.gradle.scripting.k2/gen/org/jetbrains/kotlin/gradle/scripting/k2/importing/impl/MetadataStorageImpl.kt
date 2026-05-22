// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.importing.impl

import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.metadata.impl.MetadataStorageBase
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.metadata.model.ExtendableClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.OwnPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata

@OptIn(WorkspaceEntityInternalApi::class)
internal object MetadataStorageImpl : MetadataStorageBase() {
    override fun initializeMetadata() {
        val primitiveTypeStringNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "String")
        val primitiveTypeIntNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Int")

        var typeMetadata: StorageTypeMetadata

        typeMetadata = FinalClassMetadata.ClassMetadata(
            fqName = "org.jetbrains.kotlin.gradle.scripting.k2.importing.GradleKotlinDslBaseScriptEntitySource",
            properties = listOf(
                OwnPropertyMetadata(
                    isComputable = false,
                    isKey = false,
                    isOpen = false,
                    name = "phase",
                    valueType = ValueTypeMetadata.SimpleType.CustomType(
                        isNullable = false, typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(
                            fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase", subclasses = listOf(
                                FinalClassMetadata.ClassMetadata(
                                    fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleDynamicSyncPhase",
                                    properties = listOf(
                                        OwnPropertyMetadata(
                                            isComputable = false,
                                            isKey = false,
                                            isOpen = false,
                                            name = "modelFetchPhase",
                                            valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                isNullable = false, typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(
                                                    fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase",
                                                    subclasses = listOf(
                                                        FinalClassMetadata.ClassMetadata(
                                                            fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleBuildFinishedModelFetchPhase",
                                                            properties = listOf(
                                                                OwnPropertyMetadata(
                                                                    isComputable = false,
                                                                    isKey = false,
                                                                    isOpen = false,
                                                                    name = "name",
                                                                    valueType = primitiveTypeStringNotNullable,
                                                                    withDefault = false
                                                                ),
                                                                OwnPropertyMetadata(
                                                                    isComputable = false,
                                                                    isKey = false,
                                                                    isOpen = false,
                                                                    name = "order",
                                                                    valueType = primitiveTypeIntNotNullable,
                                                                    withDefault = false
                                                                )
                                                            ),
                                                            supertypes = listOf(
                                                                "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase",
                                                                "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase\$BuildFinished",
                                                                "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase.BuildFinished",
                                                                "java.io.Serializable",
                                                                "kotlin.Comparable"
                                                            )
                                                        ),
                                                        FinalClassMetadata.ClassMetadata(
                                                            fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleProjectLoadedModelFetchPhase",
                                                            properties = listOf(
                                                                OwnPropertyMetadata(
                                                                    isComputable = false,
                                                                    isKey = false,
                                                                    isOpen = false,
                                                                    name = "name",
                                                                    valueType = primitiveTypeStringNotNullable,
                                                                    withDefault = false
                                                                ),
                                                                OwnPropertyMetadata(
                                                                    isComputable = false,
                                                                    isKey = false,
                                                                    isOpen = false,
                                                                    name = "order",
                                                                    valueType = primitiveTypeIntNotNullable,
                                                                    withDefault = false
                                                                )
                                                            ),
                                                            supertypes = listOf(
                                                                "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase",
                                                                "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase\$ProjectLoaded",
                                                                "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase.ProjectLoaded",
                                                                "java.io.Serializable",
                                                                "kotlin.Comparable"
                                                            )
                                                        ),
                                                        FinalClassMetadata.ObjectMetadata(
                                                            fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleBaseScriptModelFetchPhase",
                                                            properties = listOf(
                                                                OwnPropertyMetadata(
                                                                    isComputable = false,
                                                                    isKey = false,
                                                                    isOpen = false,
                                                                    name = "name",
                                                                    valueType = primitiveTypeStringNotNullable,
                                                                    withDefault = false
                                                                )
                                                            ),
                                                            supertypes = listOf(
                                                                "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase",
                                                                "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase\$BaseScript",
                                                                "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase.BaseScript",
                                                                "java.io.Serializable",
                                                                "kotlin.Comparable"
                                                            )
                                                        )
                                                    ),
                                                    supertypes = listOf("java.io.Serializable", "java.lang.Comparable", "kotlin.Comparable")
                                                )
                                            ),
                                            withDefault = false
                                        ),
                                        OwnPropertyMetadata(
                                            isComputable = false,
                                            isKey = false,
                                            isOpen = false,
                                            name = "name",
                                            valueType = primitiveTypeStringNotNullable,
                                            withDefault = false
                                        )
                                    ),
                                    supertypes = listOf(
                                        "kotlin.Comparable",
                                        "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                        "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Dynamic",
                                        "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Dynamic"
                                    )
                                ),
                                FinalClassMetadata.ClassMetadata(
                                    fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleDataServicesSyncPhase",
                                    properties = listOf(
                                        OwnPropertyMetadata(
                                            isComputable = false,
                                            isKey = false,
                                            isOpen = false,
                                            name = "name",
                                            valueType = primitiveTypeStringNotNullable,
                                            withDefault = false
                                        )
                                    ),
                                    supertypes = listOf(
                                        "kotlin.Comparable",
                                        "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                        "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$DataServices",
                                        "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.DataServices"
                                    )
                                ),
                                FinalClassMetadata.ClassMetadata(
                                    fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleStaticSyncPhase",
                                    properties = listOf(
                                        OwnPropertyMetadata(
                                            isComputable = false,
                                            isKey = false,
                                            isOpen = false,
                                            name = "name",
                                            valueType = primitiveTypeStringNotNullable,
                                            withDefault = false
                                        ),
                                        OwnPropertyMetadata(
                                            isComputable = false,
                                            isKey = false,
                                            isOpen = false,
                                            name = "order",
                                            valueType = primitiveTypeIntNotNullable,
                                            withDefault = false
                                        )
                                    ),
                                    supertypes = listOf(
                                        "kotlin.Comparable",
                                        "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                        "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Static",
                                        "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Static"
                                    )
                                )
                            ), supertypes = listOf("java.lang.Comparable", "kotlin.Comparable")
                        )
                    ),
                    withDefault = false
                ),
                OwnPropertyMetadata(
                    isComputable = false,
                    isKey = false,
                    isOpen = false,
                    name = "projectPath",
                    valueType = primitiveTypeStringNotNullable,
                    withDefault = false
                ),
                OwnPropertyMetadata(
                    isComputable = false,
                    isKey = false,
                    isOpen = false,
                    name = "virtualFileUrl",
                    valueType = ValueTypeMetadata.SimpleType.CustomType(
                        isNullable = true,
                        typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")
                    ),
                    withDefault = false
                )
            ),
            supertypes = listOf(
                "com.intellij.platform.workspace.storage.EntitySource",
                "org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleKotlinScriptEntitySource",
                "org.jetbrains.plugins.gradle.service.syncAction.GradleEntitySource"
            )
        )

        addMetadata(typeMetadata)

        typeMetadata = FinalClassMetadata.ClassMetadata(
            fqName = "org.jetbrains.kotlin.gradle.scripting.k2.importing.GradleKotlinDslScriptEntitySource",
            properties = listOf(
                OwnPropertyMetadata(
                    isComputable = false,
                    isKey = false,
                    isOpen = false,
                    name = "phase",
                    valueType = ValueTypeMetadata.SimpleType.CustomType(
                        isNullable = false, typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(
                            fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase", subclasses = listOf(
                                FinalClassMetadata.ClassMetadata(
                                    fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleDynamicSyncPhase",
                                    properties = listOf(
                                        OwnPropertyMetadata(
                                            isComputable = false,
                                            isKey = false,
                                            isOpen = false,
                                            name = "modelFetchPhase",
                                            valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                isNullable = false, typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(
                                                    fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase",
                                                    subclasses = listOf(
                                                        FinalClassMetadata.ClassMetadata(
                                                            fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleBuildFinishedModelFetchPhase",
                                                            properties = listOf(
                                                                OwnPropertyMetadata(
                                                                    isComputable = false,
                                                                    isKey = false,
                                                                    isOpen = false,
                                                                    name = "name",
                                                                    valueType = primitiveTypeStringNotNullable,
                                                                    withDefault = false
                                                                ),
                                                                OwnPropertyMetadata(
                                                                    isComputable = false,
                                                                    isKey = false,
                                                                    isOpen = false,
                                                                    name = "order",
                                                                    valueType = primitiveTypeIntNotNullable,
                                                                    withDefault = false
                                                                )
                                                            ),
                                                            supertypes = listOf(
                                                                "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase",
                                                                "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase\$BuildFinished",
                                                                "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase.BuildFinished",
                                                                "java.io.Serializable",
                                                                "kotlin.Comparable"
                                                            )
                                                        ),
                                                        FinalClassMetadata.ClassMetadata(
                                                            fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleProjectLoadedModelFetchPhase",
                                                            properties = listOf(
                                                                OwnPropertyMetadata(
                                                                    isComputable = false,
                                                                    isKey = false,
                                                                    isOpen = false,
                                                                    name = "name",
                                                                    valueType = primitiveTypeStringNotNullable,
                                                                    withDefault = false
                                                                ),
                                                                OwnPropertyMetadata(
                                                                    isComputable = false,
                                                                    isKey = false,
                                                                    isOpen = false,
                                                                    name = "order",
                                                                    valueType = primitiveTypeIntNotNullable,
                                                                    withDefault = false
                                                                )
                                                            ),
                                                            supertypes = listOf(
                                                                "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase",
                                                                "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase\$ProjectLoaded",
                                                                "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase.ProjectLoaded",
                                                                "java.io.Serializable",
                                                                "kotlin.Comparable"
                                                            )
                                                        ),
                                                        FinalClassMetadata.ObjectMetadata(
                                                            fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleBaseScriptModelFetchPhase",
                                                            properties = listOf(
                                                                OwnPropertyMetadata(
                                                                    isComputable = false,
                                                                    isKey = false,
                                                                    isOpen = false,
                                                                    name = "name",
                                                                    valueType = primitiveTypeStringNotNullable,
                                                                    withDefault = false
                                                                )
                                                            ),
                                                            supertypes = listOf(
                                                                "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase",
                                                                "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase\$BaseScript",
                                                                "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase.BaseScript",
                                                                "java.io.Serializable",
                                                                "kotlin.Comparable"
                                                            )
                                                        )
                                                    ),
                                                    supertypes = listOf("java.io.Serializable", "java.lang.Comparable", "kotlin.Comparable")
                                                )
                                            ),
                                            withDefault = false
                                        ),
                                        OwnPropertyMetadata(
                                            isComputable = false,
                                            isKey = false,
                                            isOpen = false,
                                            name = "name",
                                            valueType = primitiveTypeStringNotNullable,
                                            withDefault = false
                                        )
                                    ),
                                    supertypes = listOf(
                                        "kotlin.Comparable",
                                        "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                        "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Dynamic",
                                        "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Dynamic"
                                    )
                                ),
                                FinalClassMetadata.ClassMetadata(
                                    fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleDataServicesSyncPhase",
                                    properties = listOf(
                                        OwnPropertyMetadata(
                                            isComputable = false,
                                            isKey = false,
                                            isOpen = false,
                                            name = "name",
                                            valueType = primitiveTypeStringNotNullable,
                                            withDefault = false
                                        )
                                    ),
                                    supertypes = listOf(
                                        "kotlin.Comparable",
                                        "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                        "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$DataServices",
                                        "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.DataServices"
                                    )
                                ),
                                FinalClassMetadata.ClassMetadata(
                                    fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleStaticSyncPhase",
                                    properties = listOf(
                                        OwnPropertyMetadata(
                                            isComputable = false,
                                            isKey = false,
                                            isOpen = false,
                                            name = "name",
                                            valueType = primitiveTypeStringNotNullable,
                                            withDefault = false
                                        ),
                                        OwnPropertyMetadata(
                                            isComputable = false,
                                            isKey = false,
                                            isOpen = false,
                                            name = "order",
                                            valueType = primitiveTypeIntNotNullable,
                                            withDefault = false
                                        )
                                    ),
                                    supertypes = listOf(
                                        "kotlin.Comparable",
                                        "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                        "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Static",
                                        "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Static"
                                    )
                                )
                            ), supertypes = listOf("java.lang.Comparable", "kotlin.Comparable")
                        )
                    ),
                    withDefault = false
                ),
                OwnPropertyMetadata(
                    isComputable = false,
                    isKey = false,
                    isOpen = false,
                    name = "projectPath",
                    valueType = primitiveTypeStringNotNullable,
                    withDefault = false
                ),
                OwnPropertyMetadata(
                    isComputable = false,
                    isKey = false,
                    isOpen = false,
                    name = "virtualFileUrl",
                    valueType = ValueTypeMetadata.SimpleType.CustomType(
                        isNullable = true,
                        typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")
                    ),
                    withDefault = false
                )
            ),
            supertypes = listOf(
                "com.intellij.platform.workspace.storage.EntitySource",
                "org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleKotlinScriptEntitySource",
                "org.jetbrains.plugins.gradle.service.syncAction.GradleEntitySource"
            )
        )

        addMetadata(typeMetadata)

        typeMetadata = FinalClassMetadata.ClassMetadata(
            fqName = "org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleScriptDefinitionEntityId",
            properties = listOf(
                OwnPropertyMetadata(
                    isComputable = false,
                    isKey = false,
                    isOpen = false,
                    name = "id",
                    valueType = primitiveTypeStringNotNullable,
                    withDefault = false
                ),
                OwnPropertyMetadata(
                    isComputable = false,
                    isKey = false,
                    isOpen = false,
                    name = "presentableName",
                    valueType = primitiveTypeStringNotNullable,
                    withDefault = false
                )
            ),
            supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId")
        )

        addMetadata(typeMetadata)

        typeMetadata = EntityMetadata(
            fqName = "org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleScriptDefinitionEntity",
            entityDataFqName = "org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.impl.GradleScriptDefinitionEntityData",
            supertypes = listOf(
                "com.intellij.platform.workspace.storage.WorkspaceEntity",
                "com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId"
            ),
            properties = listOf(
                OwnPropertyMetadata(
                    isComputable = false,
                    isKey = false,
                    isOpen = false,
                    name = "entitySource",
                    valueType = ValueTypeMetadata.SimpleType.CustomType(
                        isNullable = false,
                        typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")
                    ),
                    withDefault = false
                ),
                OwnPropertyMetadata(
                    isComputable = false,
                    isKey = false,
                    isOpen = false,
                    name = "definitionId",
                    valueType = primitiveTypeStringNotNullable,
                    withDefault = false
                ),
                OwnPropertyMetadata(
                    isComputable = false,
                    isKey = false,
                    isOpen = false,
                    name = "compilationConfigurationData",
                    valueType = ValueTypeMetadata.SimpleType.CustomType(
                        isNullable = false,
                        typeMetadata = FinalClassMetadata.ClassMetadata(
                            fqName = "org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptCompilationConfigurationData",
                            properties = listOf(
                                OwnPropertyMetadata(
                                    isComputable = false,
                                    isKey = false,
                                    isOpen = false,
                                    name = "data",
                                    valueType = ValueTypeMetadata.SimpleType.CustomType(
                                        isNullable = false,
                                        typeMetadata = FinalClassMetadata.KnownClass(fqName = "kotlin.ByteArray")
                                    ),
                                    withDefault = false
                                )
                            ),
                            supertypes = listOf()
                        )
                    ),
                    withDefault = false
                ),
                OwnPropertyMetadata(
                    isComputable = false,
                    isKey = false,
                    isOpen = false,
                    name = "hostConfiguration",
                    valueType = ValueTypeMetadata.SimpleType.CustomType(
                        isNullable = false,
                        typeMetadata = FinalClassMetadata.ClassMetadata(
                            fqName = "org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptingHostConfigurationEntity",
                            properties = listOf(
                                OwnPropertyMetadata(
                                    isComputable = false,
                                    isKey = false,
                                    isOpen = false,
                                    name = "data",
                                    valueType = ValueTypeMetadata.SimpleType.CustomType(
                                        isNullable = false,
                                        typeMetadata = FinalClassMetadata.KnownClass(fqName = "kotlin.ByteArray")
                                    ),
                                    withDefault = false
                                )
                            ),
                            supertypes = listOf()
                        )
                    ),
                    withDefault = false
                ),
                OwnPropertyMetadata(
                    isComputable = false,
                    isKey = false,
                    isOpen = false,
                    name = "evaluationConfiguration",
                    valueType = ValueTypeMetadata.SimpleType.CustomType(
                        isNullable = true,
                        typeMetadata = FinalClassMetadata.ClassMetadata(
                            fqName = "org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptEvaluationConfigurationEntity",
                            properties = listOf(
                                OwnPropertyMetadata(
                                    isComputable = false,
                                    isKey = false,
                                    isOpen = false,
                                    name = "data",
                                    valueType = ValueTypeMetadata.SimpleType.CustomType(
                                        isNullable = false,
                                        typeMetadata = FinalClassMetadata.KnownClass(fqName = "kotlin.ByteArray")
                                    ),
                                    withDefault = false
                                )
                            ),
                            supertypes = listOf()
                        )
                    ),
                    withDefault = false
                ),
                OwnPropertyMetadata(
                    isComputable = true,
                    isKey = false,
                    isOpen = false,
                    name = "symbolicId",
                    valueType = ValueTypeMetadata.SimpleType.CustomType(
                        isNullable = false,
                        typeMetadata = FinalClassMetadata.ClassMetadata(
                            fqName = "org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleScriptDefinitionEntityId",
                            properties = listOf(
                                OwnPropertyMetadata(
                                    isComputable = false,
                                    isKey = false,
                                    isOpen = false,
                                    name = "id",
                                    valueType = primitiveTypeStringNotNullable,
                                    withDefault = false
                                ),
                                OwnPropertyMetadata(
                                    isComputable = false,
                                    isKey = false,
                                    isOpen = false,
                                    name = "presentableName",
                                    valueType = primitiveTypeStringNotNullable,
                                    withDefault = false
                                )
                            ),
                            supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId")
                        )
                    ),
                    withDefault = false
                )
            ),
            extProperties = listOf(),
            isAbstract = false
        )

        addMetadata(typeMetadata)
    }

    override fun initializeMetadataHash() {
        addMetadataHash(
            typeFqn = "org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleScriptDefinitionEntity",
            metadataHash = 774000416
        )
        addMetadataHash(
            typeFqn = "org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptCompilationConfigurationData",
            metadataHash = 2014662927
        )
        addMetadataHash(
            typeFqn = "org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptingHostConfigurationEntity",
            metadataHash = -441841951
        )
        addMetadataHash(
            typeFqn = "org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptEvaluationConfigurationEntity",
            metadataHash = -362496579
        )
        addMetadataHash(
            typeFqn = "org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleScriptDefinitionEntityId",
            metadataHash = -922050183
        )
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.EntitySource", metadataHash = 1637225356)
        addMetadataHash(
            typeFqn = "org.jetbrains.kotlin.gradle.scripting.k2.importing.GradleKotlinDslBaseScriptEntitySource",
            metadataHash = -198136545
        )
        addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase", metadataHash = 1187401489)
        addMetadataHash(
            typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$DataServices",
            metadataHash = -1256475695
        )
        addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.GradleDataServicesSyncPhase", metadataHash = -1556399787)
        addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Dynamic", metadataHash = -1614528557)
        addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.GradleDynamicSyncPhase", metadataHash = -1010561852)
        addMetadataHash(typeFqn = "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase", metadataHash = 1808583480)
        addMetadataHash(
            typeFqn = "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase\$BaseScript",
            metadataHash = 308611264
        )
        addMetadataHash(
            typeFqn = "com.intellij.gradle.toolingExtension.modelAction.GradleBaseScriptModelFetchPhase",
            metadataHash = -1293524369
        )
        addMetadataHash(
            typeFqn = "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase\$BuildFinished",
            metadataHash = 64803236
        )
        addMetadataHash(
            typeFqn = "com.intellij.gradle.toolingExtension.modelAction.GradleBuildFinishedModelFetchPhase",
            metadataHash = -651389069
        )
        addMetadataHash(
            typeFqn = "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase\$ProjectLoaded",
            metadataHash = 1151381984
        )
        addMetadataHash(
            typeFqn = "com.intellij.gradle.toolingExtension.modelAction.GradleProjectLoadedModelFetchPhase",
            metadataHash = -1086434639
        )
        addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Static", metadataHash = -1839677424)
        addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.GradleStaticSyncPhase", metadataHash = -181947250)
        addMetadataHash(
            typeFqn = "org.jetbrains.kotlin.gradle.scripting.k2.importing.GradleKotlinDslScriptEntitySource",
            metadataHash = 1563042096
        )
        addMetadataHash(
            typeFqn = "org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleKotlinScriptEntitySource",
            metadataHash = 1611667309
        )
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.SymbolicEntityId", metadataHash = -769470273)
    }
}
