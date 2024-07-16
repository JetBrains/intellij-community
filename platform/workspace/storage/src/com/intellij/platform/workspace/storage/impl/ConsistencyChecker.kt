// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl

import com.intellij.openapi.diagnostic.trace
import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntityStorage
import it.unimi.dsi.fastutil.ints.IntSet

public fun EntityStorage.assertConsistency() {
  (this as AbstractEntityStorage).assertConsistency()
}

internal fun AbstractEntityStorage.assertConsistency() {
  AbstractEntityStorage.LOG.trace { "Checking consistency of $this" }

  entitiesByType.assertConsistency(this)
  // Rules:
  //  1) Refs should not have links without a corresponding entity
  //    1.1) For abstract containers: EntityId has the class of ConnectionId
  //  2) There is no child without a parent under the hard reference

  val existingConnectionIds = entitiesByType.entityFamilies
    .mapNotNull { it?.entities?.firstOrNull()?.createEntity(this)?.asBase()?.connectionIdList() }
    .flatten()
    .toMutableSet()

  refs.oneToManyContainer.forEach { (connectionId, map) ->

    existingConnectionIds.remove(connectionId)

    // Assert correctness of connection id
    assert(connectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_MANY)

    //  1) Refs should not have links without a corresponding entity
    map.forEachKey { childId, parentId ->
      assertResolvable(this, connectionId.parentClass, parentId)
      assertResolvable(this, connectionId.childClass, childId)
    }

    //  2) All children should have a parent if the connection has a restriction for that
    if (!connectionId.isParentNullable) {
      checkStrongConnection(this, map.keys, connectionId.childClass, connectionId.parentClass, connectionId)
    }

    map.assertConsistency()
  }

  refs.oneToOneContainer.forEach { (connectionId, map) ->

    existingConnectionIds.remove(connectionId)

    // Assert correctness of connection id
    assert(connectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_ONE)

    //  1) Refs should not have links without a corresponding entity
    map.forEachKey { childId, parentId ->
      assertResolvable(this, connectionId.parentClass, parentId)
      assertResolvable(this, connectionId.childClass, childId)
    }

    //  2) Connections satisfy connectionId requirements
    if (!connectionId.isParentNullable) checkStrongConnection(this, map.keys, connectionId.childClass, connectionId.parentClass, connectionId)
  }

  refs.oneToAbstractManyContainer.forEach { (connectionId, map) ->

    existingConnectionIds.remove(connectionId)

    // Assert correctness of connection id
    assert(connectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY)

    map.forEach { (childId, parentId) ->
      //  1) Refs should not have links without a corresponding entity
      assertResolvable(this, parentId.id.clazz, parentId.id.arrayId)
      assertResolvable(this, childId.id.clazz, childId.id.arrayId)

      //  1.1) For abstract containers: EntityId has the class of ConnectionId
      assertCorrectEntityClass(connectionId.parentClass, parentId.id)
      assertCorrectEntityClass(connectionId.childClass, childId.id)
    }

    //  2) Connections satisfy connectionId requirements
    if (!connectionId.isParentNullable) {
      checkStrongAbstractConnection(this, map.keys.map { it.id }.toMutableSet(), map.keys.map { it.id.clazz }.toSet(), connectionId.debugStr())
    }
  }

  refs.abstractOneToOneContainer.forEach { (connectionId, map) ->

    existingConnectionIds.remove(connectionId)

    // Assert correctness of connection id
    assert(connectionId.connectionType == ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE)

    map.forEach { (childId, parentId) ->
      //  1) Refs should not have links without a corresponding entity
      assertResolvable(this, parentId.id.clazz, parentId.id.arrayId)
      assertResolvable(this, childId.id.clazz, childId.id.arrayId)

      //  1.1) For abstract containers: EntityId has the class of ConnectionId
      assertCorrectEntityClass(connectionId.parentClass, parentId.id)
      assertCorrectEntityClass(connectionId.childClass, childId.id)
    }

    //  2) Connections satisfy connectionId requirements
    if (!connectionId.isParentNullable) {
      checkStrongAbstractConnection(this, map.keys.mapTo(HashSet()) { it.id }, map.keys.toMutableSet().map { it.id.clazz }.toSet(),
                                    connectionId.debugStr())
    }
  }

  existingConnectionIds.filterNot { it.isParentNullable }.forEach { connectionId ->
    val childrenClass = connectionId.childClass
    val childWithoutParent = entitiesByType[childrenClass]?.entities?.firstOrNull()
    assert(childWithoutParent == null) {
      """
        There is an entity ${childWithoutParent!!.createEntityId().asString()} of type ${childrenClass.findWorkspaceEntity().simpleName} without a parent of type ${connectionId.parentClass.findWorkspaceEntity().simpleName}
        However, the parent field in child is not nullable
        Child full type: ${childrenClass.findWorkspaceEntity()}
        Parent full type: ${connectionId.parentClass.findWorkspaceEntity()}
        """.trimIndent()
    }
  }

  indexes.assertConsistency(this)
}

private fun assertCorrectEntityClass(connectionClass: Int, entityId: EntityId) {
  assert(connectionClass.findWorkspaceEntity().isAssignableFrom(entityId.clazz.findWorkspaceEntity())) {
    "Entity storage with connection class ${connectionClass.findWorkspaceEntity()} contains entity data of wrong type $entityId"
  }
}

private fun assertResolvable(storage: AbstractEntityStorage, clazz: Int, id: Int) {
  assert(storage.entitiesByType[clazz]?.get(id) != null) {
    "Reference to ${clazz.findWorkspaceEntity()}-:-$id cannot be resolved"
  }
}

private fun checkAllStrongConnections(storage: AbstractEntityStorage,
                                      entityFamilyClass: Int, keys: MutableSet<EntityId>, debugInfo: String) {
  val entityFamily = storage.entitiesByType.entityFamilies[entityFamilyClass] ?: error("Entity family doesn't exist. $debugInfo")
  entityFamily.entities.forEach { entity ->
    if (entity == null) return@forEach
    val removed = keys.remove(entity.createEntityId())
    assert(removed) { "Entity $entity doesn't have a correct connection. $debugInfo" }
  }
}

private fun checkStrongAbstractConnection(storage: AbstractEntityStorage,
                                          connectionKeys: Set<EntityId>, entityFamilyClasses: Set<Int>, debugInfo: String) {
  val keys = connectionKeys.toMutableSet()
  entityFamilyClasses.forEach { entityFamilyClass ->
    checkAllStrongConnections(storage, entityFamilyClass, keys, debugInfo)
  }
  assert(keys.isEmpty()) { "Store is inconsistent. $debugInfo" }
}

private fun checkStrongConnection(storage: AbstractEntityStorage,
                                  connectionKeys: IntSet, entityFamilyClass: Int, connectionTo: Int, connectionId: ConnectionId) {
  var counter = 0
  val entityFamily = storage.entitiesByType[entityFamilyClass] ?: ImmutableEntityFamily()
  entityFamily.entities.forEachIndexed { i, entity ->
    if (entity == null) return@forEachIndexed
    assert(i in connectionKeys) {
      """
            |Storage inconsistency. Hard reference broken.
            |Existing entity $entity
            |Misses a reference to ${connectionTo.findWorkspaceEntity()}
            |Reference id: $i
            |ConnectionId: $connectionId
            """.trimMargin()
    }
    counter++
  }

  assert(counter == connectionKeys.size) { "Store is inconsistent" }
}
