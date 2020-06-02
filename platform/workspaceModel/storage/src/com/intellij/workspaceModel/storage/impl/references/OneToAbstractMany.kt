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

class OneToAbstractMany<T : WorkspaceEntityBase, SUBT : WorkspaceEntityBase>(private val childClass: Class<SUBT>) : ReadOnlyProperty<T, Sequence<SUBT>> {

  private var connectionId: ConnectionId? = null

  override fun getValue(thisRef: T, property: KProperty<*>): Sequence<SUBT> {
    if (connectionId == null) {
      connectionId = ConnectionId.create(thisRef.javaClass, childClass, ONE_TO_ABSTRACT_MANY, true, false)
    }
    return thisRef.snapshot.extractOneToAbstractManyChildren(connectionId!!, thisRef.id)
  }
}

class MutableOneToAbstractMany<T : WorkspaceEntityBase, SUBT : WorkspaceEntityBase, MODT : ModifiableWorkspaceEntityBase<T>>(
  private val parentClass: Class<T>,
  private val childClass: Class<SUBT>
) : ReadWriteProperty<MODT, Sequence<SUBT>> {

  private var connectionId: ConnectionId? = null

  override fun getValue(thisRef: MODT, property: KProperty<*>): Sequence<SUBT> {
    if (connectionId == null) {
      connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_ABSTRACT_MANY, true, false)
    }
    return thisRef.diff.extractOneToAbstractManyChildren(connectionId!!, thisRef.id)
  }

  override fun setValue(thisRef: MODT, property: KProperty<*>, value: Sequence<SUBT>) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    if (connectionId == null) {
      connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_ABSTRACT_MANY, true, false)
    }
    thisRef.diff.updateOneToAbstractManyChildrenOfParent(connectionId!!, thisRef.id, value)
  }
}
