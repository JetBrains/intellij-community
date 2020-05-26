// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage.references

import com.intellij.workspace.api.pstorage.*
import com.intellij.workspace.api.pstorage.ConnectionId
import com.intellij.workspace.api.pstorage.ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY
import com.intellij.workspace.api.pstorage.updateOneToAbstractManyChildrenOfParent
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class OneToAbstractMany<T : PTypedEntity, SUBT : PTypedEntity>(private val childClass: Class<SUBT>) : ReadOnlyProperty<T, Sequence<SUBT>> {

  private var connectionId: ConnectionId? = null

  override fun getValue(thisRef: T, property: KProperty<*>): Sequence<SUBT> {
    if (connectionId == null) {
      connectionId = ConnectionId.create(thisRef.javaClass, childClass, ONE_TO_ABSTRACT_MANY, false, false)
    }
    return thisRef.snapshot.extractOneToAbstractManyChildren(connectionId!!, thisRef.id)
  }
}

class MutableOneToAbstractMany<T : PTypedEntity, SUBT : PTypedEntity, MODT : PModifiableTypedEntity<T>>(
  private val parentClass: Class<T>,
  private val childClass: Class<SUBT>
) : ReadWriteProperty<MODT, Sequence<SUBT>> {

  private var connectionId: ConnectionId? = null

  override fun getValue(thisRef: MODT, property: KProperty<*>): Sequence<SUBT> {
    if (connectionId == null) {
      connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_ABSTRACT_MANY, false, false)
    }
    return thisRef.diff.extractOneToAbstractManyChildren(connectionId!!, thisRef.id)
  }

  override fun setValue(thisRef: MODT, property: KProperty<*>, value: Sequence<SUBT>) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    if (connectionId == null) {
      connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_ABSTRACT_MANY, false, false)
    }
    thisRef.diff.updateOneToAbstractManyChildrenOfParent(connectionId!!, thisRef.id, value)
  }
}
