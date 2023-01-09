// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.dto

import com.intellij.collaboration.api.dto.GraphQLFragment

@GraphQLFragment("/graphql/fragment/label.graphql")
data class GitLabLabelDTO(
  val title: String
)