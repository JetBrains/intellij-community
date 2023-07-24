// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.data

enum class GitLabSnippetBlobActionEnum(val value: String) {
  create("create"),
  delete("delete"),
  move("move"),
  update("update");

  override fun toString(): String = value
}