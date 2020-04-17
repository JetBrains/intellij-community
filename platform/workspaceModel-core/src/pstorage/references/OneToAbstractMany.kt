// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage.references

import com.intellij.workspace.api.pstorage.ConnectionId
import com.intellij.workspace.api.pstorage.ConnectionId.ConnectionType.*
import com.intellij.workspace.api.pstorage.PId
import com.intellij.workspace.api.pstorage.PModifiableTypedEntity
import com.intellij.workspace.api.pstorage.PTypedEntity
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

sealed class OneToAbstractMany<T : PTypedEntity, SUBT : PTypedEntity> : ReadOnlyProperty<T, Sequence<SUBT>> {

  internal lateinit var connectionId: ConnectionId<T, SUBT>

  class HardRef<T : PTypedEntity, SUBT : PTypedEntity>(private val childClass: KClass<SUBT>) : OneToAbstractMany<T, SUBT>() {
    operator fun provideDelegate(thisRef: T, property: KProperty<*>): ReadOnlyProperty<T, Sequence<SUBT>> {
      connectionId = ConnectionId.create(thisRef.javaClass.kotlin, childClass, true, ONE_TO_ABSTRACT_MANY, false, false)
      return this
    }
  }

  override fun getValue(thisRef: T, property: KProperty<*>): Sequence<SUBT> {
    return thisRef.snapshot.extractOneToAbstractManyChildren(connectionId, thisRef.id as PId<T>)
  }
}

internal sealed class MutableOneToAbstractMany<T : PTypedEntity, SUBT : PTypedEntity, MODT : PModifiableTypedEntity<T>> : ReadWriteProperty<MODT, Sequence<SUBT>> {

  lateinit var connectionId: ConnectionId<T, SUBT>

  class HardRef<T : PTypedEntity, SUBT : PTypedEntity, MODT : PModifiableTypedEntity<T>>(
    private val parentClass: KClass<T>,
    private val childClass: KClass<SUBT>
  ) : MutableOneToAbstractMany<T, SUBT, MODT>() {
    operator fun provideDelegate(thisRef: MODT, property: KProperty<*>): ReadWriteProperty<MODT, Sequence<SUBT>> {
      connectionId = ConnectionId.create(parentClass, childClass, true, ONE_TO_ABSTRACT_MANY, false, false)
      return this
    }
  }

  override fun getValue(thisRef: MODT, property: KProperty<*>): Sequence<SUBT> {
    return thisRef.diff.extractOneToAbstractManyChildren(connectionId, thisRef.id as PId<T>)
  }

  override fun setValue(thisRef: MODT, property: KProperty<*>, value: Sequence<SUBT>) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    thisRef.diff.updateOneToAbstractManyChildrenOfParent(connectionId, thisRef.id as PId<T>, value)
  }
}
