// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl.references

import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.updateOneToAbstractOneParentOfChild
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.extractOneToAbstractOneParent
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class OneToAbstractOneChild<T : WorkspaceEntityBase, SUBT : WorkspaceEntityBase>(private val parentClass: Class<T>) : ReadOnlyProperty<SUBT, T> {

  private var connectionId: ConnectionId? = null

  override fun getValue(thisRef: SUBT, property: KProperty<*>): T {
    if (connectionId == null) {
      connectionId = ConnectionId.create(parentClass, thisRef.javaClass, ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, false,
                                         true)
    }
    return thisRef.snapshot.extractOneToAbstractOneParent(connectionId!!, thisRef.id)!!
  }
}

class MutableOneToAbstractOneChild<T : WorkspaceEntityBase, SUBT : WorkspaceEntityBase, MODSUBT : ModifiableWorkspaceEntityBase<SUBT>>(
  private val childClass: Class<SUBT>,
  private val parentClass: Class<T>
) : ReadWriteProperty<MODSUBT, T> {

  private var connectionId: ConnectionId? = null

  override fun getValue(thisRef: MODSUBT, property: KProperty<*>): T {
    if (connectionId == null) {
      connectionId = ConnectionId.create(parentClass, childClass, ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, false, true)
    }
    return thisRef.diff.extractOneToAbstractOneParent(connectionId!!, thisRef.id)!!
  }

  override fun setValue(thisRef: MODSUBT, property: KProperty<*>, value: T) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    if (connectionId == null) {
      connectionId = ConnectionId.create(parentClass, childClass, ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, false, true)
    }
    thisRef.diff.updateOneToAbstractOneParentOfChild(connectionId!!, thisRef.id, value)
  }
}
