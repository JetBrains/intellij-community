package com.intellij.cce.actions

import java.util.UUID

class UUIDBasedSessionId(private val uuid: UUID) : SessionId() {
  override val id: String
    get() = uuid.toString()
}