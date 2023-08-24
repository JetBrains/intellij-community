// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import com.intellij.collaboration.api.graphql.CachingGraphQLQueryLoader
import com.intellij.collaboration.api.graphql.GraphQLQueryLoader

internal object GitLabGQLQueryLoaders {
  private val sharedFragmentsCache = CachingGraphQLQueryLoader.createFragmentCache()

  val default: GraphQLQueryLoader by lazy {
    CachingGraphQLQueryLoader({ GitLabGQLQueryLoaders::class.java.classLoader.getResourceAsStream(it) },
                              sharedFragmentsCache)
  }
  val community: GraphQLQueryLoader by lazy {
    CachingGraphQLQueryLoader({ GitLabGQLQueryLoaders::class.java.classLoader.getResourceAsStream(it) },
                              sharedFragmentsCache,
                              fragmentsDirectories = listOf("graphql/fragment/community",
                                                            "graphql/fragment"))
  }
}