// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl.references

import com.intellij.workspaceModel.storage.impl.*
import com.intellij.workspaceModel.storage.impl.ConnectionId.ConnectionType.ONE_TO_MANY
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class OneToMany<Parent : WorkspaceEntityBase, Child : WorkspaceEntityBase>(
  private val childClass: Class<Child>,
  private val isParentInChildNullable: Boolean,
) : ReadOnlyProperty<Parent, Sequence<Child>> {

  private var connectionId: ConnectionId? = null

  override fun getValue(thisRef: Parent, property: KProperty<*>): Sequence<Child> {
    if (connectionId == null) {
      connectionId = ConnectionId.create(thisRef.javaClass, childClass, ONE_TO_MANY, isParentInChildNullable)
    }
    return thisRef.snapshot.extractOneToManyChildren(connectionId!!, thisRef.id)
  }
}

class ManyToOne private constructor() {
  class NotNull<Parent : WorkspaceEntityBase, Child : WorkspaceEntityBase>(private val parentClass: Class<Parent>) : ReadOnlyProperty<Child, Parent> {
    private var connectionId: ConnectionId? = null

    override fun getValue(thisRef: Child, property: KProperty<*>): Parent {
      if (connectionId == null) {
        connectionId = ConnectionId.create(parentClass, thisRef.javaClass, ONE_TO_MANY, false)
      }
      return thisRef.snapshot.extractOneToManyParent(connectionId!!, thisRef.id)!!
    }
  }

  class Nullable<Parent : WorkspaceEntityBase, Child : WorkspaceEntityBase>(private val parentClass: Class<Parent>) : ReadOnlyProperty<Child, Parent?> {
    private var connectionId: ConnectionId? = null

    override fun getValue(thisRef: Child, property: KProperty<*>): Parent? {
      if (connectionId == null) {
        connectionId = ConnectionId.create(parentClass, thisRef.javaClass, ONE_TO_MANY, true)
      }
      return thisRef.snapshot.extractOneToManyParent(connectionId!!, thisRef.id)
    }
  }
}

class MutableOneToMany<Parent : WorkspaceEntityBase, Child : WorkspaceEntityBase, ModifParent : ModifiableWorkspaceEntityBase<Parent>>(
  private val parentClass: Class<Parent>,
  private val childClass: Class<Child>,
  private val isParentInChildNullable: Boolean
) : ReadWriteProperty<ModifParent, Sequence<Child>> {

  private var connectionId: ConnectionId? = null

  override fun getValue(thisRef: ModifParent, property: KProperty<*>): Sequence<Child> {
    if (connectionId == null) {
      connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_MANY, isParentInChildNullable)
    }
    return thisRef.diff.extractOneToManyChildren(connectionId!!, thisRef)
  }

  override fun setValue(thisRef: ModifParent, property: KProperty<*>, value: Sequence<Child>) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    if (connectionId == null) {
      connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_MANY, isParentInChildNullable)
    }
    thisRef.diff.updateOneToManyChildrenOfParent(connectionId!!, thisRef, value)
  }
}

class MutableManyToOne private constructor() {
  class NotNull<Parent : WorkspaceEntityBase, Child : WorkspaceEntityBase, ModifChild : ModifiableWorkspaceEntityBase<Child>>(
    private val childClass: Class<Child>,
    private val parentClass: Class<Parent>
  ) : ReadWriteProperty<ModifChild, Parent> {
    private var connectionId: ConnectionId? = null

    override fun getValue(thisRef: ModifChild, property: KProperty<*>): Parent {
      if (connectionId == null) {
        connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_MANY, false)
      }
      return thisRef.diff.extractOneToManyParent(connectionId!!, thisRef)!!
    }

    override fun setValue(thisRef: ModifChild, property: KProperty<*>, value: Parent) {
      if (!thisRef.modifiable.get()) {
        throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
      }
      if (connectionId == null) {
        connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_MANY, false)
      }
      return thisRef.diff.updateOneToManyParentOfChild(connectionId!!, thisRef, value)
    }
  }

  class Nullable<Parent : WorkspaceEntityBase, Child : WorkspaceEntityBase, ModifChild : ModifiableWorkspaceEntityBase<Child>>(
    private val childClass: Class<Child>,
    private val parentClass: Class<Parent>
  ) : ReadWriteProperty<ModifChild, Parent?> {
    private var connectionId: ConnectionId? = null

    override fun getValue(thisRef: ModifChild, property: KProperty<*>): Parent? {
      if (connectionId == null) {
        connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_MANY, true)
      }
      return thisRef.diff.extractOneToManyParent(connectionId!!, thisRef)
    }

    override fun setValue(thisRef: ModifChild, property: KProperty<*>, value: Parent?) {
      if (!thisRef.modifiable.get()) {
        throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
      }
      if (connectionId == null) {
        connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_MANY, true)
      }
      return thisRef.diff.updateOneToManyParentOfChild(connectionId!!, thisRef, value)
    }
  }
}