// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage

import com.intellij.platform.workspace.storage.impl.ClassToIntConverter
import com.intellij.platform.workspace.storage.impl.findWorkspaceEntity
import com.intellij.platform.workspace.storage.impl.toClassId
import com.intellij.util.containers.Interner
import org.jetbrains.annotations.ApiStatus

@RequiresOptIn("This is an internal API for WorkspaceEntity's implementation. It's usage requires an explicit opt-in and it can be a subject for changes")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public annotation class WorkspaceEntityInternalApi

@WorkspaceEntityInternalApi
public class ConnectionId private constructor(
  public val parentClass: Int,
  public val childClass: Int,
  public val connectionType: ConnectionType,
  public val isParentNullable: Boolean
) {
  public enum class ConnectionType {
    ONE_TO_ONE,
    ONE_TO_MANY,
    ONE_TO_ABSTRACT_MANY,
    ABSTRACT_ONE_TO_ONE
  }

  /**
   * This function returns true if this connection allows removing parent of child.
   *
   * E.g. parent is optional (nullable) for child entity, so the parent can be safely removed.
   */
  public fun canRemoveParent(): Boolean = isParentNullable

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ConnectionId

    if (parentClass != other.parentClass) return false
    if (childClass != other.childClass) return false
    if (connectionType != other.connectionType) return false
    if (isParentNullable != other.isParentNullable) return false

    return true
  }

  override fun hashCode(): Int {
    var result = parentClass.hashCode()
    result = 31 * result + childClass.hashCode()
    result = 31 * result + connectionType.hashCode()
    result = 31 * result + isParentNullable.hashCode()
    return result
  }

  override fun toString(): String {
    return "Connection(parent=${ClassToIntConverter.getInstance().getClassOrDie(parentClass).simpleName} " +
           "child=${ClassToIntConverter.getInstance().getClassOrDie(childClass).simpleName} $connectionType)"
  }

  public fun debugStr(): String = """
    ConnectionId info:
      - Parent class: ${this.parentClass.findWorkspaceEntity()}
      - Child class: ${this.childClass.findWorkspaceEntity()}
      - Connection type: $connectionType
      - Parent of child is nullable: $isParentNullable
  """.trimIndent()

  public companion object {
    /** This function should be [@Synchronized] because interner is not thread-save */
    @Synchronized
    public fun <Parent : WorkspaceEntity, Child : WorkspaceEntity> create(
      parentClass: Class<Parent>,
      childClass: Class<Child>,
      connectionType: ConnectionType,
      isParentNullable: Boolean
    ): ConnectionId {
      val connectionId = ConnectionId(parentClass.toClassId(), childClass.toClassId(), connectionType, isParentNullable)
      return interner.intern(connectionId)
    }

    /** This function should be [@Synchronized] because interner is not thread-save */
    @Synchronized
    @ApiStatus.Internal
    public fun create(
      parentClass: Int,
      childClass: Int,
      connectionType: ConnectionType,
      isParentNullable: Boolean
    ): ConnectionId {
      val connectionId = ConnectionId(parentClass, childClass, connectionType, isParentNullable)
      return interner.intern(connectionId)
    }

    private val interner = Interner.createInterner<ConnectionId>()
  }
}