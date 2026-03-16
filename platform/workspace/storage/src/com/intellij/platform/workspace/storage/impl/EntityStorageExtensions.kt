// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.instrumentation.instrumentation

// ------------------------- Updating references ------------------------

// This is actually "replace children"
@Deprecated("Use the method from the instrumentation API")
public fun EntityStorage.updateOneToManyChildrenOfParent(connectionId: ConnectionId,
                                                         parent: WorkspaceEntity.Builder<out WorkspaceEntity>,
                                                         children: List<WorkspaceEntity.Builder<out WorkspaceEntity>>) {
  this.mutable.instrumentation.replaceChildren(connectionId, parent, children)
}


// This is actually "replace children"
// TODO Why we don't remove old children like in [EntityStorage.updateOneToManyChildrenOfParent]? IDEA-327863
//    This is probably a bug.
@Deprecated("Use the method from the instrumentation API")
public fun EntityStorage.updateOneToAbstractManyChildrenOfParent(connectionId: ConnectionId,
                                                                 parentEntity: WorkspaceEntity.Builder<out WorkspaceEntity>,
                                                                 childrenEntity: Sequence<WorkspaceEntity.Builder<out WorkspaceEntity>>) {
  this.mutable.instrumentation.replaceChildren(connectionId, parentEntity, childrenEntity.toList())
}

@Deprecated("Use the method from the instrumentation API")
public fun EntityStorage.updateOneToAbstractOneChildOfParent(connectionId: ConnectionId,
                                                             parentEntity: WorkspaceEntity.Builder<out WorkspaceEntity>,
                                                             childEntity: WorkspaceEntity.Builder<out WorkspaceEntity>?) {
  this.mutable.instrumentation.replaceChildren(connectionId, parentEntity, listOfNotNull(childEntity))
}

@Deprecated("Use the method from the instrumentation API")
public fun EntityStorage.updateOneToOneChildOfParent(connectionId: ConnectionId, parentEntity: WorkspaceEntity.Builder<out WorkspaceEntity>,
                                                     childEntity: WorkspaceEntity.Builder<out WorkspaceEntity>?) {
  this.mutable.instrumentation.replaceChildren(connectionId, parentEntity, listOfNotNull(childEntity))
}

@Deprecated("Use the method from the instrumentation API")
public fun EntityStorage.updateOneToManyParentOfChild(connectionId: ConnectionId,
                                                      childEntity: WorkspaceEntity.Builder<out WorkspaceEntity>,
                                                      parentEntity: WorkspaceEntity.Builder<out WorkspaceEntity>?) {
  this.mutable.instrumentation.addChild(connectionId, parentEntity, childEntity)
}

@Deprecated("Use the method from the instrumentation API")
public fun EntityStorage.updateOneToAbstractManyParentOfChild(connectionId: ConnectionId,
                                                              child: WorkspaceEntity.Builder<out WorkspaceEntity>,
                                                              parent: WorkspaceEntity.Builder<out WorkspaceEntity>?) {
  this.mutable.instrumentation.addChild(connectionId, parent, child)
}

@Deprecated("Use the method from the instrumentation API")
public fun EntityStorage.updateOneToOneParentOfChild(connectionId: ConnectionId,
                                                     childEntity: WorkspaceEntity.Builder<out WorkspaceEntity>,
                                                     parentEntity: WorkspaceEntity.Builder<out WorkspaceEntity>?) {
  this.mutable.instrumentation.addChild(connectionId, parentEntity, childEntity)
}

@Deprecated("Use the method from the instrumentation API")
public fun EntityStorage.updateOneToAbstractOneParentOfChild(connectionId: ConnectionId,
                                                             childEntity: WorkspaceEntity.Builder<out WorkspaceEntity>,
                                                             parentEntity: WorkspaceEntity.Builder<out WorkspaceEntity>?) {
  this.mutable.instrumentation.addChild(connectionId, parentEntity, childEntity)
}

// ------------------------- Extracting references references ------------------------

@Deprecated("Please use direct call to `this.instrumentation.extractOneToManyChildren`",
            ReplaceWith("this.instrumentation.extractOneToManyChildren(connectionId, parent)",
                        "com.intellij.platform.workspace.storage.instrumentation.instrumentation"))
public fun <Child : WorkspaceEntity> EntityStorage.extractOneToManyChildren(connectionId: ConnectionId,
                                                                            parent: WorkspaceEntity): Sequence<Child> {
  return this.instrumentation.getManyChildren(connectionId, parent) as Sequence<Child>
}

public fun <Child : WorkspaceEntity> MutableEntityStorage.extractOneToManyChildren(connectionId: ConnectionId,
                                                                                   parent: WorkspaceEntity.Builder<out WorkspaceEntity>): Sequence<WorkspaceEntity.Builder<out WorkspaceEntity>> {
  return this.instrumentation.getManyChildrenBuilders(connectionId, parent)
}

