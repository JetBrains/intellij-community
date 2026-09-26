// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue

@Suppress("EnumEntryName")
enum class GitLabProjectSquashOptionRest {
  never,

  always,

  @JsonEnumDefaultValue
  default_on,

  default_off
}