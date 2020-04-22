// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage.references

import com.intellij.workspace.api.pstorage.ConnectionId
import com.intellij.workspace.api.pstorage.ConnectionId.ConnectionType.ONE_TO_ONE
import com.intellij.workspace.api.pstorage.PId
import com.intellij.workspace.api.pstorage.PModifiableTypedEntity
import com.intellij.workspace.api.pstorage.PTypedEntity
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

class OneToOneParent private constructor() {
  class HardRef private constructor() {
    class NotNull<T : PTypedEntity, SUBT : PTypedEntity>(private val childClass: KClass<SUBT>, val isParentInChildNullable: Boolean) : ReadOnlyProperty<T, SUBT> {
      internal lateinit var connectionId: ConnectionId<T, SUBT>

      operator fun provideDelegate(thisRef: T, property: KProperty<*>): ReadOnlyProperty<T, SUBT> {
        connectionId = ConnectionId.create(thisRef.javaClass.kotlin, childClass, true, ONE_TO_ONE, isParentInChildNullable, false)
        return this
      }

      override fun getValue(thisRef: T, property: KProperty<*>): SUBT = thisRef.snapshot.extractOneToOneChild(connectionId,
                                                                                                              thisRef.id as PId<T>)!!
    }

    class Nullable<T : PTypedEntity, SUBT : PTypedEntity>(private val childClass: KClass<SUBT>, val isParentInChildNullable: Boolean) : ReadOnlyProperty<T, SUBT?> {
      internal lateinit var connectionId: ConnectionId<T, SUBT>

      operator fun provideDelegate(thisRef: T, property: KProperty<*>): ReadOnlyProperty<T, SUBT?> {
        connectionId = ConnectionId.create(thisRef.javaClass.kotlin, childClass, true, ONE_TO_ONE, isParentInChildNullable, true)
        return this
      }

      override fun getValue(thisRef: T, property: KProperty<*>): SUBT? = thisRef.snapshot.extractOneToOneChild(connectionId,
                                                                                                               thisRef.id as PId<T>)
    }
  }
}

sealed class OneToOneChild<SUBT : PTypedEntity, T : PTypedEntity> : ReadOnlyProperty<SUBT, T> {
  internal lateinit var connectionId: ConnectionId<T, SUBT>

  class HardRef {
    class NotNull<SUBT : PTypedEntity, T : PTypedEntity>(private val parentClass: KClass<T>, val isChildInParentNullable: Boolean) : ReadOnlyProperty<SUBT, T> {
      internal lateinit var connectionId: ConnectionId<T, SUBT>

      operator fun provideDelegate(thisRef: SUBT, property: KProperty<*>): ReadOnlyProperty<SUBT, T> {
        connectionId = ConnectionId.create(parentClass, thisRef.javaClass.kotlin, true, ONE_TO_ONE, false, isChildInParentNullable)
        return this
      }

      override fun getValue(thisRef: SUBT, property: KProperty<*>): T = thisRef.snapshot.extractOneToOneParent(connectionId,
                                                                                                               thisRef.id as PId<SUBT>)!!
    }

    class Nullable<SUBT : PTypedEntity, T : PTypedEntity>(private val parentClass: KClass<T>, val isChildInParentNullable: Boolean) : ReadOnlyProperty<SUBT, T?> {
      internal lateinit var connectionId: ConnectionId<T, SUBT>

      operator fun provideDelegate(thisRef: SUBT, property: KProperty<*>): ReadOnlyProperty<SUBT, T?> {
        connectionId = ConnectionId.create(parentClass, thisRef.javaClass.kotlin, true, ONE_TO_ONE, true, isChildInParentNullable)
        return this
      }

      override fun getValue(thisRef: SUBT, property: KProperty<*>): T? = thisRef.snapshot.extractOneToOneParent(connectionId,
                                                                                                               thisRef.id as PId<SUBT>)
    }
  }
  override fun getValue(thisRef: SUBT, property: KProperty<*>): T = thisRef.snapshot.extractOneToOneParent(connectionId,
                                                                                                           thisRef.id as PId<SUBT>)!!
}

class MutableOneToOneParent private constructor() {
  class HardRef private constructor() {
    class NotNull<T : PTypedEntity, SUBT : PTypedEntity, MODT : PModifiableTypedEntity<T>>(
      private val parentClass: KClass<T>,
      private val childClass: KClass<SUBT>,
      val isParentInChildNullable: Boolean
    ) : ReadWriteProperty<MODT, SUBT> {

      internal lateinit var connectionId: ConnectionId<T, SUBT>

      operator fun provideDelegate(thisRef: MODT, property: KProperty<*>): ReadWriteProperty<MODT, SUBT> {
        connectionId = ConnectionId.create(parentClass, childClass, true, ONE_TO_ONE, isParentInChildNullable, false)
        return this
      }

      override fun getValue(thisRef: MODT, property: KProperty<*>): SUBT {
        return thisRef.diff.extractOneToOneChild(connectionId, thisRef.id as PId<T>)!!
      }

      override fun setValue(thisRef: MODT, property: KProperty<*>, value: SUBT) {
        if (!thisRef.modifiable.get()) {
          throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
        }
        thisRef.diff.updateOneToOneChildOfParent(connectionId, thisRef.id as PId<T>, value)
      }
    }

