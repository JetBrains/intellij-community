// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api

import com.apollographql.apollo.annotations.ApolloExperimental
import com.apollographql.apollo.ast.*
import java.nio.file.Files
import java.nio.file.Paths
import java.util.stream.Collectors
import kotlin.io.path.isDirectory
import kotlin.io.path.name


internal object GHGQLTestSchemas {
  // These are schemas used by private GitHub Enterprise servers.
  val s_3_10 by lazy { loadSchema("3.10") }
  val s_3_11 by lazy { loadSchema("3.11") }
  val s_3_12 by lazy { loadSchema("3.12") }
  val s_3_13 by lazy { loadSchema("3.13") }
  val s_3_14 by lazy { loadSchema("3.14") }
  val s_3_15 by lazy { loadSchema("3.15") }

  // Cloud schemas refer to the ones used by github.com and *.ghe.com.
  val s_cloud_latest by lazy { loadSchema("cloud-latest") }

  // The schema we develop against (available under gen/schema.graphql).
  val s_dev by lazy { loadSchemaByClassPath("schema.graphql", "dev") }

  val all = listOf(s_3_10, s_3_11, s_3_12, s_3_13, s_3_14, s_3_15, s_cloud_latest /*s_dev*/)

  private fun loadSchema(version: String): GraphQLSchemaAndQueryLoaderHolder =
    loadSchemaByClassPath("/graphql/schemas/${version}.schema.graphql", version)

  @OptIn(ApolloExperimental::class)
  private fun loadSchemaByClassPath(path: String, displayName: String): GraphQLSchemaAndQueryLoaderHolder =
    GHGQLTestSchemas::class.java.getResourceAsStream(path).use { stream ->
      val schemaText = stream!!.reader(Charsets.UTF_8).readText()
      val schema = parseSchemaFromString(schemaText)

      GraphQLSchemaAndQueryLoaderHolder(displayName, path, schema, GHGQLQueryLoader)
    }

  @OptIn(ApolloExperimental::class)
  fun parseSchemaFromString(schemaText: String): Schema {
    val parsedSchema = schemaText.parseAsGQLDocument()
    if (parsedSchema.value == null || parsedSchema.issues.filtered.isNotEmpty()) {
      throw RuntimeException("Schema could not be parsed:\n\n${parsedSchema.issues.joinToString("\n") { it.message }}")
    }

    val validatedSchema = parsedSchema.value!!.validateAsSchema()
    if (validatedSchema.value == null || validatedSchema.issues.filtered.isNotEmpty()) {
      throw RuntimeException("Schema is invalid:\n\n${validatedSchema.issues.joinToString("\n") { it.message }}")
    }

    return validatedSchema.value!!
  }
}

internal object GHGQLQueryTestData {
  // TODO: Double-check when creating version-restricted queries / re-using in GitLab
  // Names of queries, human-readable, excluding /graphql/query/
  val queryNames: List<String> = run {
    val url = GHGQLQueryLoader::class.java.classLoader.getResource("graphql/query")!!
    val directory = Paths.get(url.toURI())
    Files.walk(directory)
      .filter { !it.isDirectory() }
      .map { it.name }
      .collect(Collectors.toList())
  }

  fun toPath(queryName: String): String =
    "graphql/query/${queryName}"
}

data class GraphQLSchemaAndQueryLoaderHolder(
  val displayName: String,
  val path: String,
  val schema: Schema,
  val queryLoader: GHGQLQueryLoader,
)

internal fun GHGQLQueryLoader.loadQueryAsText(queryName: String): String? {
  try {
    return loadQuery(GHGQLQueryTestData.toPath(queryName))
  }
  catch (e: Exception) {
    e.printStackTrace()
    return null
  }
}

internal fun GHGQLQueryLoader.loadQueryParsed(queryName: String): Pair<GQLOperationDefinition?, List<GQLFragmentDefinition>> {
  val queryText = loadQueryAsText(queryName)
  if (queryText == null) return null to emptyList()

  val queryDocument = queryText.parseAsGQLDocument().getOrThrow()
  val allFragmentDefinitions = queryDocument.definitions.filterIsInstance<GQLFragmentDefinition>()

  val queries = queryDocument.definitions.filterIsInstance<GQLOperationDefinition>()
  if (queries.size > 1) error("Multiple queries found")

  return queries.firstOrNull() to allFragmentDefinitions
}

internal val List<Issue>.filtered: List<Issue>
  get() = filter { it !is ApolloIssue && it !is UnusedFragment }
