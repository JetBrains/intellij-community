package com.intellij.cce.actions

abstract class SessionId {
  abstract val id: String

  override fun equals(other: Any?): Boolean = other is SessionId && id == other.id
  override fun hashCode(): Int = id.hashCode()
}