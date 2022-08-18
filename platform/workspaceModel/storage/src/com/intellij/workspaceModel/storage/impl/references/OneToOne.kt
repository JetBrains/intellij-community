// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl.references

import com.intellij.workspaceModel.storage.impl.*
import com.intellij.workspaceModel.storage.impl.ConnectionId.ConnectionType.ONE_TO_ONE
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class OneToOneParent private constructor() {
  class Nullable<Parent : WorkspaceEntityBase, Child : WorkspaceEntityBase>(
    private val childClass: Class<Child>,
    private val isParentInChildNullable: Boolean,
  ) : ReadOnlyProperty<Parent, Child?> {
    private var connectionId: ConnectionId? = null

    override fun getValue(thisRef: Parent, property: KProperty<*>): Child? {
      if (connectionId == null) {
        connectionId = ConnectionId.create(thisRef.javaClass, childClass, ONE_TO_ONE, isParentInChildNullable)
      }
      return thisRef.snapshot.extractOneToOneChild(connectionId!!, thisRef)
    }
  }
}

class OneToOneChild private constructor() {
  class NotNull<Child : WorkspaceEntityBase, Parent : WorkspaceEntityBase>(
    private val parentClass: Class<Parent>,
  ) : ReadOnlyProperty<Child, Parent> {
    private var connectionId: ConnectionId? = null

    override fun getValue(thisRef: Child, property: KProperty<*>): Parent {
      if (connectionId == null) {
        connectionId = ConnectionId.create(parentClass, thisRef.javaClass, ONE_TO_ONE, false)
      }
      return thisRef.snapshot.extractOneToOneParent(connectionId!!, thisRef)!!
    }
  }

  class Nullable<Child : WorkspaceEntityBase, Parent : WorkspaceEntityBase>(
    private val parentClass: Class<Parent>
  ) : ReadOnlyProperty<Child, Parent?> {
    private var connectionId: ConnectionId? = null

    override fun getValue(thisRef: Child, property: KProperty<*>): Parent? {
      if (connectionId == null) {
        connectionId = ConnectionId.create(parentClass, thisRef.javaClass, ONE_TO_ONE, true)
      }
      return thisRef.snapshot.extractOneToOneParent(connectionId!!, thisRef)
    }
  }
}

// TODO: 08.02.2021 It may cause issues if we'll attach two children to the same parent
class MutableOneToOneParent private constructor() {
  class Nullable<Parent : WorkspaceEntityBase, Child : WorkspaceEntityBase, ModifParent : ModifiableWorkspaceEntityBase<Parent>>(
    private val parentClass: Class<Parent>,
    private val childClass: Class<Child>,
    private val isParentInChildNullable: Boolean,
  ) : ReadWriteProperty<ModifParent, Child?> {

    private var connectionId: ConnectionId? = null

    override fun getValue(thisRef: ModifParent, property: KProperty<*>): Child? {
      if (connectionId == null) {
        connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_ONE, isParentInChildNullable)
      }
      return (thisRef.diff as MutableEntityStorageImpl).extractOneToOneChild(connectionId!!, thisRef.id)!!
    }

    override fun setValue(thisRef: ModifParent, property: KProperty<*>, value: Child?) {
      if (!thisRef.modifiable.get()) {
        throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
      }
      if (connectionId == null) {
        connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_ONE, isParentInChildNullable)
      }
      (thisRef.diff as MutableEntityStorageImpl).updateOneToOneChildOfParent(connectionId!!, thisRef, value)
    }
  }
}

class MutableOneToOneChild private constructor() {
  class NotNull<Parent : WorkspaceEntityBase, Child : WorkspaceEntityBase, ModifChild : ModifiableWorkspaceEntityBase<Child>>(
    private val childClass: Class<Child>,
    private val parentClass: Class<Parent>,
  ) : ReadWriteProperty<ModifChild, Parent> {
    private var connectionId: ConnectionId? = null

    override fun getValue(thisRef: ModifChild, property: KProperty<*>): Parent {
      if (connectionId == null) {
        connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_ONE, false)
      }
      return (thisRef.diff as MutableEntityStorageImpl).extractOneToOneParent(connectionId!!, thisRef.id)!!
    }

    override fun setValue(thisRef: ModifChild, property: KProperty<*>, value: Parent) {
      if (!thisRef.modifiable.get()) {
        throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
      }
      if (connectionId == null) {
        connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_ONE, false)
      }
      (thisRef.diff as MutableEntityStorageImpl).updateOneToOneParentOfChild(connectionId!!, thisRef, value)
    }
  }

  class Nullable<Parent : WorkspaceEntityBase, Child : WorkspaceEntityBase, ModifChild : ModifiableWorkspaceEntityBase<Child>>(
    private val childClass: Class<Child>,
    private val parentClass: Class<Parent>,
  ) : ReadWriteProperty<ModifChild, Parent?> {
    private var connectionId: ConnectionId? = null

    override fun getValue(thisRef: ModifChild, property: KProperty<*>): Parent? {
      if (connectionId == null) {
        connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_ONE, true)
      }
      return (thisRef.diff as MutableEntityStorageImpl).extractOneToOneParent(connectionId!!, thisRef.id)
    }

    override fun setValue(thisRef: ModifChild, property: KProperty<*>, value: Parent?) {
      if (!thisRef.modifiable.get()) {
        throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
      }
      if (connectionId == null) {
        connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_ONE, true)
      }
      (thisRef.diff as MutableEntityStorageImpl).updateOneToOneParentOfChild(connectionId!!, thisRef, value)
    }
  }
}
