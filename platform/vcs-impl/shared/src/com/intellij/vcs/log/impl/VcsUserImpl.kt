// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.util.VcsUserUtil
import kotlinx.serialization.Serializable

/**
 * Note: users are considered equal if they have the same name and email.
 */
@Serializable
data class VcsUserImpl @Deprecated("Use VcsUserUtil.createUser") constructor(private val name: String, private val email: String) : VcsUser {
  override fun getName(): String = name

  override fun getEmail(): String = email

  override fun toString(): String {
    return VcsUserUtil.toExactString(this)
  }
}
