// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.serialization.serializer

import com.esotericsoftware.kryo.kryo5.Kryo
import com.esotericsoftware.kryo.kryo5.KryoException
import com.esotericsoftware.kryo.kryo5.Serializer
import com.esotericsoftware.kryo.kryo5.io.Input
import com.esotericsoftware.kryo.kryo5.io.Output
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityTypesResolver
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.*
import com.intellij.platform.workspace.storage.impl.containers.Object2IntWithDefaultMap
import com.intellij.platform.workspace.storage.impl.containers.Object2LongWithDefaultMap
import com.intellij.platform.workspace.storage.impl.indices.*
import com.intellij.platform.workspace.storage.impl.serialization.*
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlImpl
import com.intellij.platform.workspace.storage.url.UrlRelativizer
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import java.util.function.BiConsumer
import java.util.function.ToIntFunction


internal class StorageSerializerUtil(
  private val typesResolver: EntityTypesResolver,
  private val virtualFileManager: VirtualFileUrlManager,
  private val interner: StorageInterner,
  private val urlRelativizer: UrlRelativizer?,
  private val classCache: Object2IntWithDefaultMap<TypeInfo>,
) {

  internal fun getParentEntityIdSerializer(): Serializer<ParentEntityId> = object : Serializer<ParentEntityId>(false, true) {
    override fun write(kryo: Kryo, output: Output, `object`: ParentEntityId) {
      kryo.writeClassAndObject(output, `object`.id.toSerializableEntityId())
    }

    override fun read(kryo: Kryo, input: Input, type: Class<out ParentEntityId>): ParentEntityId {
      val entityId = kryo.readClassAndObject(input) as SerializableEntityId
      return ParentEntityId(entityId.toEntityId(classCache))
    }
  }

  internal fun getChildEntityIdSerializer(): Serializer<ChildEntityId> = object : Serializer<ChildEntityId>(false, true) {
    override fun write(kryo: Kryo, output: Output, `object`: ChildEntityId) {
      kryo.writeClassAndObject(output, `object`.id.toSerializableEntityId())
    }

    override fun read(kryo: Kryo, input: Input, type: Class<out ChildEntityId>): ChildEntityId {
      val entityId = kryo.readClassAndObject(input) as SerializableEntityId
      return ChildEntityId(entityId.toEntityId(classCache))
    }
  }

  internal fun getImmutableEntitiesBarrelSerializer(): Serializer<ImmutableEntitiesBarrel> =
    object : Serializer<ImmutableEntitiesBarrel>(false, true) {
      override fun write(kryo: Kryo, output: Output, `object`: ImmutableEntitiesBarrel) {
        val res = java.util.HashMap<TypeInfo, EntityFamily<*>>()
        `object`.entityFamilies.forEachIndexed { i, v ->
          if (v == null) return@forEachIndexed
          val clazz = i.findWorkspaceEntity()
          val typeInfo = clazz.typeInfo
          res[typeInfo] = v
        }

        output.writeInt(res.size)
        res.forEach { (typeInfo, entityFamily) ->
          kryo.writeObject(output, typeInfo)
          try {
            kryo.writeObject(output, entityFamily)
          } catch (e: KryoException) {
            if (e.isClassNotRegisteredException) {
              throw UnsupportedClassException(typeInfo.pluginId, typeInfo.fqName, e.extractedNotRegisteredClassFqn)
            }
            throw e
          }
        }
      }

      @Suppress("UNCHECKED_CAST")
      override fun read(kryo: Kryo, input: Input, type: Class<out ImmutableEntitiesBarrel>): ImmutableEntitiesBarrel {
        val mutableBarrel = MutableEntitiesBarrel.create()

        val size = input.readInt()
        repeat(size) {
          val typeInfo = kryo.readObject(input, TypeInfo::class.java)
          val entityFamily = kryo.readObject(input, ImmutableEntityFamily::class.java)

          val classId = classCache.getOrPut(typeInfo) { typesResolver.resolveClass(typeInfo.fqName, typeInfo.pluginId).toClassId() }
          mutableBarrel.fillEmptyFamilies(classId)
          mutableBarrel.entityFamilies[classId] = entityFamily
        }
        return mutableBarrel.toImmutable()
      }
    }

  internal fun getConnectionIdSerializer(): Serializer<ConnectionId> = object : Serializer<ConnectionId>(false, true) {
    override fun write(kryo: Kryo, output: Output, `object`: ConnectionId) {
      val parentClassType = `object`.parentClass.findWorkspaceEntity()
      val childClassType = `object`.childClass.findWorkspaceEntity()
      val parentTypeInfo = parentClassType.typeInfo
      val childTypeInfo = childClassType.typeInfo

      kryo.writeClassAndObject(output, parentTypeInfo)
      kryo.writeClassAndObject(output, childTypeInfo)
      output.writeString(`object`.connectionType.name)
      output.writeBoolean(`object`.isParentNullable)
    }

    @Suppress("UNCHECKED_CAST")
    override fun read(kryo: Kryo, input: Input, type: Class<out ConnectionId>): ConnectionId {
      val parentClazzInfo = kryo.readClassAndObject(input) as TypeInfo
      val childClazzInfo = kryo.readClassAndObject(input) as TypeInfo

      val parentClass = classCache.computeIfAbsent(parentClazzInfo, ToIntFunction {
        (typesResolver.resolveClass(parentClazzInfo.fqName, parentClazzInfo.pluginId) as Class<WorkspaceEntity>).toClassId()
      })
      val childClass = classCache.computeIfAbsent(childClazzInfo, ToIntFunction {
        (typesResolver.resolveClass(childClazzInfo.fqName, childClazzInfo.pluginId) as Class<WorkspaceEntity>).toClassId()
      })

      val connectionType = ConnectionId.ConnectionType.valueOf(input.readString())
      val parentNullable = input.readBoolean()
      return ConnectionId.create(parentClass, childClass, connectionType, parentNullable)
    }
  }

  internal fun getEntityIdSerializer(): Serializer<EntityId> = object : Serializer<EntityId>(false, true) {
    override fun write(kryo: Kryo, output: Output, `object`: EntityId) {
      output.writeInt(`object`.arrayId)
      val typeClass = `object`.clazz.findWorkspaceEntity()
      val typeInfo = typeClass.typeInfo
      kryo.writeClassAndObject(output, typeInfo)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<out EntityId>): EntityId {
      val arrayId = input.readInt()
      val clazzInfo = kryo.readClassAndObject(input) as TypeInfo
      val clazz = classCache.computeIfAbsent(clazzInfo, ToIntFunction {
        typesResolver.resolveClass(clazzInfo.fqName, clazzInfo.pluginId).toClassId()
      })
      return createEntityId(arrayId, clazz)
    }
  }

  internal fun getVirtualFileUrlSerializer(): Serializer<VirtualFileUrl> = object : Serializer<VirtualFileUrl>(false, true) {
    override fun write(kryo: Kryo, output: Output, obj: VirtualFileUrl) {
      // TODO Write IDs only

      obj as VirtualFileUrlImpl
      val urlToWrite = urlRelativizer?.toRelativeUrl(obj.url) ?: obj.getUrlSegments()
      kryo.writeObject(output, urlToWrite)
    }

    @Suppress("UNCHECKED_CAST")
    override fun read(kryo: Kryo, input: Input, type: Class<out VirtualFileUrl>): VirtualFileUrl {
      // TODO consider the case when the saved cache had relative paths, and now we are expecting absolute paths
      //  (because of the Registry key value change)

      if (urlRelativizer == null) {
        val url = kryo.readObject(input, ArrayList::class.java) as List<String>
        return virtualFileManager.fromUrlSegments(url)
      }
      else {
        val serializedUrl = kryo.readObject(input, String::class.java) as String
        val convertedUrl = urlRelativizer.toAbsoluteUrl(serializedUrl)
        return virtualFileManager.fromUrl(convertedUrl)
      }
    }
  }

  internal fun getSymbolicIdIndexSerializer(): Serializer<SymbolicIdInternalIndex> = object : Serializer<SymbolicIdInternalIndex>(
    false, true) {
    override fun write(kryo: Kryo, output: Output, persistenIndex: SymbolicIdInternalIndex) {
      output.writeInt(persistenIndex.index.keys.size)
      persistenIndex.index.forEach(BiConsumer { key, value ->
        kryo.writeObject(output, key.toSerializableEntityId())
        kryo.writeClassAndObject(output, value)
      })
    }

    override fun read(kryo: Kryo, input: Input, type: Class<out SymbolicIdInternalIndex>): SymbolicIdInternalIndex {
      val res = SymbolicIdInternalIndex.MutableSymbolicIdInternalIndex.from(SymbolicIdInternalIndex())
      repeat(input.readInt()) {
        val key = kryo.readObject(input, SerializableEntityId::class.java).toEntityId(classCache)
        val value = kryo.readClassAndObject(input) as SymbolicEntityId<*>
        res.index(key, value)
      }
      return res.toImmutable()
    }
  }

  internal fun getEntityStorageIndexSerializer(): Serializer<EntityStorageInternalIndex<EntitySource>> =
    object : Serializer<EntityStorageInternalIndex<EntitySource>>(false, true) {
      override fun write(kryo: Kryo, output: Output, entityStorageIndex: EntityStorageInternalIndex<EntitySource>) {
        output.writeInt(entityStorageIndex.index.keys.size)
        entityStorageIndex.index.forEach { entry ->
          kryo.writeObject(output, entry.longKey.toSerializableEntityId())
          kryo.writeClassAndObject(output, entry.value)
        }
      }

      override fun read(kryo: Kryo,
                        input: Input,
                        type: Class<out EntityStorageInternalIndex<EntitySource>>): EntityStorageInternalIndex<EntitySource> {
        val res = EntityStorageInternalIndex.MutableEntityStorageInternalIndex.from(EntityStorageInternalIndex<EntitySource>(false))
        repeat(input.readInt()) {
          val key = kryo.readObject(input, SerializableEntityId::class.java).toEntityId(classCache)
          val value = kryo.readClassAndObject(input) as EntitySource
          res.index(key, value)
        }
        return res.toImmutable()
      }
    }

  internal fun getEntityId2JarDirSerializer(): Serializer<EntityId2JarDir> = object : Serializer<EntityId2JarDir>() {
    override fun write(kryo: Kryo, output: Output, entityId2JarDir: EntityId2JarDir) {
      output.writeInt(entityId2JarDir.keys.size)
      entityId2JarDir.keys.forEach { key ->
        val values: Set<VirtualFileUrl> = entityId2JarDir.getValues(key)
        kryo.writeObject(output, key.toSerializableEntityId())
        output.writeInt(values.size)
        for (value in values) {
          kryo.writeObject(output, value)
        }
      }
    }

    override fun read(kryo: Kryo, input: Input, type: Class<out EntityId2JarDir>): EntityId2JarDir {
      val res = EntityId2JarDir()
      repeat(input.readInt()) {
        val key = kryo.readObject(input, SerializableEntityId::class.java).toEntityId(classCache)
        repeat(input.readInt()) {
          res.put(key, kryo.readObject(input, VirtualFileUrl::class.java))
        }
      }
      return res
    }
  }

  internal fun getVfu2EntityIdSerializer(): Serializer<Vfu2EntityId> = object : Serializer<Vfu2EntityId>() {
    override fun write(kryo: Kryo, output: Output, vfu2EntityId: Vfu2EntityId) {
      output.writeInt(vfu2EntityId.keys.size)
      vfu2EntityId.forEach { (key: VirtualFileUrl, value) ->
        kryo.writeObject(output, key)
        output.writeInt(value.keys.size)
        value.forEach { internalKey: EntityIdWithProperty, internalValue ->
          kryo.writeObject(output, internalKey.entityId.toSerializableEntityId())
          output.writeString(internalKey.propertyName)
          kryo.writeObject(output, internalValue.toSerializableEntityId())
        }
      }
    }

    override fun read(kryo: Kryo, input: Input, type: Class<out Vfu2EntityId>): Vfu2EntityId {
      val vfu2EntityId = Vfu2EntityId(getHashingStrategy())
      repeat(input.readInt()) {
        val file = kryo.readObject(input, VirtualFileUrl::class.java) as VirtualFileUrl
        val size = input.readInt()
        val data = Object2LongWithDefaultMap<EntityIdWithProperty>(size)
        repeat(size) {
          val internalKeyEntityId = kryo.readObject(input, SerializableEntityId::class.java).toEntityId(classCache)
          val internalKeyPropertyName = interner.intern(input.readString())
          val entityId = kryo.readObject(input, SerializableEntityId::class.java).toEntityId(classCache)
          data.put(EntityIdWithProperty(internalKeyEntityId, internalKeyPropertyName), entityId)
        }
        vfu2EntityId.put(file, data)
      }
      return vfu2EntityId
    }
  }

  internal fun getEntityId2VfuSerializer(): Serializer<EntityId2Vfu> = object : Serializer<EntityId2Vfu>() {
    override fun write(kryo: Kryo, output: Output, entityId2Vfu: EntityId2Vfu) {
      kryo.writeClassAndObject(output, entityId2Vfu.mapKeys { it.key.toSerializableEntityId() })
    }

    @Suppress("UNCHECKED_CAST")
    override fun read(kryo: Kryo, input: Input, type: Class<out EntityId2Vfu>): EntityId2Vfu {
      val data = kryo.readClassAndObject(input) as Map<SerializableEntityId, Any>
      return EntityId2Vfu(data.size).also {
        data.forEach(BiConsumer { key, value ->
          it.put(key.toEntityId(classCache), value)
        })
      }
    }
  }

  internal fun getMultimapStorageIndexSerializer(): Serializer<MultimapStorageIndex> = object : Serializer<MultimapStorageIndex>() {
    override fun write(kryo: Kryo, output: Output, multimapIndex: MultimapStorageIndex) {
      kryo.writeClassAndObject(output, multimapIndex.toMap().mapKeys { it.key.toSerializableEntityId() })
    }

    @Suppress("UNCHECKED_CAST")
    override fun read(kryo: Kryo, input: Input?, type: Class<out MultimapStorageIndex>): MultimapStorageIndex {
      val data = kryo.readClassAndObject(input) as Map<SerializableEntityId, Set<SymbolicEntityId<*>>>
      val index = MultimapStorageIndex.MutableMultimapStorageIndex.from(MultimapStorageIndex())
      data.forEach { (key, value) ->
        index.index(key.toEntityId(classCache), value)
      }
      return index
    }
  }


  private val Class<*>.typeInfo: TypeInfo
    get() = getTypeInfo(this, interner, typesResolver)

  private fun EntityId.toSerializableEntityId(): SerializableEntityId {
    val arrayId = this.arrayId
    val clazz = this.clazz.findWorkspaceEntity()
    return interner.intern(SerializableEntityId(arrayId, clazz.typeInfo))
  }

  private fun SerializableEntityId.toEntityId(classCache: Object2IntWithDefaultMap<TypeInfo>): EntityId {
    val classId = classCache.computeIfAbsent(type, ToIntFunction {
      typesResolver.resolveClass(name = this.type.fqName, pluginId = this.type.pluginId).toClassId()
    })
    return createEntityId(arrayId = this.arrayId, clazz = classId)
  }


  private val KryoException.isClassNotRegisteredException: Boolean
    get() = message?.contains("Class is not registered:") ?: false

  private val KryoException.extractedNotRegisteredClassFqn: String
    get() {
      if (message == null) {
        return UNKNOWN_CLASS
      }

      val notRegisteredClassMessage = "Class is not registered: "
      val regex = Regex("$notRegisteredClassMessage[a-zA-Z0-9.$]+")
      return regex.find(message!!)?.value?.substringAfter(notRegisteredClassMessage) ?: UNKNOWN_CLASS
    }

  private val UNKNOWN_CLASS = "unknown class"

}

public class UnsupportedClassException(
  pluginId: PluginId, entityClassFqn: String, unsupportedClassFqn: String
): Exception(
  "Unsupported class $unsupportedClassFqn in the entity $entityClassFqn with $pluginId plugin." +
  "Please make sure that you do not use anonymous implementations of ${EntitySource::class.java.name} and ${SymbolicEntityId::class.java.name}"
)