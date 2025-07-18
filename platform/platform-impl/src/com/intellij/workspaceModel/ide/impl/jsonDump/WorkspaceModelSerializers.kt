// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jsonDump

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.metadata.model.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeStructure
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class WorkspaceModelSerializers {
  private val serializers: MutableMap<String, KSerializer<Any>> = HashMap()

  private val stringSerializer = String.serializer()

  private val entitiesListSerializer = CustomListSerializer(DynamicSerializer {
    val childEntityMeta = (it as WorkspaceEntityBase).getData().getMetadata()
    getOrBuildSerializer(it, childEntityMeta)
  })

  @OptIn(ExperimentalSerializationApi::class)
  private val entitySourceSerializer: KSerializer<Any> = object : KSerializer<Any> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Entity Source Serializer") {
      element("entitySourceFqName", stringSerializer.descriptor)
      element("virtualFileUrl", stringSerializer.descriptor)
    }

    override fun serialize(encoder: Encoder, value: Any) {
      encoder.encodeStructure(descriptor) {
        encodeSerializableElement(descriptor, 0, stringSerializer, value::class.qualifiedName ?: "No name entity source class")
        encodeNullableSerializableElement(descriptor, 1, stringSerializer, (value as? EntitySource)?.virtualFileUrl?.url)
      }
    }

    override fun deserialize(decoder: Decoder): Any = throw RuntimeException("Deserialization is not supported")
  }

  operator fun get(entity: WorkspaceEntity): KSerializer<Any> {
    val meta = (entity as WorkspaceEntityBase).getData().getMetadata()
    return getOrBuildSerializer(entity, meta)
  }

  private fun getOrBuildSerializer(value: Any, meta: StorageTypeMetadata): KSerializer<Any> {
    val existingSerializer = serializers[value::class.qualifiedName]
    if (existingSerializer != null) {
      return existingSerializer
    }
    return buildSerializer(value, meta)
  }

  @OptIn(WorkspaceEntityInternalApi::class, ExperimentalSerializationApi::class)
  private fun buildSerializer(value: Any, meta: StorageTypeMetadata): KSerializer<Any> {
    val newSerializer = object : KSerializer<Any> {
      val fqName = when (meta) {
        is EntityMetadata, is FinalClassMetadata.ObjectMetadata -> meta.fqName
        else -> null
      }

      override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor(value::class.qualifiedName ?: "No name class") {
          if (fqName != null) {
            element("fqName", stringSerializer.descriptor)
          }

          for (property in meta.properties) {
            if (property.isComputable) continue

            when (val propertyType = property.valueType) {
              is ValueTypeMetadata.SimpleType.PrimitiveType -> {
                element(property.name, stringSerializer.descriptor, isOptional = propertyType.isNullable)
              }
              is ValueTypeMetadata.EntityReference -> {
                if (!propertyType.isChild) continue

                when (propertyType.connectionType) {
                  ConnectionId.ConnectionType.ONE_TO_ONE, ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE -> {
                    element(property.name, stringSerializer.descriptor, isOptional = propertyType.isNullable)
                  }
                  ConnectionId.ConnectionType.ONE_TO_MANY, ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY -> {
                    element("${property.name}Count", Int.serializer().descriptor)
                    element(property.name, entitiesListSerializer.descriptor)
                  }
                }
              }
              is ValueTypeMetadata.SimpleType.CustomType -> {
                element(property.name, stringSerializer.descriptor, isOptional = propertyType.isNullable)
              }
              is ValueTypeMetadata.ParameterizedType -> {
                // TODO: this handles lists and sets, but not maps
                if (propertyType.genericParameterForList() == null) continue
                element("${property.name}Count", Int.serializer().descriptor)
                element(property.name, entitiesListSerializer.descriptor)
              }
            }
          }
        }

      override fun serialize(encoder: Encoder, value: Any) {
        var propertyIndex = 0

        encoder.encodeStructure(descriptor) {
          if (fqName != null) {
            encodeSerializableElement(descriptor, propertyIndex, stringSerializer, fqName)
            propertyIndex++
          }

          for (property in meta.properties) {
            if (property.isComputable) continue

            val propertyType = property.valueType
            val propertyValue = value.getPropertyValue(property.name)
            if (propertyValue == null) {
              if (propertyType !is ValueTypeMetadata.EntityReference || propertyType.isChild) {
                encodeNullableSerializableElement(descriptor, propertyIndex, stringSerializer, null)
                propertyIndex++
              }
              continue
            }

            when (propertyType) {
              is ValueTypeMetadata.SimpleType.PrimitiveType -> {
                encodeNullableSerializableElement(descriptor, propertyIndex, propertyType.serializer(), propertyValue)
              }
              is ValueTypeMetadata.EntityReference -> {
                if (!propertyType.isChild) continue

                when (propertyType.connectionType) {
                  ConnectionId.ConnectionType.ONE_TO_ONE, ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE -> {
                    val childEntity = propertyValue as WorkspaceEntityBase
                    val childMeta = childEntity.getData().getMetadata()
                    val entitySerializer = getOrBuildSerializer(childEntity, childMeta)
                    encodeSerializableElement(descriptor, propertyIndex, entitySerializer, propertyValue)
                  }
                  ConnectionId.ConnectionType.ONE_TO_MANY, ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY -> {
                    @Suppress("UNCHECKED_CAST")
                    encodeIntElement(descriptor, propertyIndex, (propertyValue as List<Any>).size)
                    propertyIndex++ // additional increment for the 'Count' property
                    @Suppress("USELESS_CAST")
                    encodeSerializableElement(descriptor, propertyIndex, entitiesListSerializer, propertyValue as List<Any>)
                  }
                }
              }
              is ValueTypeMetadata.SimpleType.CustomType -> {
                when (val typeMetadata = propertyType.typeMetadata) {
                  is FinalClassMetadata.KnownClass -> {
                    when (typeMetadata.fqName) {
                      VirtualFileUrl::class.qualifiedName -> {
                        val stringValue = (propertyValue as VirtualFileUrl).url
                        encodeNullableSerializableElement(descriptor, propertyIndex, stringSerializer, stringValue)
                      }
                      EntitySource::class.qualifiedName -> {
                        encodeNullableSerializableElement(descriptor, propertyIndex, entitySourceSerializer, propertyValue)
                      }
                      else -> {
                        val knownClassSerializer = serializers[typeMetadata.fqName]
                        if (knownClassSerializer != null) {
                          encodeNullableSerializableElement(descriptor, propertyIndex, knownClassSerializer, propertyValue)
                        } else {
                          encodeNullableSerializableElement(descriptor, propertyIndex, stringSerializer, "Unknown \"FinalClassMetadata.KnownClass\": ${typeMetadata.fqName}")
                        }
                      }
                    }
                  }
                  is FinalClassMetadata.ClassMetadata, is FinalClassMetadata.ObjectMetadata -> {
                    val serializer = getOrBuildSerializer(propertyValue, typeMetadata)
                    encodeSerializableElement(descriptor, propertyIndex, serializer, propertyValue)
                  }
                  is FinalClassMetadata.EnumClassMetadata -> {
                    encodeSerializableElement(descriptor, propertyIndex, stringSerializer, propertyValue.toString())
                  }
                  is ExtendableClassMetadata.AbstractClassMetadata -> {
                    val serializer = getSerializerForAbstractClass(propertyValue, typeMetadata)
                    if (serializer != null) {
                      encodeSerializableElement(descriptor, propertyIndex, serializer, propertyValue)
                    }
                    else {
                      // TODO: ModuleId does not get serialized, requires changes in MetadataStorage
                      val errorMessage = 
                        "Unknown subclass of ${typeMetadata.fqName} in property ${property.name}: ${propertyValue::class.qualifiedName}"
                      encodeSerializableElement(descriptor, propertyIndex, stringSerializer, errorMessage)
                    }
                  }
                }
              }
              is ValueTypeMetadata.ParameterizedType -> {
                val genericType = propertyType.genericParameterForList() ?: continue

                @Suppress("UNCHECKED_CAST")
                val valueList = propertyValue as List<Any>
                val valueSerializer = getListValueSerializer(valueList, genericType)
                val listSerializer = CustomListSerializer(valueSerializer)
                encodeIntElement(descriptor, propertyIndex, valueList.size)
                propertyIndex++ // additional increment for the 'Count' property
                encodeSerializableElement(descriptor, propertyIndex, listSerializer, valueList)
              }
            }
            propertyIndex++
          }
        }
      }

      override fun deserialize(decoder: Decoder): Any = throw RuntimeException("Deserialization is not supported")
    }

    value::class.qualifiedName?.let { serializers[it] = newSerializer }

    return newSerializer
  }

  private fun getListValueSerializer(valueList: List<Any>, genericType: ValueTypeMetadata.SimpleType): KSerializer<Any> {
    if (genericType is ValueTypeMetadata.SimpleType.CustomType) {
      val genericTypeMetadata = genericType.typeMetadata
      if (genericTypeMetadata is ExtendableClassMetadata) {
        return DynamicSerializer { value ->
          getSerializerForAbstractClass(value, genericTypeMetadata)
          ?: error("Unserializable subclass of ${genericTypeMetadata.fqName}: ${value::class.qualifiedName}")
        }
      }
    }
    val someValue = valueList.firstOrNull()
    if (someValue == null) {
      @Suppress("UNCHECKED_CAST")
      return stringSerializer as KSerializer<Any>
    }
    return when (genericType) {
      is ValueTypeMetadata.SimpleType.PrimitiveType -> genericType.serializer()
      is ValueTypeMetadata.SimpleType.CustomType -> getOrBuildSerializer(someValue, genericType.typeMetadata)
    }
  }

  private fun getSerializerForAbstractClass(value: Any, abstractClassMetadata: ExtendableClassMetadata): KSerializer<Any>? {
    val valueClassName = value::class.qualifiedName ?: return null
    val classMetadata = abstractClassMetadata.subclasses.find { it.fqName.replace("$", ".") == valueClassName } ?: return null
    return getOrBuildSerializer(value, classMetadata)
  }

  inner class CustomListSerializer(private val elementSerializer: KSerializer<Any>) : KSerializer<Any> {
    override val descriptor: SerialDescriptor = ListSerializer(stringSerializer).descriptor

    override fun serialize(encoder: Encoder, value: Any) {
      @Suppress("UNCHECKED_CAST")
      val valueList = value as List<Any>
      val collectionEncoder = encoder.beginStructure(descriptor)
      for ((index, element) in valueList.withIndex()) {
        collectionEncoder.encodeSerializableElement(descriptor, index, elementSerializer, element)
      }
      collectionEncoder.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): Any = throw RuntimeException("Deserialization is not supported")
  }

  inner class DynamicSerializer(private val serializerGetter: (Any) -> KSerializer<Any>) : KSerializer<Any> {
    override val descriptor: SerialDescriptor = stringSerializer.descriptor

    override fun serialize(encoder: Encoder, value: Any) {
      val serializer = serializerGetter(value)
      encoder.encodeSerializableValue(serializer, value)
    }

    override fun deserialize(decoder: Decoder): Nothing = throw RuntimeException("Deserialization is not supported")
  }
}
