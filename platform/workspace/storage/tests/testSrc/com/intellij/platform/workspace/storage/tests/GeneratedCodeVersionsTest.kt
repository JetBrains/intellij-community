// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.ConnectionId
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue
import kotlin.test.fail

class GeneratedCodeVersionsTest {
  private var prev_api = 0
  private var prev_impl = 0

  @BeforeEach
  fun setUp() {
    prev_api = CodeGeneratorVersions.API_VERSION
    prev_impl = CodeGeneratorVersions.IMPL_VERSION
  }

  @AfterEach
  fun tearDown() {
    CodeGeneratorVersions.API_VERSION = prev_api
    CodeGeneratorVersions.IMPL_VERSION = prev_impl
    CodeGeneratorVersions.checkApiInImpl = true
    CodeGeneratorVersions.checkApiInInterface = true
  }

  @Test
  fun `test api builder interface`() {
    val emptyBuilder = createEmptyBuilder()
    try {
      emptyBuilder.addEntity(SuperSimpleEntity {})
    }
    catch (e: AssertionError) {
      assertTrue("API" in e.message!!)
      assertTrue("'1000000'" in e.message!!)
      return
    }
    fail("No exception thrown")
  }

  @Test
  fun `test api builder impl code`() {
    CodeGeneratorVersions.API_VERSION = 1000000
    val emptyBuilder = createEmptyBuilder()
    try {
      emptyBuilder.addEntity(SuperSimpleEntity {})
    }
    catch (e: AssertionError) {
      assertContains(e.message!!, "API")
      assertContains(e.message!!, "'1000000'")
      assertContains(e.message!!, "'1000001'")
      return
    }
    fail("No exception thrown")
  }

  @Test
  fun `test impl builder impl code`() {
    CodeGeneratorVersions.checkApiInInterface = false
    CodeGeneratorVersions.checkApiInImpl = false
    val emptyBuilder = createEmptyBuilder()
    try {
      emptyBuilder.addEntity(SuperSimpleEntity {})
    }
    catch (e: AssertionError) {
      assertContains(e.message!!, "IMPL")
      assertContains(e.message!!, "'1000002'")
      return
    }
    fail("No exception thrown")
  }
}

interface SuperSimpleEntity : WorkspaceEntity {
  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(1000000)
  interface Builder: SuperSimpleEntity, WorkspaceEntity.Builder<SuperSimpleEntity> {
  }

  companion object: EntityType<SuperSimpleEntity, Builder>() {
    operator fun invoke(init: (Builder.() -> Unit)? = null):SuperSimpleEntity {
      val builder = builder()
      init?.invoke(builder)
      return builder
    }
  }
  //@formatter:on
  //endregion

}


@GeneratedCodeApiVersion(1000001)
@GeneratedCodeImplVersion(1000002)
open class SuperSimpleEntityImpl : SuperSimpleEntity, WorkspaceEntityBase(null) {


  class Builder(result: SuperSimpleEntityData?) : ModifiableWorkspaceEntityBase<SuperSimpleEntity, SuperSimpleEntityData>(
    result), SuperSimpleEntity.Builder {
    constructor() : this(SuperSimpleEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity SuperSimpleEntity is already created in a different builder")
        }
      }

      this.diff = builder
      this.snapshot = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()

      /*
      // Process entities from extension fields
      val keysToRemove = ArrayList<ExtRefKey>()
      for ((key, entity) in extReferences) {
        if (!key.isChild()) {
          continue
        }
        if (entity is List<*>) {
          for (item in entity) {
            if (item is ModifiableWorkspaceEntityBase<*, *>) {
              builder.addEntity(item)
            }
          }
          entity as List<WorkspaceEntity>
          val (withBuilder_entity, woBuilder_entity) = entity.partition { it is ModifiableWorkspaceEntityBase<*, *> && it.diff != null }
          applyRef(key.getConnectionId(), withBuilder_entity)
          keysToRemove.add(key)
        }
        else {
          entity as WorkspaceEntity
          builder.addEntity(entity)
          applyRef(key.getConnectionId(), entity)
          keysToRemove.add(key)
        }
      }
      for (key in keysToRemove) {
        extReferences.remove(key)
      }
      */

      /*
      // Adding parents and references to the parent
      val parentKeysToRemove = ArrayList<ExtRefKey>()
      for ((key, entity) in extReferences) {
        if (key.isChild()) {
          continue
        }
        if (entity is List<*>) {
          error("Cannot have parent lists")
        }
        else {
          entity as WorkspaceEntity
          builder.addEntity(entity)
          applyParentRef(key.getConnectionId(), entity)
          parentKeysToRemove.add(key)
        }
      }
      for (key in parentKeysToRemove) {
        extReferences.remove(key)
      }
      */
      checkInitialization() // TODO uncomment and check failed tests
    }

    fun checkInitialization() {
      val _diff = diff
    }


    override fun connectionIdList(): List<ConnectionId> {
      TODO("Not yet implemented")
    }

    override fun getEntityClass(): Class<SuperSimpleEntity> = SuperSimpleEntity::class.java
    override var entitySource: EntitySource
      get() = TODO("Not yet implemented")
      set(value) {}
  }

  // TODO: Fill with the data from the current entity
  override fun connectionIdList(): List<ConnectionId> {
    TODO("Not yet implemented")
  }

  override val entitySource: EntitySource
    get() = TODO("Not yet implemented")
}

class SuperSimpleEntityData : WorkspaceEntityData<SuperSimpleEntity>() {

  override fun getMetadata(): EntityMetadata {
    TODO("Not yet implemented")
  }

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<SuperSimpleEntity> {
    val modifiable = SuperSimpleEntityImpl.Builder(null)
    modifiable.allowModifications {
      modifiable.diff = diff
      modifiable.snapshot = diff
      modifiable.id = createEntityId()
      modifiable.entitySource = this.entitySource
    }
    return modifiable
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return SuperSimpleEntity::class.java
  }

  override fun createEntity(snapshot: EntityStorage): SuperSimpleEntity {
    val entity = SuperSimpleEntityImpl()
    entity.snapshot = snapshot
    entity.id = createEntityId()
    return entity
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as SuperSimpleEntityData

    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as SuperSimpleEntityData

    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    return result
  }

  override fun serialize(ser: EntityInformation.Serializer) {
    TODO("Not yet implemented")
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
    TODO("Not yet implemented")
  }
}