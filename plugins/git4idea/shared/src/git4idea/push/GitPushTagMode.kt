// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push

import com.intellij.openapi.components.BaseState
import git4idea.i18n.GitBundle

class GitPushTagMode : BaseState {
  var title by string()
  var argument by string()

  @Suppress("unused")
  constructor() : this(ALL.title!!, ALL.argument!!)

  constructor(title: String, argument: String) : super() {
    // must be in constructor, not as default value for field
    this.title = title
    this.argument = argument
  }

  companion object {
    @JvmField
    val ALL = GitPushTagMode("All", "--tags")

    @JvmField
    val FOLLOW = GitPushTagMode("Current Branch", "--follow-tags")

    @JvmStatic
    val values = arrayOf(ALL, FOLLOW)
  }
}

fun GitPushTagMode.localizedTitle(): String? = when {
  this == GitPushTagMode.ALL -> GitBundle.message("push.dialog.push.tags.combo.all")
  this == GitPushTagMode.FOLLOW -> GitBundle.message("push.dialog.push.tags.combo.current.branch")
  else -> this.title
}
