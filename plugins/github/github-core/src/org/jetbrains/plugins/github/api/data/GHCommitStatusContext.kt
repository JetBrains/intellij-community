// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data

import com.intellij.collaboration.api.dto.GraphQLFragment

@GraphQLFragment("/graphql/fragment/commitStatusContext.graphql")
class GHCommitStatusContext(
  val context: String,
  val state: GHCommitStatusContextState,
  val isRequired: Boolean,
  val targetUrl: String?
)