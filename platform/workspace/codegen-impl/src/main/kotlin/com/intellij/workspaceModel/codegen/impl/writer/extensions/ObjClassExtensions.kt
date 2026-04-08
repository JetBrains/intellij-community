// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer.extensions

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.OwnProperty
import com.intellij.workspaceModel.codegen.impl.writer.Internal
import com.intellij.workspaceModel.codegen.impl.writer.K1Deprecation
import com.intellij.workspaceModel.codegen.impl.writer.QualifiedName
import com.intellij.workspaceModel.codegen.impl.writer.WorkspaceEntity
import com.intellij.workspaceModel.codegen.impl.writer.WorkspaceEntityWithSymbolicId
import com.intellij.workspaceModel.codegen.impl.writer.fqn
import com.intellij.workspaceModel.codegen.impl.writer.symbolicIdFieldName

internal val ObjClass<*>.requiresCompatibility: Boolean
  get() = name in compatibilityEntities[module.name].orEmpty()

internal val ObjClass<*>.javaFullName: QualifiedName
  get() = fqn(module.name, name)

internal val ObjClass<*>.compatibleJavaBuilderName: String
  get() = if (requiresCompatibility) "$name.Builder" else defaultJavaBuilderName

internal val ObjClass<*>.defaultJavaBuilderName: String
  get() = "${name}Builder"

internal val ObjClass<*>.javaBuilderName: String
  get() = if (requiresCompatibility) compatibleJavaBuilderName else defaultJavaBuilderName

internal val ObjClass<*>.javaBuilderFqnName: QualifiedName
  get() = fqn(module.name, defaultJavaBuilderName)

internal val ObjClass<*>.javaImplName: String
  get() = "${name.replace(".", "")}Impl"

internal val ObjClass<*>.javaImplBuilderName
  get() = "${javaImplName}.Builder"


internal val ObjClass<*>.isAbstract: Boolean
  get() = openness == ObjClass.Openness.abstract


internal val ObjClass<*>.isStandardInterface: Boolean
  get() = name in setOf(WorkspaceEntity.simpleName, WorkspaceEntityWithSymbolicId.simpleName)

internal val ObjClass<*>.allSuperClasses: List<ObjClass<*>>
  get() = superTypes.filterIsInstance<ObjClass<*>>().flatMapTo(LinkedHashSet()) { it.allSuperClasses + listOf(it) }.toList()


internal val ObjClass<*>.allFieldsWithOwnExtensions: List<ObjProperty<*, *>>
  get() = allFieldsWithComputable + ownExtensions.filterNot { it.valueType.isEntityRef(it) }

internal val ObjClass<*>.allFields: List<OwnProperty<*, *>>
  get() {
    val fieldsByName = LinkedHashMap<String, OwnProperty<*, *>>()
    collectFields(this, fieldsByName, false)
    return fieldsByName.values.toList()
  }

internal val ObjClass<*>.allFieldsWithComputable: List<OwnProperty<*, *>>
  get() {
    val fieldsByName = LinkedHashMap<String, OwnProperty<*, *>>()
    collectFields(this, fieldsByName, true)
    return fieldsByName.values.toList()
  }

internal val ObjClass<*>.additionalAnnotations: List<String>
  get() {
    return annotations.mapNotNull {
      when (it.fqName) {
        Internal.decoded -> "@${Internal}"
        K1Deprecation.decoded -> "@${K1Deprecation}"
        else -> null
      }
    }
  }

private fun collectFields(objClass: ObjClass<*>, fieldsByName: MutableMap<String, OwnProperty<*, *>>, withComputable: Boolean) {
  for (superType in objClass.superTypes) {
    if (superType is ObjClass<*>) {
      collectFields(superType, fieldsByName, withComputable)
    }
  }
  for (field in objClass.fields) {
    if (withComputable
        || field.valueKind !is ObjProperty.ValueKind.Computable
        || field.name == symbolicIdFieldName // symbolicId is a computable field, but still we'd like to know its type
    ) {
      fieldsByName.remove(field.name)
      fieldsByName[field.name] = field
    }
  }
}


internal val ObjClass<*>.builderWithTypeParameter: Boolean
  get() = openness.extendable

private val compatibilityEntities = mapOf(
  "com.intellij.workspaceModel.test.api" to setOf("CompatibilityEntity"),
  "com.intellij.platform.workspace.jps.entities" to setOf(
    "ContentRootEntity",
    "CustomSourceRootPropertiesEntity",
    "ExcludeUrlEntity",
    "ExcludeUrlOrderEntity",
    "ExternalSystemModuleOptionsEntity",
    "FacetEntity",
    "FacetsOrderEntity",
    "LibraryEntity",
    "LibraryPropertiesEntity",
    "ModuleCustomImlDataEntity",
    "ModuleEntity",
    "ModuleGroupPathEntity",
    "ModuleSettingsFacetBridgeEntity",
    "ProjectSettingsEntity",
    "SdkEntity",
    "SourceRootEntity",
    "SourceRootOrderEntity",
    "TestModulePropertiesEntity"
  ),
  "com.intellij.java.workspace.entities" to setOf(
    "ArchivePackagingElementEntity",
    "ArtifactEntity",
    "ArtifactOutputPackagingElementEntity",
    "ArtifactPropertiesEntity",
    "ArtifactRootElementEntity",
    "ArtifactsOrderEntity",
    "CompositePackagingElementEntity",
    "CustomPackagingElementEntity",
    "DirectoryCopyPackagingElementEntity",
    "DirectoryPackagingElementEntity",
    "ExtractedDirectoryPackagingElementEntity",
    "FileCopyPackagingElementEntity",
    "FileOrDirectoryPackagingElementEntity",
    "JavaModuleSettingsEntity",
    "JavaProjectSettingsEntity",
    "JavaResourceRootPropertiesEntity",
    "JavaSourceRootPropertiesEntity",
    "LibraryFilesPackagingElementEntity",
    "ModuleOutputPackagingElementEntity",
    "ModuleSourcePackagingElementEntity",
    "ModuleTestOutputPackagingElementEntity",
    "PackagingElementEntity"
  ),
  "com.intellij.openapi.externalSystem.settings.workspaceModel" to setOf(
    "ExternalProjectsBuildClasspathEntity"
  ),
  "com.goide.vgo.project.workspaceModel.entities" to setOf(
    "VgoDependencyEntity", "VgoModuleCacheDirectoryEntity", "VgoStandaloneModuleEntity", "VgoWorkspaceEntity", "VgoWorkspaceModuleEntity"
  ),
  "org.jetbrains.kotlin.idea.workspaceModel" to setOf(
    "KotlinSettingsEntity"
  ))
