// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api

import com.google.common.annotations.VisibleForTesting
import com.intellij.collaboration.api.graphql.CachingGraphQLQueryLoader
import com.intellij.collaboration.api.graphql.GraphQLQueryLoader
import org.jetbrains.annotations.ApiStatus
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory

@ApiStatus.Internal
object GHGQLQueryLoader : GraphQLQueryLoader {
  private val delegate: CachingGraphQLQueryLoader = CachingGraphQLQueryLoader({ GHGQLQueryLoader::class.java.classLoader.getResourceAsStream(it) })

  @VisibleForTesting
  fun findAllQueries(): List<String> {
    val url = GHGQLQueryLoader::class.java.classLoader.getResource("graphql/query") ?: return emptyList()

    fun getQueries(directory: Path): List<String> = Files.walk(directory)
      .filter { !it.isDirectory() }
      .map { "graphql/query/" + directory.relativize(it).invariantSeparatorsPathString }
      .collect(Collectors.toList())

    return if (url.protocol.contains("jar")) {
      FileSystems.newFileSystem(url.toURI(), emptyMap<String, Any>()).use { jarFs ->
        getQueries(jarFs.getPath("graphql/query"))
      }
    }
    else {
      getQueries(Paths.get(url.toURI()))
    }
  }

  override fun loadQuery(queryPath: String): String {
    return delegate.loadQuery(queryPath)
  }
}