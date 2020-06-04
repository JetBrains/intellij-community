// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl.references

import com.intellij.workspaceModel.storage.impl.*
import com.intellij.workspaceModel.storage.impl.ConnectionId.ConnectionType.ONE_TO_MANY
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class OneToMany<T : WorkspaceEntityBase, SUBT : WorkspaceEntityBase>(private val childClass: Class<SUBT>,
                                                                     private val isParentInChildNullable: Boolean) : ReadOnlyProperty<T, Sequence<SUBT>> {

  private var connectionId: ConnectionId? = null

  override fun getValue(thisRef: T, property: KProperty<*>): Sequence<SUBT> {
    if (connectionId == null) {
      connectionId = ConnectionId.create(thisRef.javaClass, childClass, ONE_TO_MANY, isParentInChildNullable, false)
    }
    return thisRef.snapshot.extractOneToManyChildren(connectionId!!, thisRef.id)
  }
}

class ManyToOne private constructor() {
  class NotNull<T : WorkspaceEntityBase, SUBT : WorkspaceEntityBase>(private val parentClass: Class<T>) : ReadOnlyProperty<SUBT, T> {
    private var connectionId: ConnectionId? = null

    override fun getValue(thisRef: SUBT, property: KProperty<*>): T {
      if (connectionId == null) {
        connectionId = ConnectionId.create(parentClass, thisRef.javaClass, ONE_TO_MANY, false, false)
      }
      return thisRef.snapshot.extractOneToManyParent(connectionId!!, thisRef.id)!!
    }
  }

  class Nullable<T : WorkspaceEntityBase, SUBT : WorkspaceEntityBase>(private val parentClass: Class<T>) : ReadOnlyProperty<SUBT, T?> {
    private var connectionId: ConnectionId? = null

    override fun getValue(thisRef: SUBT, property: KProperty<*>): T? {
      if (connectionId == null) {
        connectionId = ConnectionId.create(parentClass, thisRef.javaClass, ONE_TO_MANY, true, false)
      }
      return thisRef.snapshot.extractOneToManyParent(connectionId!!, thisRef.id)
    }
  }
}

class MutableOneToMany<T : WorkspaceEntityBase, SUBT : WorkspaceEntityBase, MODT : ModifiableWorkspaceEntityBase<T>>(
  private val parentClass: Class<T>,
  private val childClass: Class<SUBT>,
  private val isParentInChildNullable: Boolean
) : ReadWriteProperty<MODT, Sequence<SUBT>> {

  private var connectionId: ConnectionId? = null

  override fun getValue(thisRef: MODT, property: KProperty<*>): Sequence<SUBT> {
    if (connectionId == null) {
      connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_MANY, isParentInChildNullable, false)
    }
    return thisRef.diff.extractOneToManyChildren(connectionId!!, thisRef.id)
  }

  override fun setValue(thisRef: MODT, property: KProperty<*>, value: Sequence<SUBT>) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    if (connectionId == null) {
      connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_MANY, isParentInChildNullable, false)
    }
    thisRef.diff.updateOneToManyChildrenOfParent(connectionId!!, thisRef.id, value)
  }
}

class MutableManyToOne private constructor() {
  class NotNull<T : WorkspaceEntityBase, SUBT : WorkspaceEntityBase, MODSUBT : ModifiableWorkspaceEntityBase<SUBT>>(
    private val childClass: Class<SUBT>,
    private val parentClass: Class<T>
  ) : ReadWriteProperty<MODSUBT, T> {
    private var connectionId: ConnectionId? = null

    override fun getValue(thisRef: MODSUBT, property: KProperty<*>): T {
      if (connectionId == null) {
        connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_MANY, false, false)
      }
      return thisRef.diff.extractOneToManyParent(connectionId!!, thisRef.id)!!
    }

    override fun setValue(thisRef: MODSUBT, property: KProperty<*>, value: T) {
      if (!thisRef.modifiable.get()) {
        throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
      }
      if (connectionId == null) {
        connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_MANY, false, false)
      }
      return thisRef.diff.updateOneToManyParentOfChild(connectionId!!, thisRef.id, value)
    }
  }

  class Nullable<T : WorkspaceEntityBase, SUBT : WorkspaceEntityBase, MODSUBT : ModifiableWorkspaceEntityBase<SUBT>>(
    private val childClass: Class<SUBT>,
    private val parentClass: Class<T>
  ) : ReadWriteProperty<MODSUBT, T?> {
    private var connectionId: ConnectionId? = null

    override fun getValue(thisRef: MODSUBT, property: KProperty<*>): T? {
      if (connectionId == null) {
        connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_MANY, true, false)
      }
      return thisRef.diff.extractOneToManyParent(connectionId!!, thisRef.id)
    }

    override fun setValue(thisRef: MODSUBT, property: KProperty<*>, value: T?) {
      if (!thisRef.modifiable.get()) {
        throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
      }
      if (connectionId == null) {
        connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_MANY, true, false)
      }
      return thisRef.diff.updateOneToManyParentOfChild(connectionId!!, thisRef.id, value)
    }
  }
}