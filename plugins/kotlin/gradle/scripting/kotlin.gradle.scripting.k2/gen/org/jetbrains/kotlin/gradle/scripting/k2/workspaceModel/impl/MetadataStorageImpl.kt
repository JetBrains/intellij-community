// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.impl

import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.metadata.impl.MetadataStorageBase
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.OwnPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata

@OptIn(WorkspaceEntityInternalApi::class)
internal object MetadataStorageImpl : MetadataStorageBase() {
  override fun initializeMetadata() {
    val primitiveTypeStringNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "String")

    var typeMetadata: StorageTypeMetadata

    typeMetadata = FinalClassMetadata.ObjectMetadata(
      fqName = "org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.KotlinGradleScriptEntitySource", properties = listOf(
        OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl",
                            valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true,
                                                                                typeMetadata = FinalClassMetadata.KnownClass(
                                                                                  fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                            withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource"))

    addMetadata(typeMetadata)

    typeMetadata = FinalClassMetadata.ClassMetadata(
      fqName = "org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleScriptDefinitionEntityId", properties = listOf(
        OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "id", valueType = primitiveTypeStringNotNullable,
                            withDefault = false),
        OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName",
                            valueType = primitiveTypeStringNotNullable, withDefault = false)),
      supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleScriptDefinitionEntity",
                                  entityDataFqName = "org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.impl.GradleScriptDefinitionEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
                                                      "com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId"),
                                  properties = listOf(
                                    OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource",
                                                        valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                            typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                              fqName = "com.intellij.platform.workspace.storage.EntitySource")),
                                                        withDefault = false),
                                    OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "definitionId",
                                                        valueType = primitiveTypeStringNotNullable, withDefault = false),
                                    OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false,
                                                        name = "compilationConfiguration",
                                                        valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                            typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                              fqName = "org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptCompilationConfigurationEntity",
                                                                                                              properties = listOf(
                                                                                                                OwnPropertyMetadata(
                                                                                                                  isComputable = false,
                                                                                                                  isKey = false,
                                                                                                                  isOpen = false,
                                                                                                                  name = "data",
                                                                                                                  valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                    isNullable = false,
                                                                                                                    typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                      fqName = "kotlin.ByteArray")),
                                                                                                                  withDefault = false)),
                                                                                                              supertypes = listOf())),
                                                        withDefault = false),
                                    OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "hostConfiguration",
                                                        valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
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
                                                                                                                    typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                      fqName = "kotlin.ByteArray")),
                                                                                                                  withDefault = false)),
                                                                                                              supertypes = listOf())),
                                                        withDefault = false),
                                    OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false,
                                                        name = "evaluationConfiguration",
                                                        valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true,
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
                                                                                                                    typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                      fqName = "kotlin.ByteArray")),
                                                                                                                  withDefault = false)),
                                                                                                              supertypes = listOf())),
                                                        withDefault = false),
                                    OwnPropertyMetadata(isComputable = true, isKey = false, isOpen = false, name = "symbolicId",
                                                        valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                            typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                              fqName = "org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleScriptDefinitionEntityId",
                                                                                                              properties = listOf(
                                                                                                                OwnPropertyMetadata(
                                                                                                                  isComputable = false,
                                                                                                                  isKey = false,
                                                                                                                  isOpen = false,
                                                                                                                  name = "id",
                                                                                                                  valueType = primitiveTypeStringNotNullable,
                                                                                                                  withDefault = false),
                                                                                                                OwnPropertyMetadata(
                                                                                                                  isComputable = false,
                                                                                                                  isKey = false,
                                                                                                                  isOpen = false,
                                                                                                                  name = "presentableName",
                                                                                                                  valueType = primitiveTypeStringNotNullable,
                                                                                                                  withDefault = false)),
                                                                                                              supertypes = listOf(
                                                                                                                "com.intellij.platform.workspace.storage.SymbolicEntityId"))),
                                                        withDefault = false)), extProperties = listOf(), isAbstract = false)

    addMetadata(typeMetadata)
  }

  override fun initializeMetadataHash() {
    addMetadataHash(typeFqn = "org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleScriptDefinitionEntity",
                    metadataHash = 603888767)
    addMetadataHash(typeFqn = "org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptCompilationConfigurationEntity",
                    metadataHash = -1162660984)
    addMetadataHash(typeFqn = "org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptingHostConfigurationEntity",
                    metadataHash = -441841951)
    addMetadataHash(typeFqn = "org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptEvaluationConfigurationEntity",
                    metadataHash = -362496579)
    addMetadataHash(typeFqn = "org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleScriptDefinitionEntityId",
                    metadataHash = -1956757513)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.EntitySource", metadataHash = 1287228111)
    addMetadataHash(typeFqn = "org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.KotlinGradleScriptEntitySource",
                    metadataHash = -532489477)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.SymbolicEntityId", metadataHash = 1136338439)
  }

}
