// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

internal sealed class OneToMany<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>> : ReadOnlyProperty<T, Sequence<SUBT>> {

  lateinit var connectionId: ConnectionId<T, SUBT>

  class HardRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>>(
    private val childClass: KClass<SUBT>
  ) : OneToMany<T, SUBT>() {
    operator fun provideDelegate(thisRef: T, property: KProperty<*>): ReadOnlyProperty<T, Sequence<SUBT>> {
      connectionId = ConnectionId.create(thisRef.javaClass.kotlin, childClass, true)
      return this
    }
  }

  class SoftRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>>(
    private val childClass: KClass<SUBT>
  ) : OneToMany<T, SUBT>() {
    operator fun provideDelegate(thisRef: T, property: KProperty<*>): ReadOnlyProperty<T, Sequence<SUBT>> {
      connectionId = ConnectionId.create(thisRef.javaClass.kotlin, childClass, false)
      return this
    }
  }

  override fun getValue(thisRef: T, property: KProperty<*>): Sequence<SUBT> {
    return thisRef.snapshot.extractChildren(connectionId, thisRef.id)
  }
}

internal class ManyToOne private constructor() {
  internal class HardRef private constructor() {
    class NotNull<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>>(
      private val parentClass: KClass<T>
    ) : ReadOnlyProperty<SUBT, T> {
      lateinit var connectionId: ConnectionId<T, SUBT>

      operator fun provideDelegate(thisRef: SUBT, property: KProperty<*>): ReadOnlyProperty<SUBT, T> {
        connectionId = ConnectionId.create(parentClass, thisRef.javaClass.kotlin, true)
        return this
      }

      override fun getValue(thisRef: SUBT, property: KProperty<*>): T = thisRef.snapshot.extractParent(connectionId, thisRef.id)!!
    }

    class Nullable<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>>(
      private val parentClass: KClass<T>
    ) : ReadOnlyProperty<SUBT, T?> {
      lateinit var connectionId: ConnectionId<T, SUBT>

      operator fun provideDelegate(thisRef: SUBT, property: KProperty<*>): ReadOnlyProperty<SUBT, T?> {
        connectionId = ConnectionId.create(parentClass, thisRef.javaClass.kotlin, true)
        return this
      }

      override fun getValue(thisRef: SUBT, property: KProperty<*>): T? = thisRef.snapshot.extractParent(connectionId, thisRef.id)
    }
  }

  class SoftRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>>(
    private val parentClass: KClass<T>
  ) : ReadOnlyProperty<SUBT, T?> {

    lateinit var connectionId: ConnectionId<T, SUBT>

    operator fun provideDelegate(thisRef: SUBT, property: KProperty<*>): ReadOnlyProperty<SUBT, T?> {
      connectionId = ConnectionId.create(parentClass, thisRef.javaClass.kotlin, false)
      return this
    }

    override fun getValue(thisRef: SUBT, property: KProperty<*>): T? = thisRef.snapshot.extractParent(connectionId, thisRef.id)
  }
}

internal sealed class MutableOneToMany<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODT : PModifiableTypedEntity<T>> : ReadWriteProperty<MODT, Sequence<SUBT>> {

  lateinit var connectionId: ConnectionId<T, SUBT>

  class HardRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODT : PModifiableTypedEntity<T>>(
    private val parentClass: KClass<T>,
    private val childClass: KClass<SUBT>
  ) : MutableOneToMany<T, SUBT, MODT>() {
    operator fun provideDelegate(thisRef: MODT, property: KProperty<*>): ReadWriteProperty<MODT, Sequence<SUBT>> {
      connectionId = ConnectionId.create(parentClass, childClass, true)
      return this
    }
  }

  class SoftRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODT : PModifiableTypedEntity<T>>(
    private val parentClass: KClass<T>,
    private val childClass: KClass<SUBT>
  ) : MutableOneToMany<T, SUBT, MODT>() {
    operator fun provideDelegate(thisRef: MODT, property: KProperty<*>): ReadWriteProperty<MODT, Sequence<SUBT>> {
      connectionId = ConnectionId.create(parentClass, childClass, false)
      return this
    }
  }

  override fun getValue(thisRef: MODT, property: KProperty<*>): Sequence<SUBT> {
    return thisRef.diff.extractChildren(connectionId, thisRef.id)
  }

  override fun setValue(thisRef: MODT, property: KProperty<*>, value: Sequence<SUBT>) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    thisRef.diff.updateChildrenOfParent(connectionId, thisRef.id, value)
  }
}

internal class MutableManyToOne private constructor() {
  internal class HardRef private constructor() {
    class NotNull<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODSUBT : PModifiableTypedEntity<SUBT>>(
      private val childClass: KClass<SUBT>,
      private val parentClass: KClass<T>
    ) : ReadWriteProperty<MODSUBT, T> {
      lateinit var connectionId: ConnectionId<T, SUBT>

      operator fun provideDelegate(thisRef: MODSUBT, property: KProperty<*>): ReadWriteProperty<MODSUBT, T> {
        connectionId = ConnectionId.create(parentClass, childClass, true)
        return this
      }

      override fun getValue(thisRef: MODSUBT, property: KProperty<*>): T {
        return thisRef.diff.extractParent(connectionId, thisRef.id)!!
      }

      override fun setValue(thisRef: MODSUBT, property: KProperty<*>, value: T) {
        if (!thisRef.modifiable.get()) {
          throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
        }
        return thisRef.diff.updateParentOfChild(connectionId, thisRef.id, value)
      }
    }

    class Nullable<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODSUBT : PModifiableTypedEntity<SUBT>>(
      private val childClass: KClass<SUBT>,
      private val parentClass: KClass<T>
    ) : ReadWriteProperty<MODSUBT, T?> {
      lateinit var connectionId: ConnectionId<T, SUBT>

      operator fun provideDelegate(thisRef: MODSUBT, property: KProperty<*>): ReadWriteProperty<MODSUBT, T?> {
        connectionId = ConnectionId.create(parentClass, childClass, true)
        return this
      }

      override fun getValue(thisRef: MODSUBT, property: KProperty<*>): T? {
        return thisRef.diff.extractParent(connectionId, thisRef.id)
      }

      override fun setValue(thisRef: MODSUBT, property: KProperty<*>, value: T?) {
        if (!thisRef.modifiable.get()) {
          throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
        }
        return thisRef.diff.updateParentOfChild(connectionId, thisRef.id, value)
      }
    }
  }

  class SoftRef<T : PTypedEntity<T>, SUBT : PTypedEntity<SUBT>, MODSUBT : PModifiableTypedEntity<SUBT>>(
    private val childClass: KClass<SUBT>,
    private val parentClass: KClass<T>
  ) : ReadWriteProperty<MODSUBT, T?> {

    lateinit var connectionId: ConnectionId<T, SUBT>

    operator fun provideDelegate(thisRef: MODSUBT, property: KProperty<*>): ReadWriteProperty<MODSUBT, T?> {
      connectionId = ConnectionId.create(parentClass, childClass, false)
      return this
    }

    override fun getValue(thisRef: MODSUBT, property: KProperty<*>): T? {
      return thisRef.diff.extractParent(connectionId, thisRef.id)
    }

    override fun setValue(thisRef: MODSUBT, property: KProperty<*>, value: T?) {
      if (!thisRef.modifiable.get()) {
        throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
      }
      return thisRef.diff.updateParentOfChild(connectionId, thisRef.id, value)
    }
  }
}