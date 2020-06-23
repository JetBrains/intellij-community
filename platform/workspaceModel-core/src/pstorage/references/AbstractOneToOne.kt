// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage.references

import com.intellij.workspace.api.pstorage.ConnectionId
import com.intellij.workspace.api.pstorage.PId
import com.intellij.workspace.api.pstorage.PModifiableTypedEntity
import com.intellij.workspace.api.pstorage.PTypedEntity
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

class OneToAbstractOneChild<T : PTypedEntity, SUBT : PTypedEntity>(private val parentClass: KClass<T>) : ReadOnlyProperty<SUBT, T> {

  private lateinit var connectionId: ConnectionId<T, SUBT>

  override fun getValue(thisRef: SUBT, property: KProperty<*>): T {
    return thisRef.snapshot.extractOneToAbstractOneParent(connectionId, thisRef.id as PId<SUBT>)!!
  }

  operator fun provideDelegate(thisRef: SUBT, property: KProperty<*>): ReadOnlyProperty<SUBT, T> {
    connectionId = ConnectionId.create(parentClass, thisRef.javaClass.kotlin, ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, false, false)
    return this
  }
}

class MutableOneToAbstractOneChild<T : PTypedEntity, SUBT : PTypedEntity, MODSUBT : PModifiableTypedEntity<SUBT>>(
  private val childClass: KClass<SUBT>,
  private val parentClass: KClass<T>
) : ReadWriteProperty<MODSUBT, T> {

  private lateinit var connectionId: ConnectionId<T, SUBT>

  override fun getValue(thisRef: MODSUBT, property: KProperty<*>): T {
    return thisRef.diff.extractOneToAbstractOneParent(connectionId, thisRef.id as PId<SUBT>)!!
  }

  override fun setValue(thisRef: MODSUBT, property: KProperty<*>, value: T) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    thisRef.diff.updateOneToAbstractOneParentOfChild(connectionId, thisRef.id as PId<SUBT>, value)
  }

  operator fun provideDelegate(thisRef: MODSUBT, property: KProperty<*>): ReadWriteProperty<MODSUBT, T> {
    connectionId = ConnectionId.create(parentClass, childClass, ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, false, false)
    return this
  }
}
