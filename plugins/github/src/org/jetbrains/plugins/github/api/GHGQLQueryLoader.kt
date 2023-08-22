// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import com.google.common.annotations.VisibleForTesting
import com.intellij.collaboration.api.graphql.CachingGraphQLQueryLoader
import com.intellij.collaboration.api.graphql.GraphQLQueryLoader
import java.nio.file.Files
import java.nio.file.Paths
import java.util.stream.Collectors
import kotlin.io.path.isDirectory

object GHGQLQueryLoader : GraphQLQueryLoader
                          by CachingGraphQLQueryLoader({ GHGQLQueryLoader::class.java.classLoader.getResourceAsStream(it) }) {
  @VisibleForTesting
  fun findAllQueries(): List<String> {
    val url = GHGQLQueryLoader::class.java.classLoader.getResource("graphql/query")!!
    val directory = Paths.get(url.toURI())
    return Files.walk(directory)
      .filter { !it.isDirectory() }
      .map { "graphql/query/" + it.fileName.toString() }
      .collect(Collectors.toList())
  }
}