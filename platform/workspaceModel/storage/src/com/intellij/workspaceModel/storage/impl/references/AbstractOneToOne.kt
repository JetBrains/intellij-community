// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl.references

import com.intellij.workspaceModel.storage.impl.*
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class OneToAbstractOneParent<Parent : WorkspaceEntityBase, Child : WorkspaceEntityBase>(private val childClass: Class<Child>) : ReadOnlyProperty<Parent, Child?> {

  private var connectionId: ConnectionId? = null

  override fun getValue(thisRef: Parent, property: KProperty<*>): Child? {
    if (connectionId == null) {
      connectionId = ConnectionId.create(thisRef.javaClass, childClass, ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, true)
    }
    return thisRef.snapshot.extractAbstractOneToOneChild(connectionId!!, thisRef.id.asParent())
  }
}

class MutableOneToAbstractOneParent<Parent : WorkspaceEntityBase, Child : WorkspaceEntityBase, ModifParent : ModifiableWorkspaceEntityBase<Parent>>(
  private val parentClass: Class<Parent>,
  private val childClass: Class<Child>
) : ReadWriteProperty<ModifParent, Child?> {

  private var connectionId: ConnectionId? = null

  override fun getValue(thisRef: ModifParent, property: KProperty<*>): Child? {
    if (connectionId == null) {
      connectionId = ConnectionId.create(parentClass, childClass, ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, true)
    }
    return thisRef.diff.extractAbstractOneToOneChild(connectionId!!, thisRef)
  }

  override fun setValue(thisRef: ModifParent, property: KProperty<*>, value: Child?) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    if (connectionId == null) {
      connectionId = ConnectionId.create(parentClass, childClass, ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, true)
    }
    thisRef.diff.updateOneToAbstractOneChildOfParent(connectionId!!, thisRef, value)
  }
}
