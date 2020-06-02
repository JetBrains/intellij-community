// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl.references

import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ConnectionId.ConnectionType.ONE_TO_ONE
import com.intellij.workspaceModel.storage.impl.*
import com.intellij.workspaceModel.storage.impl.extractOneToOneChild
import com.intellij.workspaceModel.storage.impl.extractOneToOneParent
import com.intellij.workspaceModel.storage.impl.updateOneToOneChildOfParent
import com.intellij.workspaceModel.storage.impl.updateOneToOneParentOfChild
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class OneToOneParent private constructor() {
  class NotNull<T : WorkspaceEntityBase, SUBT : WorkspaceEntityBase>(private val childClass: Class<SUBT>,
                                                                     val isParentInChildNullable: Boolean) : ReadOnlyProperty<T, SUBT> {
    private var connectionId: ConnectionId? = null

    override fun getValue(thisRef: T, property: KProperty<*>): SUBT {
      if (connectionId == null) {
        connectionId = ConnectionId.create(thisRef.javaClass, childClass, ONE_TO_ONE, isParentInChildNullable, false)
      }
      return thisRef.snapshot.extractOneToOneChild(connectionId!!, thisRef.id)!!
    }
  }

  class Nullable<T : WorkspaceEntityBase, SUBT : WorkspaceEntityBase>(private val childClass: Class<SUBT>,
                                                                      val isParentInChildNullable: Boolean) : ReadOnlyProperty<T, SUBT?> {
    private var connectionId: ConnectionId? = null

    override fun getValue(thisRef: T, property: KProperty<*>): SUBT? {
      if (connectionId == null) {
        connectionId = ConnectionId.create(thisRef.javaClass, childClass, ONE_TO_ONE, isParentInChildNullable, true)
      }
      return thisRef.snapshot.extractOneToOneChild(connectionId!!, thisRef.id)
    }
  }
}

class OneToOneChild private constructor() {
  class NotNull<SUBT : WorkspaceEntityBase, T : WorkspaceEntityBase>(private val parentClass: Class<T>,
                                                                     val isChildInParentNullable: Boolean) : ReadOnlyProperty<SUBT, T> {
    private var connectionId: ConnectionId? = null

    override fun getValue(thisRef: SUBT, property: KProperty<*>): T {
      if (connectionId == null) {
        connectionId = ConnectionId.create(parentClass, thisRef.javaClass, ONE_TO_ONE, false, isChildInParentNullable)
      }
      return thisRef.snapshot.extractOneToOneParent(connectionId!!, thisRef.id)!!
    }
  }

  class Nullable<SUBT : WorkspaceEntityBase, T : WorkspaceEntityBase>(private val parentClass: Class<T>,
                                                                      val isChildInParentNullable: Boolean) : ReadOnlyProperty<SUBT, T?> {
    private var connectionId: ConnectionId? = null

    override fun getValue(thisRef: SUBT, property: KProperty<*>): T? {
      if (connectionId == null) {
        connectionId = ConnectionId.create(parentClass, thisRef.javaClass, ONE_TO_ONE, true, isChildInParentNullable)
      }
      return thisRef.snapshot.extractOneToOneParent(connectionId!!, thisRef.id)
    }
  }
}

class MutableOneToOneParent private constructor() {
  class NotNull<T : WorkspaceEntityBase, SUBT : WorkspaceEntityBase, MODT : ModifiableWorkspaceEntityBase<T>>(
    private val parentClass: Class<T>,
    private val childClass: Class<SUBT>,
    private val isParentInChildNullable: Boolean
  ) : ReadWriteProperty<MODT, SUBT> {

    private var connectionId: ConnectionId? = null

    override fun getValue(thisRef: MODT, property: KProperty<*>): SUBT {
      if (connectionId == null) {
        connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_ONE, isParentInChildNullable, false)
      }
      return thisRef.diff.extractOneToOneChild(connectionId!!, thisRef.id)!!
    }

    override fun setValue(thisRef: MODT, property: KProperty<*>, value: SUBT) {
      if (!thisRef.modifiable.get()) {
        throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
      }
      if (connectionId == null) {
        connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_ONE, isParentInChildNullable, false)
      }
      thisRef.diff.updateOneToOneChildOfParent(connectionId!!, thisRef.id, value)
    }
  }

  class Nullable<T : WorkspaceEntityBase, SUBT : WorkspaceEntityBase, MODT : ModifiableWorkspaceEntityBase<T>>(
    private val parentClass: Class<T>,
    private val childClass: Class<SUBT>,
    private val isParentInChildNullable: Boolean
  ) : ReadWriteProperty<MODT, SUBT?> {

    private var connectionId: ConnectionId? = null

    override fun getValue(thisRef: MODT, property: KProperty<*>): SUBT? {
      if (connectionId == null) {
        connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_ONE, isParentInChildNullable, true)
      }
      return thisRef.diff.extractOneToOneChild(connectionId!!, thisRef.id)!!
    }

    override fun setValue(thisRef: MODT, property: KProperty<*>, value: SUBT?) {
      if (!thisRef.modifiable.get()) {
        throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
      }
      if (connectionId == null) {
        connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_ONE, isParentInChildNullable, true)
      }
      thisRef.diff.updateOneToOneChildOfParent(connectionId!!, thisRef.id, value)
    }
  }
}

class MutableOneToOneChild private constructor() {
  class NotNull<T : WorkspaceEntityBase, SUBT : WorkspaceEntityBase, MODSUBT : ModifiableWorkspaceEntityBase<SUBT>>(
    private val childClass: Class<SUBT>,
    private val parentClass: Class<T>,
    private val isChildInParentNullable: Boolean
  ) : ReadWriteProperty<MODSUBT, T> {
    private var connectionId: ConnectionId? = null

    override fun getValue(thisRef: MODSUBT, property: KProperty<*>): T {
      if (connectionId == null) {
        connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_ONE, false, isChildInParentNullable)
      }
      return thisRef.diff.extractOneToOneParent(connectionId!!, thisRef.id)!!
    }

    override fun setValue(thisRef: MODSUBT, property: KProperty<*>, value: T) {
      if (!thisRef.modifiable.get()) {
        throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
      }
      if (connectionId == null) {
        connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_ONE, false, isChildInParentNullable)
      }
      thisRef.diff.updateOneToOneParentOfChild(connectionId!!, thisRef.id, value)
    }
  }

  class Nullable<T : WorkspaceEntityBase, SUBT : WorkspaceEntityBase, MODSUBT : ModifiableWorkspaceEntityBase<SUBT>>(
    private val childClass: Class<SUBT>,
    private val parentClass: Class<T>,
    private val isChildInParentNullable: Boolean
  ) : ReadWriteProperty<MODSUBT, T?> {
    private var connectionId: ConnectionId? = null

    override fun getValue(thisRef: MODSUBT, property: KProperty<*>): T? {
      if (connectionId == null) {
        connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_ONE, true, isChildInParentNullable)
      }
      return thisRef.diff.extractOneToOneParent(connectionId!!, thisRef.id)
    }

    override fun setValue(thisRef: MODSUBT, property: KProperty<*>, value: T?) {
      if (!thisRef.modifiable.get()) {
        throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
      }
      if (connectionId == null) {
        connectionId = ConnectionId.create(parentClass, childClass, ONE_TO_ONE, true, isChildInParentNullable)
      }
      thisRef.diff.updateOneToOneParentOfChild(connectionId!!, thisRef.id, value)
    }
  }
}
