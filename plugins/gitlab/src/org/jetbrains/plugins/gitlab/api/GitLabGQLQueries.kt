// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

object GitLabGQLQueries {
  const val getCurrentUser = "graphql/query/getCurrentUser.graphql"
  const val getProjectMembers = "graphql/query/getProjectMembers.graphql"
  const val getProjectLabels = "graphql/query/getProjectLabels.graphql"
}