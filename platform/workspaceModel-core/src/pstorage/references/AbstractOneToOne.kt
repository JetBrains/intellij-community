// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage.references

import com.intellij.workspace.api.pstorage.*
import com.intellij.workspace.api.pstorage.ConnectionId
import com.intellij.workspace.api.pstorage.updateOneToAbstractOneParentOfChild
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class OneToAbstractOneChild<T : PTypedEntity, SUBT : PTypedEntity>(private val parentClass: Class<T>) : ReadOnlyProperty<SUBT, T> {

  private var connectionId: ConnectionId? = null

  override fun getValue(thisRef: SUBT, property: KProperty<*>): T {
    if (connectionId == null) {
      connectionId = ConnectionId.create(parentClass, thisRef.javaClass, ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, false,
                                         false)
    }
    return thisRef.snapshot.extractOneToAbstractOneParent(connectionId!!, thisRef.id)!!
  }
}

class MutableOneToAbstractOneChild<T : PTypedEntity, SUBT : PTypedEntity, MODSUBT : PModifiableTypedEntity<SUBT>>(
  private val childClass: Class<SUBT>,
  private val parentClass: Class<T>
) : ReadWriteProperty<MODSUBT, T> {

  private var connectionId: ConnectionId? = null

  override fun getValue(thisRef: MODSUBT, property: KProperty<*>): T {
    if (connectionId == null) {
      connectionId = ConnectionId.create(parentClass, childClass, ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, false, false)
    }
    return thisRef.diff.extractOneToAbstractOneParent(connectionId!!, thisRef.id)!!
  }

  override fun setValue(thisRef: MODSUBT, property: KProperty<*>, value: T) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    if (connectionId == null) {
      connectionId = ConnectionId.create(parentClass, childClass, ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, false, false)
    }
    thisRef.diff.updateOneToAbstractOneParentOfChild(connectionId!!, thisRef.id, value)
  }
}
