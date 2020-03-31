// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import com.google.common.annotations.VisibleForTesting
import com.google.common.cache.CacheBuilder
import com.intellij.util.io.isDirectory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

object GHGQLQueryLoader {
  private const val GQL_DIRECTORY = "graphql"
  private const val GQL_FRAGMENTS_DIRECTORY = "fragment"
  private const val GQL_QUERY_DIRECTORY = "query"
  private const val GQL_FILE_SUFFIX = "graphql"

  private val fragmentDefinitionRegex = Regex("fragment (.*) on .*\\{")

  private val fragmentsCache = CacheBuilder.newBuilder()
    .expireAfterAccess(2, TimeUnit.MINUTES)
    .build<String, Fragment>()

  private val queriesCache = CacheBuilder.newBuilder()
    .expireAfterAccess(1, TimeUnit.MINUTES)
    .build<String, String>()

  @Throws(IOException::class)
  fun loadQuery(queryName: String): String {
    return queriesCache.get(queryName) {
      val (body, fragmentNames) = readCollectingFragmentNames("$GQL_DIRECTORY/$GQL_QUERY_DIRECTORY/${queryName}.$GQL_FILE_SUFFIX")

      val builder = StringBuilder()
      for (fragment in getFragmentsWithDependencies(fragmentNames)) {
        builder.append(fragment.body).append("\n")
      }

      builder.append(body)
      builder.toString()
    }
  }

  private fun getFragmentsWithDependencies(names: Set<String>): Set<Fragment> {
    val set = mutableSetOf<Fragment>()
    for (name in names) {
      val fragment = fragmentsCache.get(name) {
        Fragment(name)
      }
      set.add(fragment)
      set.addAll(getFragmentsWithDependencies(fragment.dependencies))
    }
    return set
  }

  private fun readCollectingFragmentNames(filePath: String): Pair<String, Set<String>> {
    val bodyBuilder = StringBuilder()
    val fragments = mutableSetOf<String>()
    val innerFragments = mutableSetOf<String>()

    val stream = GHGQLQueryLoader::class.java.classLoader.getResourceAsStream(filePath)
                 ?: throw GHGQLFileNotFoundException("Couldn't find file $filePath")
    stream.reader().forEachLine {
      val line = it.trim()
      bodyBuilder.append(line).append("\n")

      if (line.startsWith("fragment")) {
        val fragmentName = fragmentDefinitionRegex.matchEntire(line)?.groupValues?.get(1)?.trim()
        if (fragmentName != null)
          innerFragments.add(fragmentName)
      }

      if (line.startsWith("...") && line.length > 3 && !line[3].isWhitespace()) {
        val fragmentName = line.substring(3)
        fragments.add(fragmentName)
      }
    }
    fragments.removeAll(innerFragments)
    return bodyBuilder.toString() to fragments
  }

  private class Fragment(val name: String) {

    val body: String
    val dependencies: Set<String>

    init {
      val (body, dependencies) = readCollectingFragmentNames("$GQL_DIRECTORY/$GQL_FRAGMENTS_DIRECTORY/${name}.$GQL_FILE_SUFFIX")
      this.body = body
      this.dependencies = dependencies
    }


    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is Fragment) return false

      if (name != other.name) return false

      return true
    }

    override fun hashCode(): Int {
      return name.hashCode()
    }
  }

  @VisibleForTesting
  fun findAllQueries(): List<String> {
    val url = GHGQLQueryLoader::class.java.classLoader.getResource("${GQL_DIRECTORY}/${GQL_QUERY_DIRECTORY}")
    val directory = Paths.get(url.toURI())
    return Files.walk(directory)
      .filter { !it.isDirectory() }
      .map { it.fileName.toString().removeSuffix(".${GQL_FILE_SUFFIX}") }
      .collect(Collectors.toList())
  }
}