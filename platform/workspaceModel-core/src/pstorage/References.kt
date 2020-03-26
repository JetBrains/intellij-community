// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.TypedEntity
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.jvm.javaField

fun <E : TypedEntity> KProperty1<E, *>.declaringClass(): Class<E> = this.javaField!!.declaringClass as Class<E>

sealed class OneToMany<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>>(
  protected val snapshot: PEntityStorage,
  protected val remote: KProperty1<SUBT, T?>
) : ReadOnlyProperty<T, Sequence<SUBT>> {

  lateinit var connectionId: ConnectionId
  lateinit var remoteClass: Class<SUBT>

  class HardRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>>(
    snapshot: PEntityStorage, remote: KProperty1<SUBT, T?>
  ) : OneToMany<T, SUBT>(snapshot, remote) {
    operator fun provideDelegate(thisRef: T, property: KProperty<*>): ReadOnlyProperty<T, Sequence<SUBT>> {
      connectionId = ConnectionId.create(property as KProperty1<*, *>, remote, true)
      remoteClass = remote.declaringClass()
      return this
    }
  }

  class SoftRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>>(
    snapshot: PEntityStorage, remote: KProperty1<SUBT, T?>
  ) : OneToMany<T, SUBT>(snapshot, remote) {
    operator fun provideDelegate(thisRef: T, property: KProperty<*>): ReadOnlyProperty<T, Sequence<SUBT>> {
      connectionId = ConnectionId.create(property as KProperty1<*, *>, remote, false)
      remoteClass = remote.declaringClass()
      return this
    }
  }

  override fun getValue(thisRef: T, property: KProperty<*>): Sequence<SUBT> {
    return snapshot.extractOneToManyRefs(connectionId, remoteClass, thisRef.id)
  }
}

sealed class ManyToOne<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>>(
  protected val snapshot: PEntityStorage,
  protected val remote: KProperty1<T, Sequence<SUBT>>
) : ReadOnlyProperty<SUBT, T?> {

  lateinit var connectionId: ConnectionId
  lateinit var remoteClass: Class<T>

  class HardRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>>(
    snapshot: PEntityStorage,
    remote: KProperty1<T, Sequence<SUBT>>
  ) : ManyToOne<T, SUBT>(snapshot, remote) {
    operator fun provideDelegate(thisRef: SUBT, property: KProperty<*>): ReadOnlyProperty<SUBT, T?> {
      connectionId = ConnectionId.create(property as KProperty1<*, *>, remote, true)
      remoteClass = remote.declaringClass()
      return this
    }
  }

  class SoftRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>>(
    snapshot: PEntityStorage,
    remote: KProperty1<T, Sequence<SUBT>>
  ) : ManyToOne<T, SUBT>(snapshot, remote) {
    operator fun provideDelegate(thisRef: SUBT, property: KProperty<*>): ReadOnlyProperty<SUBT, T?> {
      connectionId = ConnectionId.create(property as KProperty1<*, *>, remote, false)
      remoteClass = remote.declaringClass()
      return this
    }
  }

  override fun getValue(thisRef: SUBT, property: KProperty<*>): T? = snapshot.extractManyToOneRef(connectionId, remoteClass, thisRef.id)
}

sealed class MutableOneToMany<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODT : PModifiableTypedEntity<T>>(
  protected val snapshot: PEntityStorageBuilder,
  protected val local: KProperty1<T, Sequence<SUBT>>,
  protected val remote: KProperty1<SUBT, T?>
) : ReadWriteProperty<MODT, Sequence<SUBT>> {

  lateinit var connectionId: ConnectionId
  lateinit var remoteClass: Class<SUBT>

  class HardRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODT : PModifiableTypedEntity<T>>(
    snapshot: PEntityStorageBuilder,
    local: KProperty1<T, Sequence<SUBT>>,
    remote: KProperty1<SUBT, T?>
  ) : MutableOneToMany<T, SUBT, MODT>(snapshot, local, remote) {
    operator fun provideDelegate(thisRef: MODT, property: KProperty<*>): ReadWriteProperty<MODT, Sequence<SUBT>> {
      connectionId = ConnectionId.create(remote, local, true)
      remoteClass = remote.declaringClass()
      return this
    }
  }

  class SoftRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODT : PModifiableTypedEntity<T>>(
    snapshot: PEntityStorageBuilder,
    local: KProperty1<T, Sequence<SUBT>>,
    remote: KProperty1<SUBT, T?>
  ) : MutableOneToMany<T, SUBT, MODT>(snapshot, local, remote) {
    operator fun provideDelegate(thisRef: MODT, property: KProperty<*>): ReadWriteProperty<MODT, Sequence<SUBT>> {
      connectionId = ConnectionId.create(remote, local, false)
      remoteClass = remote.declaringClass()
      return this
    }
  }

  override fun getValue(thisRef: MODT, property: KProperty<*>): Sequence<SUBT> {
    return snapshot.extractOneToManyRefs(connectionId, remoteClass, thisRef.id)
  }

  override fun setValue(thisRef: MODT, property: KProperty<*>, value: Sequence<SUBT>) {
    snapshot.updateOneToMany(connectionId, thisRef.id, value)
  }
}

sealed class MutableManyToOne<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODSUBT : PModifiableTypedEntity<SUBT>>(
  protected val snapshot: PEntityStorageBuilder,
  protected val local: KProperty1<SUBT, T?>,
  protected val remote: KProperty1<T, Sequence<SUBT>>
) : ReadWriteProperty<MODSUBT, T?> {

  lateinit var connectionId: ConnectionId
  lateinit var remoteClass: Class<T>

  class HardRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODSUBT : PModifiableTypedEntity<SUBT>>(
    snapshot: PEntityStorageBuilder,
    local: KProperty1<SUBT, T?>,
    remote: KProperty1<T, Sequence<SUBT>>
  ) : MutableManyToOne<T, SUBT, MODSUBT>(snapshot, local, remote) {
    operator fun provideDelegate(thisRef: MODSUBT, property: KProperty<*>): ReadWriteProperty<MODSUBT, T?> {
      connectionId = ConnectionId.create(local, remote, true)
      remoteClass = remote.declaringClass()
      return this
    }
  }

  class SoftRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODSUBT : PModifiableTypedEntity<SUBT>>(
    snapshot: PEntityStorageBuilder,
    local: KProperty1<SUBT, T?>,
    remote: KProperty1<T, Sequence<SUBT>>
  ) : MutableManyToOne<T, SUBT, MODSUBT>(snapshot, local, remote) {
    operator fun provideDelegate(thisRef: MODSUBT, property: KProperty<*>): ReadWriteProperty<MODSUBT, T?> {
      connectionId = ConnectionId.create(local, remote, false)
      remoteClass = remote.declaringClass()
      return this
    }
  }

  override fun getValue(thisRef: MODSUBT, property: KProperty<*>): T? {
    return snapshot.extractManyToOneRef(connectionId, remoteClass, thisRef.id)
  }

  override fun setValue(thisRef: MODSUBT, property: KProperty<*>, value: T?) {
    return snapshot.updateManyToOne(connectionId, thisRef.id, value)
  }
}