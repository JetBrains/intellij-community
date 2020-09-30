// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl.references

import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY
import com.intellij.workspaceModel.storage.impl.updateOneToAbstractManyChildrenOfParent
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.extractOneToAbstractManyChildren
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class OneToAbstractMany<Parent : WorkspaceEntityBase, Child : WorkspaceEntityBase>(private val childClass: Class<Child>) : ReadOnlyProperty<Parent, Sequence<Child>> {

  private var connectionId: ConnectionId? = null

  override fun getValue(thisRef: Parent, property: KProperty<*>): Sequence<Child> {
    if (connectionId == null) {
      connectionId = ConnectionId.create(thisRef.javaClass, childClass, ONE_TO_ABSTRACT_MANY, true, false)
    }
    return thisRef.snapshot.extractOneToAbstractManyChildren(connectionId!!, thisRef.id)
  }
}

class MutableOneToAbstractMany<Parent : WorkspaceEntityBase, Child : WorkspaceEntityBase, ModifParent : ModifiableWorkspaceEntityBase<Parent>>(
  private val parentClass: Class<Parent>,
  private val childClass: Class<Child>
) : ReadWriteProperty<ModifParent, Sequence<Child>> {

  private var connectionId: ConnectionId? = null

  override fun getValue(thisRef: ModifParent, property: KProperty<*>): Sequence<Child> {
    if (connectionId == null) {
      connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_ABSTRACT_MANY, true, false)
    }
    return thisRef.diff.extractOneToAbstractManyChildren(connectionId!!, thisRef.id)
  }

  override fun setValue(thisRef: ModifParent, property: KProperty<*>, value: Sequence<Child>) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    if (connectionId == null) {
      connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_ABSTRACT_MANY, true, false)
    }
    thisRef.diff.updateOneToAbstractManyChildrenOfParent(connectionId!!, thisRef.id, value)
  }
}
