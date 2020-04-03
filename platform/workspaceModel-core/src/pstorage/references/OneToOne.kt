// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage.references

import com.intellij.workspace.api.pstorage.ConnectionId
import com.intellij.workspace.api.pstorage.PModifiableTypedEntity
import com.intellij.workspace.api.pstorage.PTypedEntity
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

sealed class OneToOneParent<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>> : ReadOnlyProperty<T, SUBT?> {
  internal lateinit var connectionId: ConnectionId<T, SUBT>

  class HardRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>>(private val childClass: KClass<SUBT>) : OneToOneParent<T, SUBT>() {
    operator fun provideDelegate(thisRef: T, property: KProperty<*>): ReadOnlyProperty<T, SUBT?> {
      connectionId = ConnectionId.create(thisRef.javaClass.kotlin, childClass, true)
      return this
    }
  }

  class SoftRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>>(private val childClass: KClass<SUBT>) : OneToOneParent<T, SUBT>() {
    operator fun provideDelegate(thisRef: T, property: KProperty<*>): ReadOnlyProperty<T, SUBT?> {
      connectionId = ConnectionId.create(thisRef.javaClass.kotlin, childClass, false)
      return this
    }
  }

  override fun getValue(thisRef: T, property: KProperty<*>): SUBT? = thisRef.snapshot.extractOneToOneChild(connectionId, thisRef.id)
}

sealed class OneToOneChild<SUBT : PTypedEntity<SUBT>, T : PTypedEntity<T>> : ReadOnlyProperty<SUBT, T?> {
  internal lateinit var connectionId: ConnectionId<T, SUBT>

  class HardRef<SUBT : PTypedEntity<SUBT>, T : PTypedEntity<T>>(private val parentClass: KClass<T>) : OneToOneChild<SUBT, T>() {
    operator fun provideDelegate(thisRef: SUBT, property: KProperty<*>): ReadOnlyProperty<SUBT, T?> {
      connectionId = ConnectionId.create(parentClass, thisRef.javaClass.kotlin, true)
      return this
    }
  }

  class SoftRef<SUBT : PTypedEntity<SUBT>, T : PTypedEntity<T>>(private val parentClass: KClass<T>) : OneToOneChild<SUBT, T>() {
    operator fun provideDelegate(thisRef: SUBT, property: KProperty<*>): ReadOnlyProperty<SUBT, T?> {
      connectionId = ConnectionId.create(parentClass, thisRef.javaClass.kotlin, false)
      return this
    }
  }

  override fun getValue(thisRef: SUBT, property: KProperty<*>): T? = thisRef.snapshot.extractOneToOneParent(connectionId, thisRef.id)
}

internal sealed class MutableOneToOneParent<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODT : PModifiableTypedEntity<T>> : ReadWriteProperty<MODT, SUBT?> {

  lateinit var connectionId: ConnectionId<T, SUBT>

  class HardRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODT : PModifiableTypedEntity<T>>(
    private val parentClass: KClass<T>,
    private val childClass: KClass<SUBT>
  ) : MutableOneToOneParent<T, SUBT, MODT>() {
    operator fun provideDelegate(thisRef: MODT, property: KProperty<*>): ReadWriteProperty<MODT, SUBT?> {
      connectionId = ConnectionId.create(parentClass, childClass, true)
      return this
    }
  }

  class SoftRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODT : PModifiableTypedEntity<T>>(
    private val parentClass: KClass<T>,
    private val childClass: KClass<SUBT>
  ) : MutableOneToOneParent<T, SUBT, MODT>() {
    operator fun provideDelegate(thisRef: MODT, property: KProperty<*>): ReadWriteProperty<MODT, SUBT?> {
      connectionId = ConnectionId.create(parentClass, childClass, false)
      return this
    }
  }

  override fun getValue(thisRef: MODT, property: KProperty<*>): SUBT? {
    return thisRef.diff.extractOneToOneChild(connectionId, thisRef.id)
  }

  override fun setValue(thisRef: MODT, property: KProperty<*>, value: SUBT?) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    thisRef.diff.updateOneToOneChildOfParent(connectionId, thisRef.id, value)
  }
}

internal sealed class MutableOneToOneChild<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODSUBT : PModifiableTypedEntity<SUBT>> : ReadWriteProperty<MODSUBT, T?> {

  lateinit var connectionId: ConnectionId<T, SUBT>

  class HardRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODSUBT : PModifiableTypedEntity<SUBT>>(
    private val parentClass: KClass<T>,
    private val childClass: KClass<SUBT>
  ) : MutableOneToOneChild<T, SUBT, MODSUBT>() {
    operator fun provideDelegate(thisRef: MODSUBT, property: KProperty<*>): ReadWriteProperty<MODSUBT, T?> {
      connectionId = ConnectionId.create(parentClass, childClass, true)
      return this
    }
  }

  class SoftRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODSUBT : PModifiableTypedEntity<SUBT>>(
    private val parentClass: KClass<T>,
    private val childClass: KClass<SUBT>
  ) : MutableOneToOneChild<T, SUBT, MODSUBT>() {
    operator fun provideDelegate(thisRef: MODSUBT, property: KProperty<*>): ReadWriteProperty<MODSUBT, T?> {
      connectionId = ConnectionId.create(parentClass, childClass, false)
      return this
    }
  }

  override fun getValue(thisRef: MODSUBT, property: KProperty<*>): T? {
    return thisRef.diff.extractOneToOneParent(connectionId, thisRef.id)
  }

  override fun setValue(thisRef: MODSUBT, property: KProperty<*>, value: T?) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    thisRef.diff.updateOneToOneParentOfChild(connectionId, thisRef.id, value)
  }
}
