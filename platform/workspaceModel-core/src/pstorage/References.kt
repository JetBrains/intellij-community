// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

internal sealed class OneToMany<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>>(
  protected val snapshot: PEntityStorage
) : ReadOnlyProperty<T, Sequence<SUBT>> {

  lateinit var connectionId: ConnectionId<T, SUBT>

  class HardRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>>(
    snapshot: PEntityStorage, private val remoteClass: KClass<SUBT>
  ) : OneToMany<T, SUBT>(snapshot) {
    operator fun provideDelegate(thisRef: T, property: KProperty<*>): ReadOnlyProperty<T, Sequence<SUBT>> {
      connectionId = ConnectionId.create(thisRef.javaClass.kotlin, remoteClass, true)
      return this
    }
  }

  class SoftRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>>(
    snapshot: PEntityStorage, private val remoteClass: KClass<SUBT>
  ) : OneToMany<T, SUBT>(snapshot) {
    operator fun provideDelegate(thisRef: T, property: KProperty<*>): ReadOnlyProperty<T, Sequence<SUBT>> {
      connectionId = ConnectionId.create(thisRef.javaClass.kotlin, remoteClass, false)
      return this
    }
  }

  override fun getValue(thisRef: T, property: KProperty<*>): Sequence<SUBT> {
    return snapshot.extractOneToManyRefs(connectionId, thisRef.id)
  }
}

internal sealed class ManyToOne<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>>(
  private val snapshot: PEntityStorage
) : ReadOnlyProperty<SUBT, T?> {

  lateinit var connectionId: ConnectionId<T, SUBT>

  class HardRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>>(
    snapshot: PEntityStorage,
    private val remoteClass: KClass<T>
  ) : ManyToOne<T, SUBT>(snapshot) {
    operator fun provideDelegate(thisRef: SUBT, property: KProperty<*>): ReadOnlyProperty<SUBT, T?> {
      connectionId = ConnectionId.create(remoteClass, thisRef.javaClass.kotlin, true)
      return this
    }
  }

  class SoftRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>>(
    snapshot: PEntityStorage,
    private val remoteClass: KClass<T>
  ) : ManyToOne<T, SUBT>(snapshot) {
    operator fun provideDelegate(thisRef: SUBT, property: KProperty<*>): ReadOnlyProperty<SUBT, T?> {
      connectionId = ConnectionId.create(remoteClass, thisRef.javaClass.kotlin, false)
      return this
    }
  }

  override fun getValue(thisRef: SUBT, property: KProperty<*>): T? = snapshot.extractManyToOneRef(connectionId, thisRef.id)
}

internal sealed class MutableOneToMany<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODT : PModifiableTypedEntity<T>>(
  protected val snapshot: PEntityStorageBuilder
) : ReadWriteProperty<MODT, Sequence<SUBT>> {

  lateinit var connectionId: ConnectionId<T, SUBT>

  class HardRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODT : PModifiableTypedEntity<T>>(
    snapshot: PEntityStorageBuilder,
    private val local: KClass<T>,
    private val remote: KClass<SUBT>
  ) : MutableOneToMany<T, SUBT, MODT>(snapshot) {
    operator fun provideDelegate(thisRef: MODT, property: KProperty<*>): ReadWriteProperty<MODT, Sequence<SUBT>> {
      connectionId = ConnectionId.create(local, remote, true)
      return this
    }
  }

  class SoftRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODT : PModifiableTypedEntity<T>>(
    snapshot: PEntityStorageBuilder,
    private val local: KClass<T>,
    private val remote: KClass<SUBT>
  ) : MutableOneToMany<T, SUBT, MODT>(snapshot) {
    operator fun provideDelegate(thisRef: MODT, property: KProperty<*>): ReadWriteProperty<MODT, Sequence<SUBT>> {
      connectionId = ConnectionId.create(local, remote, false)
      return this
    }
  }

  override fun getValue(thisRef: MODT, property: KProperty<*>): Sequence<SUBT> {
    return snapshot.extractOneToManyRefs(connectionId, thisRef.id)
  }

  override fun setValue(thisRef: MODT, property: KProperty<*>, value: Sequence<SUBT>) {
    snapshot.updateOneToMany(connectionId, thisRef.id, value)
  }
}

internal sealed class MutableManyToOne<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODSUBT : PModifiableTypedEntity<SUBT>>(
  private val snapshot: PEntityStorageBuilder
) : ReadWriteProperty<MODSUBT, T?> {

  lateinit var connectionId: ConnectionId<T, SUBT>

  class HardRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODSUBT : PModifiableTypedEntity<SUBT>>(
    snapshot: PEntityStorageBuilder,
    private val local: KClass<SUBT>,
    private val remote: KClass<T>
  ) : MutableManyToOne<T, SUBT, MODSUBT>(snapshot) {
    operator fun provideDelegate(thisRef: MODSUBT, property: KProperty<*>): ReadWriteProperty<MODSUBT, T?> {
      connectionId = ConnectionId.create(remote, local, true)
      return this
    }
  }

  class SoftRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODSUBT : PModifiableTypedEntity<SUBT>>(
    snapshot: PEntityStorageBuilder,
    private val local: KClass<SUBT>,
    private val remote: KClass<T>
  ) : MutableManyToOne<T, SUBT, MODSUBT>(snapshot) {
    operator fun provideDelegate(thisRef: MODSUBT, property: KProperty<*>): ReadWriteProperty<MODSUBT, T?> {
      connectionId = ConnectionId.create(remote, local, false)
      return this
    }
  }

  override fun getValue(thisRef: MODSUBT, property: KProperty<*>): T? {
    return snapshot.extractManyToOneRef(connectionId, thisRef.id)
  }

  override fun setValue(thisRef: MODSUBT, property: KProperty<*>, value: T?) {
    return snapshot.updateManyToOne(connectionId, thisRef.id, value)
  }
}