    class Nullable<T : PTypedEntity, SUBT : PTypedEntity, MODT : PModifiableTypedEntity<T>>(
      private val parentClass: KClass<T>,
      private val childClass: KClass<SUBT>,
      val isParentInChildNullable: Boolean
    ) : ReadWriteProperty<MODT, SUBT?> {

      internal lateinit var connectionId: ConnectionId<T, SUBT>

      operator fun provideDelegate(thisRef: MODT, property: KProperty<*>): ReadWriteProperty<MODT, SUBT?> {
        connectionId = ConnectionId.create(parentClass, childClass, true, ONE_TO_ONE, isParentInChildNullable, true)
        return this
      }

      override fun getValue(thisRef: MODT, property: KProperty<*>): SUBT? {
        return thisRef.diff.extractOneToOneChild(connectionId, thisRef.id as PId<T>)!!
      }

      override fun setValue(thisRef: MODT, property: KProperty<*>, value: SUBT?) {
        if (!thisRef.modifiable.get()) {
          throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
        }
        thisRef.diff.updateOneToOneChildOfParent(connectionId, thisRef.id as PId<T>, value)
      }
    }
  }
}

sealed class MutableOneToOneChild<T : PTypedEntity, SUBT : PTypedEntity, MODSUBT : PModifiableTypedEntity<SUBT>> : ReadWriteProperty<MODSUBT, T> {

  internal lateinit var connectionId: ConnectionId<T, SUBT>

  class HardRef() {
    class NotNull<T : PTypedEntity, SUBT : PTypedEntity, MODSUBT : PModifiableTypedEntity<SUBT>>(
      private val childClass: KClass<SUBT>,
      private val parentClass: KClass<T>,
      val isChildInParentNullable: Boolean
    ) : ReadWriteProperty<MODSUBT, T> {
      internal lateinit var connectionId: ConnectionId<T, SUBT>

      operator fun provideDelegate(thisRef: MODSUBT, property: KProperty<*>): ReadWriteProperty<MODSUBT, T> {
        connectionId = ConnectionId.create(parentClass, childClass, true, ONE_TO_ONE, false, isChildInParentNullable)
        return this
      }

      override fun getValue(thisRef: MODSUBT, property: KProperty<*>): T {
        return thisRef.diff.extractOneToOneParent(connectionId, thisRef.id as PId<SUBT>)!!
      }

      override fun setValue(thisRef: MODSUBT, property: KProperty<*>, value: T) {
        if (!thisRef.modifiable.get()) {
          throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
        }
        thisRef.diff.updateOneToOneParentOfChild(connectionId, thisRef.id as PId<SUBT>, value)
      }
    }

    class Nullable<T : PTypedEntity, SUBT : PTypedEntity, MODSUBT : PModifiableTypedEntity<SUBT>>(
      private val childClass: KClass<SUBT>,
      private val parentClass: KClass<T>,
      val isChildInParentNullable: Boolean
    ) : ReadWriteProperty<MODSUBT, T?> {
      internal lateinit var connectionId: ConnectionId<T, SUBT>

      operator fun provideDelegate(thisRef: MODSUBT, property: KProperty<*>): ReadWriteProperty<MODSUBT, T?> {
        connectionId = ConnectionId.create(parentClass, childClass, true, ONE_TO_ONE, true, isChildInParentNullable)
        return this
      }

      override fun getValue(thisRef: MODSUBT, property: KProperty<*>): T? {
        return thisRef.diff.extractOneToOneParent(connectionId, thisRef.id as PId<SUBT>)
      }

      override fun setValue(thisRef: MODSUBT, property: KProperty<*>, value: T?) {
        if (!thisRef.modifiable.get()) {
          throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
        }
        thisRef.diff.updateOneToOneParentOfChild(connectionId, thisRef.id as PId<SUBT>, value)
      }
    }
  }

  override fun getValue(thisRef: MODSUBT, property: KProperty<*>): T {
    return thisRef.diff.extractOneToOneParent(connectionId, thisRef.id as PId<SUBT>)!!
  }

  override fun setValue(thisRef: MODSUBT, property: KProperty<*>, value: T) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    thisRef.diff.updateOneToOneParentOfChild(connectionId, thisRef.id as PId<SUBT>, value)
  }
}
