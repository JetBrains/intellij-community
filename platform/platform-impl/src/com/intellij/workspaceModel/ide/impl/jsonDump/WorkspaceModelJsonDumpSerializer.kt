// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jsonDump

import com.intellij.platform.workspace.jps.entities.InheritedSdkDependency
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.ModuleDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleSourceDependency
import com.intellij.platform.workspace.jps.entities.SdkDependency
import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.getChildrenConnections
import com.intellij.platform.workspace.storage.impl.AbstractEntityStorage
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.findWorkspaceEntity
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.ExtendableClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.OwnPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class WorkspaceModelJsonDumpSerializer {
  // private val encounteredClasses: MutableMap<String, StorageClassMetadata> = mutableMapOf()

  private fun JsonObjectBuilder.putListOrSet(propertyName: String, valueList: Collection<Any>, valueTypeMetadata: ValueTypeMetadata) {
    put("${propertyName}_Count", valueList.size)
    if (valueList.isEmpty()) {
      putJsonArray(propertyName) {}
      return
    }
    when (valueTypeMetadata) {
      is ValueTypeMetadata.EntityReference -> {
        putJsonArray(propertyName) {
          add("Unexpected entity reference list")
        }
      }
      is ValueTypeMetadata.SimpleType.PrimitiveType -> {
        putJsonArray(propertyName) {
          for (value in valueList) {
            add(value.toString())
          }
        }
      }
      is ValueTypeMetadata.SimpleType.CustomType -> {
        when (val customTypeMetadata = valueTypeMetadata.typeMetadata) {
          is FinalClassMetadata -> {
            putJsonArray(propertyName) {
              for (value in valueList) {
                val asJson = finalClassAsJson(value, customTypeMetadata)
                asJson?.let { add(it) }
              }
            }
          }
          is ExtendableClassMetadata.AbstractClassMetadata -> {
            putJsonArray(propertyName) {
              for (value in valueList) {
                val thisMetadata = value::class.qualifiedName?.let { classFqn ->
                  customTypeMetadata.subclasses.find { it.fqName.replace("$", ".") == classFqn }
                }
                if (thisMetadata != null) {
                  val asJson = finalClassAsJson(value, thisMetadata)
                  asJson?.let { add(it) }
                }
                else {
                  add("Could not serialize abstract class ${customTypeMetadata.fqName}")
                }
              }
            }
          }
        }
      }
      is ValueTypeMetadata.ParameterizedType -> {
        putJsonArray(propertyName) {
          add("Nested Lists/Sets are not supported")
        }
      }
    }
  }

  private fun finalClassAsJson(value: Any, typeMetadata: FinalClassMetadata): JsonObject? {
    if (typeMetadata.fqName == VirtualFileUrl::class.qualifiedName) {
      return buildJsonObject {
        put("url", (value as VirtualFileUrl).url)
      }
    }
    if (typeMetadata.fqName == EntitySource::class.qualifiedName) {
      return buildJsonObject {
        put("entitySourceFqn", value::class.qualifiedName)
        val vfu = (value as? EntitySource)?.virtualFileUrl?.url
        vfu?.let { put("virtualFileUrl", it) }
      }
    }
    when (typeMetadata) {
      is FinalClassMetadata.KnownClass -> {
        // TODO: metadata exists somewhere in entity metadata
        //if (typeMetadata.fqName in encounteredClasses) {
        //} else {
        return buildJsonObject {
          put("unsupported", typeMetadata.fqName)
        }
      }
      is FinalClassMetadata.ClassMetadata, is FinalClassMetadata.ObjectMetadata -> {
        if (typeMetadata.isSymbolicId()) {
          value as SymbolicEntityId<*>
          return buildJsonObject {
            put("fqn", typeMetadata.fqName)
            put("presentableName", value.presentableName)
          }
        }
        return buildJsonObject {
          put("fqn", typeMetadata.fqName)
          for (property in typeMetadata.properties) {
            putProperty(value, property)
          }
        }
      }
      is FinalClassMetadata.EnumClassMetadata -> {
        return buildJsonObject {
          put("fqn", typeMetadata.fqName)
          put("Enum value:", value.toString())
        }
      }
    }
  }

  private fun JsonObjectBuilder.putProperty(from: Any, propertyMeta: OwnPropertyMetadata) {
    if (propertyMeta.isComputable) return

    val propertyName = propertyMeta.name
    val propertyType = propertyMeta.valueType
    val propertyValue = from.getPropertyValue(propertyName)

    if (propertyValue == null) {
      put(propertyName, "null")
      return
    }

    when (propertyType) {
      is ValueTypeMetadata.EntityReference -> return
      is ValueTypeMetadata.SimpleType.PrimitiveType -> put(propertyName, propertyValue.toString())
      is ValueTypeMetadata.SimpleType.CustomType -> {
        when (val customTypeMetadata = propertyType.typeMetadata) {
          is FinalClassMetadata -> {
            val asJson = finalClassAsJson(propertyValue, customTypeMetadata)
            asJson?.let { put(propertyName, it) }
          }
          is ExtendableClassMetadata.AbstractClassMetadata -> {
            val thisMetadata = propertyValue::class.qualifiedName?.let { classFqn ->
              customTypeMetadata.subclasses.find { it.fqName.replace("$", ".") == classFqn }
            }
            if (thisMetadata != null) {
              val asJson = finalClassAsJson(propertyValue, thisMetadata)
              asJson?.let { put(propertyName, it) }
            }
            else {
              put(propertyName, "Could not serialize abstract class ${customTypeMetadata.fqName}")
            }
          }
        }
      }
      is ValueTypeMetadata.ParameterizedType -> {
        val valueTypeMetadata = propertyType.genericParameterForList()
        if (valueTypeMetadata == null) {
          // TODO: serialize maps
          put(propertyName, "Serializing Map is not supported")
          return
        }

        @Suppress("UNCHECKED_CAST")
        val valueList = propertyValue as Collection<Any>
        putListOrSet(propertyName, valueList, valueTypeMetadata)
      }
    }
  }

  fun entityAsJson(entity: WorkspaceEntity): JsonObject {
    if (entity is ModuleEntity) {
      return moduleEntityAsJson(entity)
    }
    val entityBase = entity as WorkspaceEntityBase
    return entityBaseAsJson(entityBase)
  }

  private fun moduleEntityAsJson(entity: ModuleEntity): JsonObject {
    entity as WorkspaceEntityBase
    val entityMetadata = entity.getData().getMetadata()

    return buildJsonObject {
      put("fqn", entityMetadata.fqName)
      
      for (property in entityMetadata.properties) {
        if (property.name != "dependencies")
          putProperty(entity, property)
      }

      put("dependencies_Count", entity.dependencies.size)
      putJsonArray("dependencies") {
        for (dependency in entity.dependencies) {
          when (dependency) {
            InheritedSdkDependency -> add("InheritedSdkDependency")
            ModuleSourceDependency -> add("ModuleSourceDependency")
            is LibraryDependency -> add("Library(${dependency.library.presentableName}, ${dependency.exported}, ${dependency.scope})")
            is ModuleDependency -> add("Module(${dependency.module.presentableName}, ${dependency.exported}, ${dependency.scope}, ${dependency.productionOnTest})")
            is SdkDependency -> add("Sdk(${dependency.sdk.presentableName})")
          }
        }
      }

      putEntityChildren(entity)
    }
  }

  private fun entityBaseAsJson(entity: WorkspaceEntityBase): JsonObject {
    val entityMetadata = entity.getData().getMetadata()

    return buildJsonObject {
      put("fqn", entityMetadata.fqName)

      for (property in entityMetadata.properties) {
        putProperty(entity, property)
      }

      putEntityChildren(entity)
    }
  }

  @OptIn(WorkspaceEntityInternalApi::class, EntityStorageInstrumentationApi::class)
  private fun JsonObjectBuilder.putEntityChildren(entity: WorkspaceEntityBase) {
    val snapshot = entity.snapshot as AbstractEntityStorage
    val childrenConnections = getChildrenConnections(entity, snapshot).sortedBy { it.childClass }
    for (connectionId in childrenConnections) {
      val childClassName = connectionId.childClass.findWorkspaceEntity().simpleName
      when (connectionId.connectionType) {
        ConnectionId.ConnectionType.ONE_TO_ONE, ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE -> {
          val jsonName = entityChildReferenceJsonName(childClassName)
          val child = snapshot.instrumentation.getOneChild(connectionId, entity) as? WorkspaceEntityBase
          if (child != null) {
            val childAsJson = entityBaseAsJson(child)
            put(jsonName, childAsJson)
          }
          else {
            put(jsonName, "null")
          }
        }
        ConnectionId.ConnectionType.ONE_TO_MANY, ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY -> {
          val jsonName = entityChildReferenceJsonName(childClassName, true)

          @Suppress("UNCHECKED_CAST")
          val children = snapshot.instrumentation.getManyChildren(connectionId, entity).toList() as List<WorkspaceEntityBase>
          put("${jsonName}_Count", children.size)
          putJsonArray(jsonName) {
            for (child in children) {
              val childAsJson = entityBaseAsJson(child)
              add(childAsJson)
            }
          }
        }
      }
    }
  }
}
