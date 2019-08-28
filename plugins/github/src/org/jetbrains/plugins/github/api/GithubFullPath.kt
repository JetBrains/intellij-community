// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import com.intellij.openapi.util.text.StringUtil

/**
 * @author Aleksey Pivovarov
 */
class GithubFullPath(val user: String, val repository: String) {

  val fullName: String
    get() = "$user/$repository"

  override fun toString(): String {
    return "'$fullName'"
  }

  override fun equals(o: Any?): Boolean {
    if (this === o) return true
    if (o == null || javaClass != o.javaClass) return false

    val that = o as GithubFullPath?

    if (!StringUtil.equalsIgnoreCase(repository, that!!.repository)) return false
    return if (!StringUtil.equalsIgnoreCase(user, that.user)) false else true

  }

  override fun hashCode(): Int {
    var result = user.hashCode()
    result = 31 * result + repository.hashCode()
    return result
  }
}
