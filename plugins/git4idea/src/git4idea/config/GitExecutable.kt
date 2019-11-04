// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config

sealed class GitExecutable {
  abstract val exePath: String

  data class Local(override val exePath: String)
    : GitExecutable() {
    override fun toString(): String = exePath
  }
}
