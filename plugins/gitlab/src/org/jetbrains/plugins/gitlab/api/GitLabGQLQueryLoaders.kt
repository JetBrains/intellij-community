// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import com.intellij.collaboration.api.graphql.CachingGraphQLQueryLoader
import com.intellij.collaboration.api.graphql.GraphQLQueryLoader

internal object GitLabGQLQueryLoaders {
  private val sharedFragmentsCache = CachingGraphQLQueryLoader.createFragmentCache()
  private val loaderCache = mutableMapOf<GitLabServerMetadata, CachingGraphQLQueryLoader>()

  private val fragmentVersions = listOf(
    GitLabVersion(13, 5),
    GitLabVersion(13, 9),
    GitLabVersion(13, 11),
    GitLabVersion(14, 0),
    GitLabVersion(14, 5),
    GitLabVersion(14, 7),
    GitLabVersion(15, 9),
    GitLabVersion(16, 1),
    GitLabVersion(16, 8)
  )

  val default: GraphQLQueryLoader by lazy {
    CachingGraphQLQueryLoader(
      { GitLabGQLQueryLoaders::class.java.classLoader.getResourceAsStream(it) },
      sharedFragmentsCache
    )
  }

  /**
   * Gets a query loader for specific metadata of a GitLab server.
   */
  fun forMetadata(metadata: GitLabServerMetadata): GraphQLQueryLoader =
    loaderCache.getOrPut(metadata) {
      // Highly specific and annoying piece of code:
      // The version associated to a directory is the version it was changed in, but the fragments in that directory
      // should only be used in versions before that version, not in the version itself.
      // The order of resolution should be community before enterprise and lower version change before higher version change.
      val directories = fragmentVersions
                          .filter { metadata.version < it }
                          .map { "graphql/fragment/${it.major}_${it.minor}" } +
                        listOf("graphql/fragment")
      val directoriesIncludingCommunity =
        if (metadata.edition == GitLabEdition.Community) directories.flatMap { listOf("$it/community", it) }
        else directories

      CachingGraphQLQueryLoader(
        { GitLabGQLQueryLoaders::class.java.classLoader.getResourceAsStream(it) },
        fragmentsDirectories = directoriesIncludingCommunity
      )
    }
}