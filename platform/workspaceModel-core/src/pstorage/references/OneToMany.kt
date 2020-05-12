// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage.references

import com.intellij.workspace.api.pstorage.ConnectionId
import com.intellij.workspace.api.pstorage.ConnectionId.ConnectionType.ONE_TO_MANY
import com.intellij.workspace.api.pstorage.PId
import com.intellij.workspace.api.pstorage.PModifiableTypedEntity
import com.intellij.workspace.api.pstorage.PTypedEntity
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

class OneToMany<T : PTypedEntity, SUBT : PTypedEntity>(private val childClass: KClass<SUBT>,
                                                       private val isParentInChildNullable: Boolean) : ReadOnlyProperty<T, Sequence<SUBT>> {

  private lateinit var connectionId: ConnectionId<T, SUBT>

  override fun getValue(thisRef: T, property: KProperty<*>): Sequence<SUBT> {
    return thisRef.snapshot.extractOneToManyChildren(connectionId, thisRef.id as PId<T>)
  }

  operator fun provideDelegate(thisRef: T, property: KProperty<*>): ReadOnlyProperty<T, Sequence<SUBT>> {
    connectionId = ConnectionId.create(thisRef.javaClass.kotlin, childClass, ONE_TO_MANY, isParentInChildNullable, false)
    return this
  }
}

class ManyToOne private constructor() {
  class NotNull<T : PTypedEntity, SUBT : PTypedEntity>(private val parentClass: KClass<T>) : ReadOnlyProperty<SUBT, T> {
    private lateinit var connectionId: ConnectionId<T, SUBT>

    operator fun provideDelegate(thisRef: SUBT, property: KProperty<*>): ReadOnlyProperty<SUBT, T> {
      connectionId = ConnectionId.create(parentClass, thisRef.javaClass.kotlin, ONE_TO_MANY, false, false)
      return this
    }

    override fun getValue(thisRef: SUBT, property: KProperty<*>): T = thisRef.snapshot.extractOneToManyParent(connectionId,
                                                                                                              thisRef.id as PId<SUBT>)!!
  }

  class Nullable<T : PTypedEntity, SUBT : PTypedEntity>(private val parentClass: KClass<T>) : ReadOnlyProperty<SUBT, T?> {
    private lateinit var connectionId: ConnectionId<T, SUBT>

    operator fun provideDelegate(thisRef: SUBT, property: KProperty<*>): ReadOnlyProperty<SUBT, T?> {
      connectionId = ConnectionId.create(parentClass, thisRef.javaClass.kotlin, ONE_TO_MANY, true, false)
      return this
    }

    override fun getValue(thisRef: SUBT, property: KProperty<*>): T? = thisRef.snapshot.extractOneToManyParent(connectionId,
                                                                                                               thisRef.id as PId<SUBT>)
  }
}

sealed class MutableOneToMany<T : PTypedEntity, SUBT : PTypedEntity, MODT : PModifiableTypedEntity<T>>(
  private val parentClass: KClass<T>,
  private val childClass: KClass<SUBT>,
  private val isParentInChildNullable: Boolean
) : ReadWriteProperty<MODT, Sequence<SUBT>> {

  private lateinit var connectionId: ConnectionId<T, SUBT>

  override fun getValue(thisRef: MODT, property: KProperty<*>): Sequence<SUBT> {
    return thisRef.diff.extractOneToManyChildren(connectionId, thisRef.id as PId<T>)
  }

  override fun setValue(thisRef: MODT, property: KProperty<*>, value: Sequence<SUBT>) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    thisRef.diff.updateOneToManyChildrenOfParent(connectionId, thisRef.id as PId<T>, value)
  }

  operator fun provideDelegate(thisRef: MODT, property: KProperty<*>): ReadWriteProperty<MODT, Sequence<SUBT>> {
    connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_MANY, isParentInChildNullable, false)
    return this
  }
}

class MutableManyToOne private constructor() {
  class NotNull<T : PTypedEntity, SUBT : PTypedEntity, MODSUBT : PModifiableTypedEntity<SUBT>>(
    private val childClass: KClass<SUBT>,
    private val parentClass: KClass<T>
  ) : ReadWriteProperty<MODSUBT, T> {
    private lateinit var connectionId: ConnectionId<T, SUBT>

    operator fun provideDelegate(thisRef: MODSUBT, property: KProperty<*>): ReadWriteProperty<MODSUBT, T> {
      connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_MANY, false, false)
      return this
    }

    override fun getValue(thisRef: MODSUBT, property: KProperty<*>): T {
      return thisRef.diff.extractOneToManyParent(connectionId, thisRef.id as PId<SUBT>)!!
    }

    override fun setValue(thisRef: MODSUBT, property: KProperty<*>, value: T) {
      if (!thisRef.modifiable.get()) {
        throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
      }
      return thisRef.diff.updateOneToManyParentOfChild(connectionId, thisRef.id as PId<SUBT>, value)
    }
  }

  class Nullable<T : PTypedEntity, SUBT : PTypedEntity, MODSUBT : PModifiableTypedEntity<SUBT>>(
    private val childClass: KClass<SUBT>,
    private val parentClass: KClass<T>
  ) : ReadWriteProperty<MODSUBT, T?> {
    private lateinit var connectionId: ConnectionId<T, SUBT>

    operator fun provideDelegate(thisRef: MODSUBT, property: KProperty<*>): ReadWriteProperty<MODSUBT, T?> {
      connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_MANY, true, false)
      return this
    }

    override fun getValue(thisRef: MODSUBT, property: KProperty<*>): T? {
      return thisRef.diff.extractOneToManyParent(connectionId, thisRef.id as PId<SUBT>)
    }

    override fun setValue(thisRef: MODSUBT, property: KProperty<*>, value: T?) {
      if (!thisRef.modifiable.get()) {
        throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
      }
      return thisRef.diff.updateOneToManyParentOfChild(connectionId, thisRef.id as PId<SUBT>, value)
    }
  }
}