// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage.references

import com.intellij.workspace.api.pstorage.ConnectionId
import com.intellij.workspace.api.pstorage.ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY
import com.intellij.workspace.api.pstorage.PId
import com.intellij.workspace.api.pstorage.PModifiableTypedEntity
import com.intellij.workspace.api.pstorage.PTypedEntity
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

class OneToAbstractMany<T : PTypedEntity, SUBT : PTypedEntity>(private val childClass: KClass<SUBT>) : ReadOnlyProperty<T, Sequence<SUBT>> {

  private lateinit var connectionId: ConnectionId<T, SUBT>

  override fun getValue(thisRef: T, property: KProperty<*>): Sequence<SUBT> {
    return thisRef.snapshot.extractOneToAbstractManyChildren(connectionId, thisRef.id as PId<T>)
  }

  operator fun provideDelegate(thisRef: T, property: KProperty<*>): ReadOnlyProperty<T, Sequence<SUBT>> {
    connectionId = ConnectionId.create(thisRef.javaClass.kotlin, childClass, ONE_TO_ABSTRACT_MANY, false, false)
    return this
  }
}

class MutableOneToAbstractMany<T : PTypedEntity, SUBT : PTypedEntity, MODT : PModifiableTypedEntity<T>>(
  private val parentClass: KClass<T>,
  private val childClass: KClass<SUBT>
) : ReadWriteProperty<MODT, Sequence<SUBT>> {

  private lateinit var connectionId: ConnectionId<T, SUBT>

  override fun getValue(thisRef: MODT, property: KProperty<*>): Sequence<SUBT> {
    return thisRef.diff.extractOneToAbstractManyChildren(connectionId, thisRef.id as PId<T>)
  }

  override fun setValue(thisRef: MODT, property: KProperty<*>, value: Sequence<SUBT>) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    thisRef.diff.updateOneToAbstractManyChildrenOfParent(connectionId, thisRef.id as PId<T>, value)
  }

  operator fun provideDelegate(thisRef: MODT, property: KProperty<*>): ReadWriteProperty<MODT, Sequence<SUBT>> {
    connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_ABSTRACT_MANY, false, false)
    return this
  }
}
