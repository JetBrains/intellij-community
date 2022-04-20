// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.impl.ExtRefKey
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue
import kotlin.test.fail

class GeneratedCodeVersionsTest {
  private var prev_api = 0
  private var prev_impl = 0
  @Before
  fun setUp() {
    prev_api = CodeGeneratorVersions.API_VERSION
    prev_impl = CodeGeneratorVersions.IMPL_VERSION
  }

  @After
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
      assertTrue("'0'" in e.message!!)
      assertTrue("'-10'" in e.message!!)
      return
    }
    fail("No exception thrown")
  }

  @Test
  fun `test api builder impl code`() {
    CodeGeneratorVersions.API_VERSION = -10
    val emptyBuilder = createEmptyBuilder()
    try {
      emptyBuilder.addEntity(SuperSimpleEntity {})
    }
    catch (e: AssertionError) {
      assertContains(e.message!!, "API")
      assertContains(e.message!!, "'-10'")
      assertContains(e.message!!, "'-11'")
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
      assertContains(e.message!!, "'0'")
      assertContains(e.message!!, "'-12'")
      return
    }
    fail("No exception thrown")
  }
}

interface SuperSimpleEntity : WorkspaceEntity {
  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(-10)
  interface Builder: SuperSimpleEntity, ModifiableWorkspaceEntity<SuperSimpleEntity>, ObjBuilder<SuperSimpleEntity> {
  }

  companion object: Type<SuperSimpleEntity, Builder>()
  //@formatter:on
  //endregion

}



@GeneratedCodeApiVersion(-11)
@GeneratedCodeImplVersion(-12)
open class SuperSimpleEntityImpl: SuperSimpleEntity, WorkspaceEntityBase() {




  class Builder(val result: SuperSimpleEntityData?): ModifiableWorkspaceEntityBase<SuperSimpleEntity>(), SuperSimpleEntity.Builder {
    constructor(): this(SuperSimpleEntityData())

    override fun build(): SuperSimpleEntity = this

    override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
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

      // Process entities from extension fields
      val keysToRemove = ArrayList<ExtRefKey>()
      for ((key, entity) in extReferences) {
        if (!key.isChild()) {
          continue
        }
        if (entity is List<*>) {
          for (item in entity) {
            if (item is ModifiableWorkspaceEntityBase<*>) {
              builder.addEntity(item)
            }
          }
          entity as List<WorkspaceEntity>
          val (withBuilder_entity, woBuilder_entity) = entity.partition { it is ModifiableWorkspaceEntityBase<*> && it.diff != null }
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
      checkInitialization() // TODO uncomment and check failed tests
    }

    fun checkInitialization() {
      val _diff = diff
    }




    override fun getEntityData(): SuperSimpleEntityData = result ?: super.getEntityData() as SuperSimpleEntityData
    override fun getEntityClass(): Class<SuperSimpleEntity> = SuperSimpleEntity::class.java
  }

  // TODO: Fill with the data from the current entity
  fun builder(): ObjBuilder<*> = Builder(SuperSimpleEntityData())
}

class SuperSimpleEntityData : WorkspaceEntityData<SuperSimpleEntity>() {


  override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<SuperSimpleEntity> {
    val modifiable = SuperSimpleEntityImpl.Builder(null)
    modifiable.allowModifications {
      modifiable.diff = diff
      modifiable.snapshot = diff
      modifiable.id = createEntityId()
      modifiable.entitySource = this.entitySource
    }
    return modifiable
  }

  override fun createEntity(snapshot: WorkspaceEntityStorage): SuperSimpleEntity {
    val entity = SuperSimpleEntityImpl()
    entity.entitySource = entitySource
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
}