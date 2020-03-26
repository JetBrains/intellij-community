// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

sealed class OneToMany<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>>(
  private val snapshot: PEntityStorage,
  private val remote: KProperty1<SUBT, T?>
) : ReadOnlyProperty<T, Sequence<SUBT>> {

  class HardRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>>(
    snapshot: PEntityStorage, remote: KProperty1<SUBT, T?>
  ) : OneToMany<T, SUBT>(snapshot, remote)

  class SoftRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>>(
    snapshot: PEntityStorage, remote: KProperty1<SUBT, T?>
  ) : OneToMany<T, SUBT>(snapshot, remote)

  override fun getValue(thisRef: T, property: KProperty<*>): Sequence<SUBT> {
    return snapshot.extractRefs(property as KProperty1<T, Sequence<SUBT>>, remote, thisRef.id)
  }
}

sealed class ManyToOne<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>>(
  private val snapshot: PEntityStorage,
  private val remote: KProperty1<T, Sequence<SUBT>>
) : ReadOnlyProperty<SUBT, T?> {

  class HardRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>>(
    snapshot: PEntityStorage,
    remote: KProperty1<T, Sequence<SUBT>>
  ) : ManyToOne<T, SUBT>(snapshot, remote)

  class SoftRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>>(
    snapshot: PEntityStorage,
    remote: KProperty1<T, Sequence<SUBT>>
  ) : ManyToOne<T, SUBT>(snapshot, remote)

  override fun getValue(thisRef: SUBT, property: KProperty<*>): T? {
    return snapshot.extractBackRef(property as KProperty1<SUBT, T?>, remote, thisRef.id)
  }
}

sealed class MutableOneToMany<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODT : PModifiableTypedEntity<T>>(
  private val snapshot: PEntityStorageBuilder,
  private val local: KProperty1<T, Sequence<SUBT>>,
  private val remote: KProperty1<SUBT, T?>
) : ReadWriteProperty<MODT, Sequence<SUBT>> {

  class HardRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODT : PModifiableTypedEntity<T>>(
    snapshot: PEntityStorageBuilder,
    local: KProperty1<T, Sequence<SUBT>>,
    remote: KProperty1<SUBT, T?>
  ) : MutableOneToMany<T, SUBT, MODT>(snapshot, local, remote)

  class SoftRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODT : PModifiableTypedEntity<T>>(
    snapshot: PEntityStorageBuilder,
    local: KProperty1<T, Sequence<SUBT>>,
    remote: KProperty1<SUBT, T?>
  ) : MutableOneToMany<T, SUBT, MODT>(snapshot, local, remote)

  override fun getValue(thisRef: MODT, property: KProperty<*>): Sequence<SUBT> {
    return snapshot.extractRefs(local, remote, thisRef.id)
  }

  override fun setValue(thisRef: MODT, property: KProperty<*>, value: Sequence<SUBT>) {
    snapshot.updateRef(local, remote, thisRef.id, value)
  }
}

sealed class MutableManyToOne<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODSUBT : PModifiableTypedEntity<SUBT>>(
  private val snapshot: PEntityStorageBuilder,
  private val local: KProperty1<SUBT, T?>,
  private val remote: KProperty1<T, Sequence<SUBT>>
) : ReadWriteProperty<MODSUBT, T?> {

  class HardRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODSUBT : PModifiableTypedEntity<SUBT>>(
    snapshot: PEntityStorageBuilder,
    local: KProperty1<SUBT, T?>,
    remote: KProperty1<T, Sequence<SUBT>>
  ) : MutableManyToOne<T, SUBT, MODSUBT>(snapshot, local, remote)

  class SoftRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODSUBT : PModifiableTypedEntity<SUBT>>(
    snapshot: PEntityStorageBuilder,
    local: KProperty1<SUBT, T?>,
    remote: KProperty1<T, Sequence<SUBT>>
  ) : MutableManyToOne<T, SUBT, MODSUBT>(snapshot, local, remote)

  override fun getValue(thisRef: MODSUBT, property: KProperty<*>): T? {
    return snapshot.extractBackRef(local, remote, thisRef.id)
  }

  override fun setValue(thisRef: MODSUBT, property: KProperty<*>, value: T?) {
    return snapshot.updateBackRef(local, remote, thisRef.id, value)
  }
}