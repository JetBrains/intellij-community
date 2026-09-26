// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git

import git4idea.i18n.GitBundle
import org.jetbrains.annotations.Nls
import java.util.function.Supplier

object GitDisplayName {
  @JvmField
  val POINTER: Supplier<@Nls String> = GitBundle.messagePointer("git4idea.vcs.name")

  @JvmField
  val NAME: @Nls String = GitBundle.message("git4idea.vcs.name")
}