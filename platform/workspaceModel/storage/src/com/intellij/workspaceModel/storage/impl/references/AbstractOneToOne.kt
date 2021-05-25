// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl.references

import com.intellij.workspaceModel.storage.impl.*
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.extractOneToAbstractOneParent
import com.intellij.workspaceModel.storage.impl.updateOneToAbstractOneParentOfChild
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class OneToAbstractOneParent<Parent : WorkspaceEntityBase, Child : WorkspaceEntityBase>(private val childClass: Class<Child>) : ReadOnlyProperty<Parent, Child> {

  private var connectionId: ConnectionId? = null

  override fun getValue(thisRef: Parent, property: KProperty<*>): Child {
    if (connectionId == null) {
      connectionId = ConnectionId.create(thisRef.javaClass, childClass, ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, true,
                                         false)
    }
    return thisRef.snapshot.extractAbstractOneToOneChild(connectionId!!, thisRef.id.asParent())!!
  }
}

class OneToAbstractOneChild<Parent : WorkspaceEntityBase, Child : WorkspaceEntityBase>(private val parentClass: Class<Parent>) : ReadOnlyProperty<Child, Parent> {

  private var connectionId: ConnectionId? = null

  override fun getValue(thisRef: Child, property: KProperty<*>): Parent {
    if (connectionId == null) {
      connectionId = ConnectionId.create(parentClass, thisRef.javaClass, ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, false,
                                         true)
    }
    return thisRef.snapshot.extractOneToAbstractOneParent(connectionId!!, thisRef.id.asChild())!!
  }
}

class MutableOneToAbstractOneParent<Parent : WorkspaceEntityBase, Child : WorkspaceEntityBase, ModifParent : ModifiableWorkspaceEntityBase<Parent>>(
  private val parentClass: Class<Parent>,
  private val childClass: Class<Child>
) : ReadWriteProperty<ModifParent, Child> {

  private var connectionId: ConnectionId? = null

  override fun getValue(thisRef: ModifParent, property: KProperty<*>): Child {
    if (connectionId == null) {
      connectionId = ConnectionId.create(parentClass, childClass, ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, true, false)
    }
    return thisRef.diff.extractAbstractOneToOneChild(connectionId!!, thisRef.id.asParent())!!
  }

  override fun setValue(thisRef: ModifParent, property: KProperty<*>, value: Child) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    if (connectionId == null) {
      connectionId = ConnectionId.create(parentClass, childClass, ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, true, false)
    }
    thisRef.diff.updateOneToAbstractOneChildOfParent(connectionId!!, thisRef.id.asParent(), value)
  }
}

class MutableOneToAbstractOneChild<Parent : WorkspaceEntityBase, Child : WorkspaceEntityBase, ModifChild : ModifiableWorkspaceEntityBase<Child>>(
  private val childClass: Class<Child>,
  private val parentClass: Class<Parent>
) : ReadWriteProperty<ModifChild, Parent> {

  private var connectionId: ConnectionId? = null

  override fun getValue(thisRef: ModifChild, property: KProperty<*>): Parent {
    if (connectionId == null) {
      connectionId = ConnectionId.create(parentClass, childClass, ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, false, true)
    }
    return thisRef.diff.extractOneToAbstractOneParent(connectionId!!, thisRef.id.asChild())!!
  }

  override fun setValue(thisRef: ModifChild, property: KProperty<*>, value: Parent) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    if (connectionId == null) {
      connectionId = ConnectionId.create(parentClass, childClass, ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, false, true)
    }
    thisRef.diff.updateOneToAbstractOneParentOfChild(connectionId!!, thisRef.id.asChild(), value)
  }
}