@Deprecated("Use the method from the instrumentation API")
public fun <Child : WorkspaceEntity> EntityStorage.extractOneToAbstractManyChildren(connectionId: ConnectionId,
                                                                                    parent: WorkspaceEntity): Sequence<Child> {
  return this.instrumentation.getManyChildren(connectionId, parent) as Sequence<Child>
}

@Deprecated("Use the method from the instrumentation API")
public fun <Parent : WorkspaceEntity> EntityStorage.extractOneToAbstractManyParent(
  connectionId: ConnectionId,
  child: WorkspaceEntity
): Parent? {
  return this.instrumentation.getParent(connectionId, child) as? Parent
}

@Deprecated("Use the method from the instrumentation API")
public fun <Parent : WorkspaceEntity> MutableEntityStorage.extractOneToAbstractManyParent(
  connectionId: ConnectionId,
  child: WorkspaceEntity.Builder<out WorkspaceEntity>,
): WorkspaceEntity.Builder<out WorkspaceEntity>? {
  return this.instrumentation.getParentBuilder(connectionId, child)
}

@Deprecated("Please use direct call to instrumentation level API",
            ReplaceWith("this.instrumentation.getOneChild(connectionId, parent)",
                        "com.intellij.platform.workspace.storage.instrumentation.instrumentation"))
public fun <Child : WorkspaceEntity> EntityStorage.extractOneToAbstractOneChild(connectionId: ConnectionId,
                                                                                parent: WorkspaceEntity): Child? {
  @Suppress("UNCHECKED_CAST")
  return this.instrumentation.getOneChild(connectionId, parent) as Child?
}

@Deprecated("Use the method from the instrumentation API")
public fun <Child : WorkspaceEntity> EntityStorage.extractOneToOneChild(connectionId: ConnectionId, parent: WorkspaceEntity): Child? {
  return this.instrumentation.getOneChild(connectionId, parent) as? Child
}

@Suppress("UNCHECKED_CAST")
internal fun <Child : WorkspaceEntity> AbstractEntityStorage.extractOneToOneChild(connectionId: ConnectionId, parentId: EntityId): Child? {
  val entitiesList = entitiesByType[connectionId.childClass] ?: return null
  return refs.getOneToOneChild(connectionId, parentId.arrayId) {
    val childEntityData = entitiesList[it]
    if (childEntityData == null) {
      if (!brokenConsistency) {
        error("""
          Consistency issue. Cannot get a child in one to one connection.
          Connection id: $connectionId
          Parent id: $parentId
          Child array id: $it
        """.trimIndent())
      }
      null
    }
    else childEntityData.createEntity(this) as Child
  }
}

@Deprecated("Use the method from the instrumentation API")
public fun <Parent : WorkspaceEntity> EntityStorage.extractOneToOneParent(connectionId: ConnectionId,
                                                                          child: WorkspaceEntity): Parent? {
  return this.instrumentation.getParent(connectionId, child) as? Parent
}

@Deprecated("Use the method from the instrumentation API")
public fun <Parent : WorkspaceEntity> MutableEntityStorage.extractOneToOneParent(connectionId: ConnectionId,
                                                                          child: WorkspaceEntity.Builder<out WorkspaceEntity>): WorkspaceEntity.Builder<out WorkspaceEntity>? {
  return this.instrumentation.getParentBuilder(connectionId, child)
}

@Deprecated("Use the method from the instrumentation API")
public fun <Parent : WorkspaceEntity> EntityStorage.extractOneToAbstractOneParent(
  connectionId: ConnectionId,
  child: WorkspaceEntity,
): Parent? {
  return this.instrumentation.getParent(connectionId, child) as? Parent
}

@Deprecated("Use the method from the instrumentation API")
public fun <Parent : WorkspaceEntity> MutableEntityStorage.extractOneToAbstractOneParent(
  connectionId: ConnectionId,
  child: WorkspaceEntity.Builder<out WorkspaceEntity>,
): WorkspaceEntity.Builder<out WorkspaceEntity>? {
  return this.instrumentation.getParentBuilder(connectionId, child)
}

@Deprecated("Use the method from the instrumentation API")
public fun <Parent : WorkspaceEntity> EntityStorage.extractOneToManyParent(connectionId: ConnectionId,
                                                                           child: WorkspaceEntity): Parent? {
  return this.instrumentation.getParent(connectionId, child) as? Parent
}

@Deprecated("Use the method from the instrumentation API")
public fun <Parent : WorkspaceEntity> MutableEntityStorage.extractOneToManyParent(connectionId: ConnectionId,
                                                                                  child: WorkspaceEntity.Builder<out WorkspaceEntity>): WorkspaceEntity.Builder<out WorkspaceEntity>? {
  return this.instrumentation.getParentBuilder(connectionId, child)
}
