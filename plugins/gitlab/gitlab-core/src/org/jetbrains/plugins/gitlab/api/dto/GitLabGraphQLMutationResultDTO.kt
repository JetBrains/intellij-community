// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

abstract class GitLabGraphQLMutationResultDTO<V>(
  val errors: List<String>?
) {
  abstract val value: V?

  class Empty(errors: List<String>?) : GitLabGraphQLMutationResultDTO<Unit>(errors) {
    override val value: Unit = Unit
  }
}