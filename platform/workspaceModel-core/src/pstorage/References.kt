// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

internal sealed class OneToMany<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>> : ReadOnlyProperty<T, Sequence<SUBT>> {

  lateinit var connectionId: ConnectionId<T, SUBT>

  class HardRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>>(
    private val remoteClass: KClass<SUBT>
  ) : OneToMany<T, SUBT>() {
    operator fun provideDelegate(thisRef: T, property: KProperty<*>): ReadOnlyProperty<T, Sequence<SUBT>> {
      connectionId = ConnectionId.create(thisRef.javaClass.kotlin, remoteClass, true)
      return this
    }
  }

  class SoftRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>>(
    private val remoteClass: KClass<SUBT>
  ) : OneToMany<T, SUBT>() {
    operator fun provideDelegate(thisRef: T, property: KProperty<*>): ReadOnlyProperty<T, Sequence<SUBT>> {
      connectionId = ConnectionId.create(thisRef.javaClass.kotlin, remoteClass, false)
      return this
    }
  }

  override fun getValue(thisRef: T, property: KProperty<*>): Sequence<SUBT> {
    return thisRef.snapshot.extractOneToManyRefs(connectionId, thisRef.id)
  }
}

internal sealed class ManyToOne<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>> : ReadOnlyProperty<SUBT, T?> {

  lateinit var connectionId: ConnectionId<T, SUBT>

  class HardRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>>(
    private val remoteClass: KClass<T>
  ) : ManyToOne<T, SUBT>() {
    operator fun provideDelegate(thisRef: SUBT, property: KProperty<*>): ReadOnlyProperty<SUBT, T?> {
      connectionId = ConnectionId.create(remoteClass, thisRef.javaClass.kotlin, true)
      return this
    }
  }

  class SoftRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>>(
    private val remoteClass: KClass<T>
  ) : ManyToOne<T, SUBT>() {
    operator fun provideDelegate(thisRef: SUBT, property: KProperty<*>): ReadOnlyProperty<SUBT, T?> {
      connectionId = ConnectionId.create(remoteClass, thisRef.javaClass.kotlin, false)
      return this
    }
  }

  override fun getValue(thisRef: SUBT, property: KProperty<*>): T? = thisRef.snapshot.extractManyToOneRef(connectionId, thisRef.id)
}

internal sealed class MutableOneToMany<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODT : PModifiableTypedEntity<T>> : ReadWriteProperty<MODT, Sequence<SUBT>> {

  lateinit var connectionId: ConnectionId<T, SUBT>

  class HardRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODT : PModifiableTypedEntity<T>>(
    private val local: KClass<T>,
    private val remote: KClass<SUBT>
  ) : MutableOneToMany<T, SUBT, MODT>() {
    operator fun provideDelegate(thisRef: MODT, property: KProperty<*>): ReadWriteProperty<MODT, Sequence<SUBT>> {
      connectionId = ConnectionId.create(local, remote, true)
      return this
    }
  }

  class SoftRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODT : PModifiableTypedEntity<T>>(
    private val local: KClass<T>,
    private val remote: KClass<SUBT>
  ) : MutableOneToMany<T, SUBT, MODT>() {
    operator fun provideDelegate(thisRef: MODT, property: KProperty<*>): ReadWriteProperty<MODT, Sequence<SUBT>> {
      connectionId = ConnectionId.create(local, remote, false)
      return this
    }
  }

  override fun getValue(thisRef: MODT, property: KProperty<*>): Sequence<SUBT> {
    return thisRef.diff.extractOneToManyRefs(connectionId, thisRef.id)
  }

  override fun setValue(thisRef: MODT, property: KProperty<*>, value: Sequence<SUBT>) {
    thisRef.diff.updateOneToMany(connectionId, thisRef.id, value)
  }
}

internal sealed class MutableManyToOne<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODSUBT : PModifiableTypedEntity<SUBT>> : ReadWriteProperty<MODSUBT, T?> {

  lateinit var connectionId: ConnectionId<T, SUBT>

  class HardRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODSUBT : PModifiableTypedEntity<SUBT>>(
    private val local: KClass<SUBT>,
    private val remote: KClass<T>
  ) : MutableManyToOne<T, SUBT, MODSUBT>() {
    operator fun provideDelegate(thisRef: MODSUBT, property: KProperty<*>): ReadWriteProperty<MODSUBT, T?> {
      connectionId = ConnectionId.create(remote, local, true)
      return this
    }
  }

  class SoftRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODSUBT : PModifiableTypedEntity<SUBT>>(
    private val local: KClass<SUBT>,
    private val remote: KClass<T>
  ) : MutableManyToOne<T, SUBT, MODSUBT>() {
    operator fun provideDelegate(thisRef: MODSUBT, property: KProperty<*>): ReadWriteProperty<MODSUBT, T?> {
      connectionId = ConnectionId.create(remote, local, false)
      return this
    }
  }

  override fun getValue(thisRef: MODSUBT, property: KProperty<*>): T? {
    return thisRef.diff.extractManyToOneRef(connectionId, thisRef.id)
  }

  override fun setValue(thisRef: MODSUBT, property: KProperty<*>, value: T?) {
    return thisRef.diff.updateManyToOne(connectionId, thisRef.id, value)
  }
}