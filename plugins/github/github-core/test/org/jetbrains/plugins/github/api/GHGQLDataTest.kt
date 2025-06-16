// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/**
 * Welcome to the GitHub GraphQL fragment and DTO testing class!
 *
 * We have 4 different sets of tests in this class that complement each other:
 * - [GHGQLQueryTest] checks that our custom fragments compile/validate against current/old GraphQL schemas.
 * - [GHGQLDTOTest] checks that our custom DTOs don't cause a runtime failure with current/old GraphQL schemas and our user-defined fragments.
 * - [GHGQLDeserializationAssumptionsTest] checks that the assumptions about Jackson deserialization we make in the first two tests are valid.
 * - [MetaTest] illustrates the kind of failures that the first two tests should be able to catch.
 *
 * If a new type of issue is found, causing a game-breaking bug, there's a couple possible causes:
 * 1. The version of GitHub that the issue occurs for doesn't have an associated schema in this test folder yet. Add it.
 * 2. The bug is not detected by our testing. In this case it's probably a DTO-check that needs to be added.
 *    - Extend/adjust the DTO test so that it detects your issue.
 *    - Test that it works by adding a test to [MetaTest].
 */
package org.jetbrains.plugins.github.api

import com.apollographql.apollo.annotations.ApolloExperimental
import com.apollographql.apollo.ast.*
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition
import com.fasterxml.jackson.databind.type.ArrayType
import com.fasterxml.jackson.databind.type.CollectionLikeType
import com.intellij.collaboration.api.data.GraphQLRequestPagination
import com.intellij.diff.util.Side
import com.intellij.idea.IJIgnore
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.github.api.GithubApiRequest.Post.GQLQuery
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent
import org.jetbrains.plugins.github.api.data.GHReactionContent
import org.junit.Ignore
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*
import kotlin.reflect.full.primaryConstructor

typealias PluginQuery<T> = GQLQuery<T>

@RunWith(Parameterized::class)
class GHGQLQueryTest(val testCase: TestCase) {
  companion object {
    //region: Generating Test Cases
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun testCases(): List<TestCase> =
      TestCases.constructTestCases { queryName, _, schema ->
        val queryText = schema.queryLoader.loadQueryAsText(queryName)
        listOf(TestCase(queryName, queryText, schema))
      }
    //endregion
  }

  private val queryName by testCase::queryName
  private val queryText by testCase::queryText
  private val schema by testCase.schemaHolder::schema

  internal var issues: List<Issue> = listOf()

  /**
   * "smoke test" for a test-case.
   * If this test is breaking, most likely the @GraphQLFragment annotation is not used in the right way.
   */
  @Test
  fun `test query text is not null`() {
    assertThat(queryText).isNotNull()
  }

  @Test
  @OptIn(ApolloExperimental::class)
  fun `test fragment is valid in schema`() {
    queryText!!
      .parseAsGQLDocument().assertNoIssues("parsing query")
      .validateAsExecutable(schema).issues.assertNoIssues("validating query")
  }

  private fun <V : Any> GQLResult<V>.assertNoIssues(activity: String): V {
    this@assertNoIssues.issues.assertNoIssues(activity)
    assertThat(value).isNotNull()

    return value!!
  }

  private fun List<Issue>.assertNoIssues(activity: String) {
    val importantIssues = this.filtered
    this@GHGQLQueryTest.issues = importantIssues

    assertThat(importantIssues)
      .withFailMessage {
        """
          Errors occurred while verifying the query with name:
          ${queryName}
          
          with schema
          ${testCase.schemaHolder.displayName} at ${testCase.schemaHolder.path}
          
          
          while ${activity}
          
          
          Errors:
          
        """.trimIndent() + importantIssues.joinToString(separator = "\n") { it.message }
      }
      .isEmpty()
  }

  data class TestCase(
    val queryName: String,
    val queryText: String?,
    val schemaHolder: GraphQLSchemaAndQueryLoaderHolder,
  ) {
    override fun toString(): String {
      return "${queryName} - ${schemaHolder.displayName}"
    }
  }
}

/**
 * Used to test that our data classes, mostly situated under [org.jetbrains.plugins.github.api.data],
 * match consistently with the fragment they are expected to follow.
 */
@RunWith(Parameterized::class)
class GHGQLDTOTest(val testCase: TestCase) {
  companion object {
    //region: Generating Test Cases
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun testCases(): List<TestCase> =
      TestCases.constructTestCases { queryName, pluginQueries, schema ->
        val (gqlQuery, auxiliaryFragments) = schema.queryLoader.loadQueryParsed(queryName)
        listOf(TestCase(queryName, gqlQuery, pluginQueries.first(), schema, auxiliaryFragments))
      }
    //endregion

    private val mapper = GithubApiContentHelper.getObjectMapper(gqlNaming = true)
    private val ALWAYS_VALID_FIELD_NAMES = setOf("__typename")

    private val BASIC_TYPE_MAPPINGS = setOf(
      "Int" to Int::class.java,
      "Int" to Long::class.java,
      "Int" to Integer::class.java, // null-checking is handled in verification already
      "Float" to Float::class.java,
      "String" to String::class.java,
      "Boolean" to Boolean::class.java,
      "ID" to String::class.java,

      // GitHub GQL specific scalars
      "Date" to Date::class.java,
      "DateTime" to Date::class.java,
      "URI" to String::class.java,
      "GitObjectID" to String::class.java,
      "GitSSHRemote" to String::class.java,
      "GitTimestamp" to Date::class.java,
    )
  }

  private val clazz by testCase.pluginQuery::clazz
  private val pathFromData = (testCase.pluginQuery as? GQLQuery.Traversed<*>)?.pathFromData
  private val schema by testCase.schemaHolder::schema
  private val query by testCase::gqlQuery
  private val pluginQuery by testCase::pluginQuery
  private val auxiliaryFragments by testCase::auxiliaryFragments

  internal val errors: MutableList<String> = mutableListOf()

  /**
   * "smoke test" for a test-case.
   * If this test is breaking, most likely the @GraphQLFragment annotation is not used in the right way.
   */
  @Test
  fun `test query exists and is not empty`() {
    assertThat(query).isNotNull()

    // Empty fragments are useless, don't have them!
    // This is also a sanity-check to know for sure that something meaningful is loaded and checked
    assertThat(query!!.selections).isNotEmpty()
  }

  /**
   * This test does... a lot...
   * The reason for this is that it's hard to break into pieces, since basically all checks rely on
   * other checks passing to even be able to execute.
   *
   * In short, this test tries to - as well as we can - ensure that DTO classes match the fragment they're
   * supposed to serialize from. To do so, we do a minimal amount of type-checking and null-checking.
   *
   * Currently, this test tries to detect the following possible problems:
   * - When a Java field is non-nullable, but the matching schema field has a nullable type.
   * - When a Java field exists, but no matching field can be found within the fragment.
   * - When a Java enumeration does not define all the members of the associated GraphQL type.
   * - When a Java Type is clearly not matching the associated GraphQL type (type-checking).
   */
  //region: DTO x Fragment x Schema checking
  @Test
  fun `test dto class matches the query + fragments + schema`() {
    verifyQueryParams()
    verifyQuery()?.let { (gqlTypedef, fragment) ->
      val javaType = mapper.typeFactory.constructType(clazz)

      verifyDtoClass(gqlTypedef, fragment, javaType)
    }

    assertThat(errors)
      .withFailMessage {
        """
          Errors occurred while verifying the DTOs+schema for:
          ${pluginQuery.queryName}
          
          with schema
          ${testCase.schemaHolder.displayName} at ${testCase.schemaHolder.path}
          
          
          Errors:
          
        """.trimIndent() + errors.joinToString(separator = "\n")
      }
      .isEmpty()
  }

  private fun verifyQueryParams() {
    (pluginQuery.variablesObject.keys - query!!.variableDefinitions.map { it.name }.toSet()).forEach { name ->
      errors += "Variable `$name` is not used in the query."
    }
    (query!!.variableDefinitions.map { it.name }.toSet() - pluginQuery.variablesObject.keys).forEach { name ->
      val variableDef = query!!.variableDefinitions.find { it.name == name }!!
      // it's okay to miss nullable or defaulted variables
      if (variableDef.defaultValue != null || variableDef.type !is GQLNonNullType) return@forEach

      errors += "Variable `$name` is not supplied in the variables object."
    }

    // for those variables that are present in both, check the types
    // TODO: but we can't easily re-use what's already here: input type-checking != output type-checking
  }

  private fun verifyQuery(): Pair<GQLTypeDefinition, List<GQLSelection>>? {
    // Only get the fragment/type under the path. Rate limits are considered to be parsed safe-enough
    var gqlTypedef: GQLTypeDefinition = when (query!!.operationType) {
      "query" -> schema.queryTypeDefinition
      "mutation" -> schema.mutationTypeDefinition
      else -> null
    } ?: run {
      errors += "Missing base operations definition for type `${query!!.operationType}`"
      return null
    }
    var fragment: List<GQLSelection> = query!!.selections

    for (path in pathFromData.orEmpty()) {
      val fieldAndParent = listAllFragmentFields(gqlTypedef, fragment).firstOrNull { (it.field.alias ?: it.field.name) == path }
      if (fieldAndParent == null) {
        errors += "Cannot resolve field in query path `${path}`"
        return null
      }
      val (fieldParentTypedef, field) = fieldAndParent

      var resolvedGqlType = fieldParentTypedef.resolveFieldDefinition(field)?.type
      if (resolvedGqlType == null) {
        errors += "Cannot resolve field within type `${gqlTypedef.name}`: `${field.name}`"
        return null
      }

      // Need to check if the type is nullable if the query is non-null
      if (pluginQuery is GQLQuery.TraversedParsed &&
          resolvedGqlType !is GQLNonNullType && resolvedGqlType !is GQLListType) {
        // TODO: Add an assumption test to check this
        //errors += "Field `${path}` in ${resolvedGqlType.toGQLString()} is nullable, but is expected to be non-null"
      }

      // Strip all list and non-null wrappers
      while (resolvedGqlType !is GQLNamedType) {
        resolvedGqlType = when (resolvedGqlType) {
          is GQLListType -> resolvedGqlType.type
          is GQLNonNullType -> resolvedGqlType.type
          else -> error("")
        }
      }

      gqlTypedef = lookupType(resolvedGqlType.name) ?: break
      fragment = field.selections
    }

    return gqlTypedef to fragment
  }

  private fun verifyDtoClass(gqlTypedef: GQLTypeDefinition, gqlFields: List<GQLSelection>, javaType: JavaType) {
    val beanDescription = mapper.serializationConfig.introspect(javaType)
    val fragmentFields = listAllFragmentFields(gqlTypedef, gqlFields).associateBy { it.field.alias ?: it.field.name }

    for (field in beanDescription.findProperties()) {
      if (field.name in ALWAYS_VALID_FIELD_NAMES) continue

      // Check that the field exists in the fragment
      val fragmentFieldAndParent = fragmentFields[field.name]
      if (fragmentFieldAndParent == null) {
        errors.add("Field is defined in class, but not in fragment: `${field.name}` in `${javaType}`")
        continue
      }
      val (fieldParentTypedef, fragmentField) = fragmentFieldAndParent

      // Check that the field's DTO type matches with the schema
      val fieldDef = fieldParentTypedef.resolveFieldDefinition(fragmentField)
      if (fieldDef == null) continue

      verifyDtoField(fragmentField, fieldDef, field)
    }
  }

  private fun verifyDtoField(gqlField: GQLField, gqlFieldDef: GQLFieldDefinition, javaField: BeanPropertyDefinition) {
    validateNullability(gqlFieldDef, javaField)
    verifyDtoFieldType(gqlField, gqlFieldDef.type, javaField.primaryType)
  }

  private tailrec fun verifyDtoFieldType(gqlField: GQLField, gqlType: GQLType, javaType: JavaType) {
    when (val t = gqlType) {
      is GQLNonNullType -> {
        // Just unwrap, we don't need to do anything with non-null GQL types
        verifyDtoFieldType(gqlField, t.type, javaType)
      }
      is GQLListType -> {
        // Unwrap list types after checking that the java type is also list-like
        val nextJavaType = when (javaType) {
          is ArrayType -> javaType.contentType
          is CollectionLikeType -> javaType.contentType
          else -> null
        }

        if (nextJavaType == null) {
          errors.add("Types don't match for field `${gqlField.name}`: `${javaType}` cannot unify with `${gqlType}`")
          return
        }

        verifyDtoFieldType(gqlField, t.type, nextJavaType)
      }
      is GQLNamedType -> {
        // If the type is in the list of simple types, it's okay
        if ((t.name to javaType.rawClass) in BASIC_TYPE_MAPPINGS) return

        val gqlTypedef = lookupType(t.name)
        if (gqlTypedef == null) return

        // If the type is an enum, make sure all possible values are present in both Java type and GQL type
        if (gqlTypedef is GQLEnumTypeDefinition) {
          validateEnumField(gqlField.name, javaType, gqlTypedef)
          return
        }

        // If the type doesn't have fields, it must be a primitive/scalar, so it cannot be unified
        if (gqlField.selections.isEmpty()) {
          errors.add("Types don't match for field `${gqlField.name}`: `${javaType}` cannot unify with `${t.name}`")
          return
        }

        // If the type is a complex type, recursively check its fields
        verifyDtoClass(gqlTypedef, gqlField.selections, javaType)
      }
    }
  }

  private fun validateNullability(gqlFieldDef: GQLFieldDefinition, javaField: BeanPropertyDefinition) {
    // if it's not required, we can just make it `null`, not a problem
    if (!javaField.isRequired) return

    // get the kotlin field/parameter to check default values
    val kClass = javaField.constructorParameter.owner.rawType.kotlin
    val paramHasDefault = kClass.primaryConstructor?.parameters?.find { it.name == javaField.name }?.isOptional

    // if it has a default value and is required, it can't be `null`
    // see: [GHGQLDeserializationAssumptionsTest] -> `deserializing a missing value instead of a list is fine with default`
    if (paramHasDefault == true) return

    // if the GQL field is non-null, again not a problem, it will just never be null
    if (gqlFieldDef.type is GQLNonNullType) return

    errors.add("Field `${javaField.name}` is non-null (`${javaField.primaryType}`), but nullable according to the schema (`${gqlFieldDef.type.toGQLString()}`)")
  }

  private fun validateEnumField(name: String, type: JavaType, gqlTypedef: GQLEnumTypeDefinition) {
    if (!type.isEnumType) {
      errors.add("Types don't match for field `$name`: `$type` should be an enum like `${gqlTypedef.name}`")
      return
    }

    val javaEnumValues = type.rawClass.enumConstants.map { it.toString() }.toSet()
    for (gqlValue in gqlTypedef.enumValues) {
      if (gqlValue.name !in javaEnumValues) {
        errors.add("Enum value `${gqlValue.name}` missing in type `${type.rawClass.name}` for field `$name`")
      }
    }
  }

  private data class GQLFieldWithParent(
    val parent: GQLTypeDefinition,
    val field: GQLField,
  )

  private fun listAllFragmentFields(gqlTypedef: GQLTypeDefinition, fragment: List<GQLSelection>): List<GQLFieldWithParent> {
    val allFields = mutableSetOf<GQLFieldWithParent>()

    val selectionsToGoThrough = mutableListOf(gqlTypedef to fragment)
    while (selectionsToGoThrough.isNotEmpty()) {
      val (parent, fields) = selectionsToGoThrough.removeFirst()

      for (selection in fields) {
        when (selection) {
          is GQLField -> allFields.add(GQLFieldWithParent(parent, selection))
          is GQLFragmentSpread -> {
            val fragment = auxiliaryFragments.find { it.name == selection.name }
            if (fragment == null) {
              errors.add("Undefined fragment used in spread: `...${selection.name}`")
              continue
            }
            val parent = lookupType(fragment.typeCondition.name) ?: continue
            selectionsToGoThrough.add(parent to fragment.selections)
          }
          is GQLInlineFragment -> {
            val parent = selection.typeCondition?.let { lookupType(it.name) } ?: parent
            selectionsToGoThrough.add(parent to selection.selections)
          }
        }
      }
    }

    return allFields.toList()
  }

  private fun GQLType.toGQLString(): String =
    when (this) {
      is GQLNonNullType -> "${type.toGQLString()}!"
      is GQLListType -> "[${type.toGQLString()}]"
      is GQLNamedType -> name
    }
  //endregion

  //region: Common Utility
  private fun GQLTypeDefinition.resolveFieldDefinition(field: GQLField): GQLFieldDefinition? {
    return when (this) {
      is GQLUnionTypeDefinition ->
        memberTypes.firstNotNullOfOrNull {
          lookupType(it.name)?.resolveFieldDefinition(field)
        }
      is GQLObjectTypeDefinition ->
        fieldDefinitions(schema).find { f -> f.name == field.name } ?: implementsInterfaces.firstNotNullOfOrNull {
          lookupType(it)?.resolveFieldDefinition(field)
        }
      is GQLInterfaceTypeDefinition ->
        fieldDefinitions(schema).find { f -> f.name == field.name } ?: implementsInterfaces.firstNotNullOfOrNull {
          lookupType(it)?.resolveFieldDefinition(field)
        }
      else -> null
    }
  }

  private fun lookupType(type: String): GQLTypeDefinition? =
    schema.typeDefinitions[type] ?: run {
      errors.add("Undefined type: `$type`")
      null
    }
  //endregion

  data class TestCase(
    val queryName: String,
    val gqlQuery: GQLOperationDefinition?,
    val pluginQuery: PluginQuery<*>,
    val schemaHolder: GraphQLSchemaAndQueryLoaderHolder,
    val auxiliaryFragments: List<GQLFragmentDefinition> = emptyList(),
  ) {
    override fun toString(): String {
      return "${queryName} - ${schemaHolder.displayName}"
    }
  }
}

private object TestCases {
  private val DUMMY_SERVER_PATH = GithubServerPath.DEFAULT_SERVER
  private val DUMMY_REPO_PATH = GHRepositoryPath("user", "project")
  private val DUMMY_REPO_COORDINATES = GHRepositoryCoordinates(DUMMY_SERVER_PATH, DUMMY_REPO_PATH)
  private const val DUMMY_ORG = "jetbrains"
  private const val DUMMY_LOGIN = "user"
  private const val DUMMY_ID = "id"
  private const val DUMMY_TEXT = "some text"
  private val DUMMY_PAGINATION = GraphQLRequestPagination.DEFAULT
  private val DUMMY_REACTION = GHReactionContent.THUMBS_DOWN
  private val DUMMY_PR_EVENT = GHPullRequestReviewEvent.APPROVE
  private const val DUMMY_NUMBER = 2L
  private val DUMMY_SIDE = Side.LEFT

  private val QUERIES by lazy {
    listOf(
      GHGQLRequests.User.find(DUMMY_SERVER_PATH, DUMMY_LOGIN),

      GHGQLRequests.Organization.Team.findAll(DUMMY_SERVER_PATH, DUMMY_ORG, DUMMY_PAGINATION),
      GHGQLRequests.Organization.Team.findAll(DUMMY_SERVER_PATH, DUMMY_ORG, null),

      GHGQLRequests.Repo.find(DUMMY_REPO_COORDINATES),
      GHGQLRequests.Repo.loadPullRequestTemplates(DUMMY_REPO_COORDINATES),
      GHGQLRequests.Repo.getProtectionRules(DUMMY_REPO_COORDINATES, DUMMY_PAGINATION),
      GHGQLRequests.Repo.getProtectionRules(DUMMY_REPO_COORDINATES, null),
      GHGQLRequests.Repo.getCommitStatus(DUMMY_REPO_COORDINATES, DUMMY_TEXT),
      GHGQLRequests.Repo.getCommitStatusContext(DUMMY_REPO_COORDINATES, DUMMY_TEXT, DUMMY_PAGINATION),

      GHGQLRequests.Comment.updateComment(DUMMY_SERVER_PATH, DUMMY_ID, DUMMY_TEXT),
      GHGQLRequests.Comment.deleteComment(DUMMY_SERVER_PATH, DUMMY_ID),
      GHGQLRequests.Comment.addReaction(DUMMY_SERVER_PATH, DUMMY_ID, DUMMY_REACTION),
      GHGQLRequests.Comment.removeReaction(DUMMY_SERVER_PATH, DUMMY_ID, DUMMY_REACTION),

      GHGQLRequests.PullRequest.findOneId(DUMMY_REPO_COORDINATES, DUMMY_NUMBER),
      GHGQLRequests.PullRequest.create(DUMMY_REPO_COORDINATES, DUMMY_TEXT, DUMMY_TEXT, DUMMY_TEXT, DUMMY_TEXT, DUMMY_TEXT, true),
      GHGQLRequests.PullRequest.create(DUMMY_REPO_COORDINATES, DUMMY_TEXT, DUMMY_TEXT, DUMMY_TEXT, DUMMY_TEXT, null, false),
      GHGQLRequests.PullRequest.findOne(DUMMY_REPO_COORDINATES, DUMMY_NUMBER),
      GHGQLRequests.PullRequest.update(DUMMY_REPO_COORDINATES, DUMMY_ID, DUMMY_TEXT, DUMMY_TEXT),
      GHGQLRequests.PullRequest.update(DUMMY_REPO_COORDINATES, DUMMY_ID, null, null),
      GHGQLRequests.PullRequest.markReadyForReview(DUMMY_REPO_COORDINATES, DUMMY_ID),
      GHGQLRequests.PullRequest.mergeabilityData(DUMMY_REPO_COORDINATES, DUMMY_NUMBER),
      GHGQLRequests.PullRequest.search(DUMMY_SERVER_PATH, DUMMY_TEXT, DUMMY_PAGINATION),
      GHGQLRequests.PullRequest.search(DUMMY_SERVER_PATH, DUMMY_TEXT, null),
      GHGQLRequests.PullRequest.metrics(DUMMY_REPO_COORDINATES),
      GHGQLRequests.PullRequest.reviewThreads(DUMMY_REPO_COORDINATES, DUMMY_NUMBER, DUMMY_PAGINATION),
      GHGQLRequests.PullRequest.reviewThreads(DUMMY_REPO_COORDINATES, DUMMY_NUMBER, null),
      GHGQLRequests.PullRequest.commits(DUMMY_REPO_COORDINATES, DUMMY_NUMBER, DUMMY_PAGINATION),
      GHGQLRequests.PullRequest.commits(DUMMY_REPO_COORDINATES, DUMMY_NUMBER, null),
      GHGQLRequests.PullRequest.files(DUMMY_REPO_COORDINATES, DUMMY_NUMBER, DUMMY_PAGINATION),
      GHGQLRequests.PullRequest.markFileAsViewed(DUMMY_SERVER_PATH, DUMMY_TEXT, DUMMY_TEXT),
      GHGQLRequests.PullRequest.unmarkFileAsViewed(DUMMY_SERVER_PATH, DUMMY_TEXT, DUMMY_TEXT),

      GHGQLRequests.PullRequest.Timeline.items(DUMMY_SERVER_PATH, DUMMY_TEXT, DUMMY_TEXT, DUMMY_NUMBER, DUMMY_PAGINATION),
      GHGQLRequests.PullRequest.Timeline.items(DUMMY_SERVER_PATH, DUMMY_TEXT, DUMMY_TEXT, DUMMY_NUMBER, null),
      GHGQLRequests.PullRequest.Review.create(DUMMY_SERVER_PATH, DUMMY_TEXT, DUMMY_PR_EVENT, DUMMY_TEXT, DUMMY_TEXT, listOf()),
      GHGQLRequests.PullRequest.Review.create(DUMMY_SERVER_PATH, DUMMY_TEXT, null, null, null, null),
      GHGQLRequests.PullRequest.Review.submit(DUMMY_SERVER_PATH, DUMMY_TEXT, DUMMY_PR_EVENT, DUMMY_TEXT),
      GHGQLRequests.PullRequest.Review.submit(DUMMY_SERVER_PATH, DUMMY_TEXT, DUMMY_PR_EVENT, null),
      GHGQLRequests.PullRequest.Review.updateBody(DUMMY_SERVER_PATH, DUMMY_TEXT, DUMMY_TEXT),
      GHGQLRequests.PullRequest.Review.delete(DUMMY_SERVER_PATH, DUMMY_TEXT),
      GHGQLRequests.PullRequest.Review.pendingReviews(DUMMY_SERVER_PATH, DUMMY_TEXT),
      GHGQLRequests.PullRequest.Review.addComment(DUMMY_SERVER_PATH, DUMMY_TEXT, DUMMY_TEXT, DUMMY_TEXT, DUMMY_TEXT, DUMMY_NUMBER.toInt()),
      GHGQLRequests.PullRequest.Review.addComment(DUMMY_SERVER_PATH, DUMMY_TEXT, DUMMY_TEXT, DUMMY_TEXT),
      GHGQLRequests.PullRequest.Review.deleteComment(DUMMY_SERVER_PATH, DUMMY_TEXT),
      GHGQLRequests.PullRequest.Review.updateComment(DUMMY_SERVER_PATH, DUMMY_TEXT, DUMMY_TEXT),
      GHGQLRequests.PullRequest.Review.addThread(DUMMY_SERVER_PATH, DUMMY_TEXT, DUMMY_TEXT, DUMMY_NUMBER.toInt(), DUMMY_SIDE, DUMMY_NUMBER.toInt(), DUMMY_TEXT),
      GHGQLRequests.PullRequest.Review.resolveThread(DUMMY_SERVER_PATH, DUMMY_TEXT),
      GHGQLRequests.PullRequest.Review.unresolveThread(DUMMY_SERVER_PATH, DUMMY_TEXT),
    )
  }

  private val QUERIES_BY_PATH by lazy {
    QUERIES.groupBy { it.queryName }
  }

  fun <TestCase> constructTestCases(f: (queryName: String, pluginQueries: List<PluginQuery<*>>, GraphQLSchemaAndQueryLoaderHolder) -> List<TestCase>): List<TestCase> {
    val schemas = GHGQLTestSchemas.all
    val queryNames = GHGQLQueryTestData.queryNames

    return schemas.flatMap { schema ->
      queryNames.flatMap { queryName ->
        val queryPath = GHGQLQueryTestData.toPath(queryName)
        val queries = QUERIES_BY_PATH[queryPath] ?: error("""
          Missing query object for ${queryPath}.
          
          Please create an instance of org.jetbrains.plugins.github.api.GithubApiRequest.Post.GQLQuery
          inside org.jetbrains.plugins.github.api.TestCases#QUERIES.
        """.trimIndent())

        f(queryName, queries, schema)
      }
    }
  }
}

/**
 * Test to show off the assumptions that must/can be made while testing data classes.
 */
class GHGQLDeserializationAssumptionsTest {
  companion object {
    private val mapper = GithubApiContentHelper.getObjectMapper(gqlNaming = true)
  }

  data class ListHolder(
    val l: List<String>,
  )

  data class ListHolderWithDefault(
    val l: List<String> = emptyList(),
  )

  @Test
  fun `deserializing a missing value instead of a list is not fine`() {
    assertThrows<Exception> { mapper.readValue("""{}""", ListHolder::class.java) }
  }

  @Test
  fun `deserializing a missing value instead of a list is fine with default`() {
    assertDoesNotThrow { mapper.readValue("""{}""", ListHolderWithDefault::class.java) }
  }

  @Test
  fun `deserializing a null instead of a list is fine`() {
    assertDoesNotThrow { mapper.readValue("""{"l":null}""", ListHolder::class.java) }
  }

  @Test
  fun `deserializing a null inside a list is fine`() {
    assertDoesNotThrow { mapper.readValue("""{"l":[null]}""", ListHolder::class.java) }
  }
}

/**
 * Used to sanity-check the capabilities of the above tests.
 *
 * Here, you can read/write what type of issues are expected to be caught by each of the tests in an example-form.
 */
internal class MetaTest {
  data class SingleValueHolder(
    val v1: String,
  )

  @Test
  fun `field may be missing from DTO class if present in fragment`() {
    // more data is OK, but missing data is certain failure
    `dto testing succeeds for`(
      pluginQuery = simple(SingleValueHolder::class.java),
      schema = """
        type Query {
          v1: String!
          v2: String!
        }
      """.trimIndent(),
      query = """
        query {
          v1
          v2
        }
      """.trimIndent()
    )
  }

  @Test
  fun `field may not be missing from fragment if present in DTO class`() {
    `dto testing fails for`(
      pluginQuery = simple(SingleValueHolder::class.java),
      // note that omitting '!' means the String is nullable
      schema = """
        type Query {
          v1: String!
          v2: String!
        }
      """.trimIndent(),
      query = """
        query {
          v2
        }
      """.trimIndent(),
      expectedErrors = listOf(
        "Field is defined in class, but not in fragment: `v1` in `[simple type, class org.jetbrains.plugins.github.api.MetaTest\$SingleValueHolder]`"
      )
    )
  }

  data class NestedClassHolder(
    val n: Nested,
  ) {
    data class Nested(
      val v1: String,
      val v2: String,
    )
  }

  @Test
  fun `nested fragments are also checked (positive)`() {
    `dto testing succeeds for`(
      pluginQuery = simple(NestedClassHolder::class.java),
      // note that omitting '!' means the String is nullable
      schema = """
        type Nested {
          v1: String!
          v2: String!
        }
        
        type Query {
          n: Nested!
        }
      """.trimIndent(),
      query = """
        query {
          n {
            v1
            v2
          }
        }
      """.trimIndent()
    )
  }

  @Test
  fun `nested fragments are also checked (negative)`() {
    `dto testing fails for`(
      pluginQuery = simple(NestedClassHolder::class.java),
      // note that omitting '!' means the String is nullable
      schema = """
        type Nested {
          v1: String!
        }
        
        type Query {
          n: Nested!
        }
      """.trimIndent(),
      query = """
        query {
          n {
            v1
          }
        }
      """.trimIndent(),
      expectedErrors = listOf(
        "Field is defined in class, but not in fragment: `v2` in `[simple type, class org.jetbrains.plugins.github.api.MetaTest\$NestedClassHolder\$Nested]`"
      )
    )
  }

  data class StringHolder(
    val l: String,
  )

  @Test
  fun `nullable GQL fields cannot be deserialized into non-nullable fields`() {
    `dto testing fails for`(
      // note that omitting '!' means the String is nullable
      pluginQuery = simple(StringHolder::class.java),
      schema = """
        type Query {
          l: String
        }
      """.trimIndent(),
      query = """
        query {
          l
        }
      """.trimIndent(),
      expectedErrors = listOf(
        "Field `l` is non-null (`[simple type, class java.lang.String]`), but nullable according to the schema (`String`)"
      )
    )
  }

  @Test
  fun `non-nullable GQL fields can(!) be deserialized into non-nullable fields`() {
    `dto testing succeeds for`(
      pluginQuery = simple(StringHolder::class.java),
      // here '!' means that the String is non-null
      schema = """
        type Query {
          l: String!
        }
      """.trimIndent(),
      query = """
        query {
          l
        }
      """.trimIndent()
    )
  }

  enum class EnumExample {
    A, B, C
  }

  data class EnumHolder(
    val e: EnumExample,
  )

  @Test
  fun `enum classes may not miss any values from the GQL enum`() {
    `dto testing fails for`(
      pluginQuery = simple(EnumHolder::class.java),
      // here '!' means that the String is non-null
      schema = """
        enum EnumExample {
          A
          B
          C
          D
        }
        
        type Query {
          e: EnumExample!
        }
      """.trimIndent(),
      query = """
        query {
          e
        }
      """.trimIndent(),
      expectedErrors = listOf(
        "Enum value `D` missing in type `org.jetbrains.plugins.github.api.MetaTest\$EnumExample` for field `e`"
      )
    )
  }

  @Test
  fun `enum classes may define more values than the GQL enum`() {
    `dto testing succeeds for`(
      pluginQuery = simple(EnumHolder::class.java),
      // here '!' means that the String is non-null
      schema = """
        enum EnumExample {
          A
          B
        }
        
        type Query {
          e: EnumExample!
        }
      """.trimIndent(),
      query = """
        query {
          e
        }
      """.trimIndent()
    )
  }

  data class ListHolderWithoutDefault(
    val l: List<String>,
  )

  @Test
  fun `non-defaulted nullable lists are caught`() {
    `dto testing fails for`(
      pluginQuery = simple(ListHolderWithoutDefault::class.java),
      schema = """
        type Query {
          l: [String!]
        }
      """.trimIndent(),
      query = """
        query {
          l        
        }
      """.trimIndent(),
      expectedErrors = listOf(
        "Field `l` is non-null (`[collection type; class java.util.List, contains [simple type, class java.lang.String]]`), but nullable according to the schema (`[String!]`)"
      )
    )
  }

  @Test
  fun `nullable list entries are okay, they're just filtered out`() {
    `dto testing succeeds for`(
      pluginQuery = simple(ListHolderWithoutDefault::class.java),
      schema = """
        type Query {
          l: [String]!
        }
      """.trimIndent(),
      query = """
        query {
          l        
        }
      """.trimIndent()
    )
  }

  data class ListHolderWithDefault(
    val l: List<String> = emptyList(),
  )

  @Test
  fun `defaulted nullable lists are fine`() {
    `dto testing succeeds for`(
      pluginQuery = simple(ListHolderWithDefault::class.java),
      schema = """
        type Query {
          l: [String!]
        }
      """.trimIndent(),
      query = """
        query {
          l        
        }
      """.trimIndent()
    )
  }

  @Test
  fun `supplying missing parameters is problematic`() {
    `dto testing fails for`(
      pluginQuery = simple(ListHolderWithDefault::class.java, variablesObject = mapOf(
        "param" to "value"
      )),
      schema = """
        type Query {
          l: [String!]
        }
      """.trimIndent(),
      query = """
        query {
          l        
        }
      """.trimIndent(),
      expectedErrors = listOf(
        "Variable `param` is not used in the query."
      )
    )
  }

  @Test
  fun `failing to supply required parameters is problematic`() {
    `dto testing fails for`(
      pluginQuery = simple(ListHolderWithDefault::class.java, variablesObject = mapOf()),
      schema = """
        type Query {
          l: [String!]
        }
      """.trimIndent(),
      query = """
        query(${"\$param"}: String!) {
          l        
        }
      """.trimIndent(),
      expectedErrors = listOf(
        "Variable `param` is not supplied in the variables object."
      )
    )
  }

  @Test
  fun `failing to supply defaulted parameters is fine`() {
    `dto testing succeeds for`(
      pluginQuery = simple(ListHolderWithDefault::class.java, variablesObject = mapOf()),
      schema = """
        type Query {
          l: [String!]
        }
      """.trimIndent(),
      query = """
        query(${"\$param"}: String! = "default") {
          l
        }
      """.trimIndent()
    )
  }

  @Test
  fun `failing to supply nullable parameters is fine`() {
    `dto testing succeeds for`(
      pluginQuery = simple(ListHolderWithDefault::class.java, variablesObject = mapOf()),
      schema = """
        type Query {
          l: [String!]
        }
      """.trimIndent(),
      query = """
        query(${"\$param"}: String) {
          l        
        }
      """.trimIndent(),
    )
  }

  @Test
  fun `correctly supplying parameters is fine`() {
    `dto testing succeeds for`(
      pluginQuery = simple(ListHolderWithDefault::class.java, variablesObject = mapOf(
        "param" to "value"
      )),
      schema = """
        type Query {
          l: [String!]
        }
      """.trimIndent(),
      query = """
        query(${"\$param"}: String!) {
          l        
        }
      """.trimIndent(),
    )
  }

  @Test
  fun `missing fields in schema are caught`() {
    `fragment testing fails for`(
      schema = """
        type Query {
          l1: [String!]
        }
      """.trimIndent(),
      query = """
        query {
          l2
        }
      """.trimIndent(),
      expectedErrors = listOf(
        "Can't query `l2` on type `Query`"
      )
    )
  }

  @Test
  fun `inline fragment on out-of-hierarchy type is caught`() {
    `fragment testing fails for`(
      schema = """
        type User {
          name: String!
        }
        
        type Bot {
          botName: String!
        }
        
        union RequestedReviewer = User
        
        type Query {
          q: RequestedReviewer
        }
      """.trimIndent(),
      query = """
        fragment f on RequestedReviewer {
          ... on User {
            name
          }
          
          ... on Bot {
            botName
          }
        }
        
        query {
          q {
            ...f
          }
        }
      """.trimIndent(),
      expectedErrors = listOf(
        "Inline fragment cannot be spread here as result can never be of type `Bot`"
      )
    )
  }

  data class Example(
    val name: String
  )

  @Test
  @Ignore
  @IJIgnore(issue = "disabled")
  fun `query path resolution finds nullable components`() {
    `dto testing fails for`(
      pluginQuery = traversed(Example::class.java, "q"),
      schema = """
        type Example {
          name: String!
        }
        
        type Query {
          q: Example
        }
      """.trimIndent(),
      query = """
        query {
          q {
            name
          }
        }
      """.trimIndent(),
      expectedErrors = listOf(
        "Field `q` in Example is nullable, but is expected to be non-null"
      )
    )
  }

  @Test
  fun `queries can't have differently shaped types - nullability`() {
    `fragment testing fails for`(
      schema = """
        type A {
          name: String
        }
        
        type B {
          name: String!
        }
        
        union U = A | B
        
        type Query {
          q: U
        }
      """.trimIndent(),
      query = """
        query {
          q {
            ... on A {
              name
            }
            ... on B {
              name
            }
          }
        }
      """.trimIndent(),
      expectedErrors = listOf(
        "`name` cannot be merged with `name` (at null: (7, 7)): they have different shapes. Use different aliases on the fields to fetch both if this was intentional.",
        "`name` cannot be merged with `name` (at null: (4, 7)): they have different shapes. Use different aliases on the fields to fetch both if this was intentional."
      )
    )
  }

  @Test
  fun `queries can't have differently shaped types - aliasing solves it`() {
    `fragment testing succeeds for`(
      schema = """
        type A {
          name: String
        }
        
        type B {
          name: String!
        }
        
        union U = A | B
        
        type Query {
          q: U
        }
      """.trimIndent(),
      query = """
        query {
          q {
            ... on A {
              aName: name
            }
            ... on B {
              bName: name
            }
          }
        }
      """.trimIndent()
    )
  }

  @Test
  fun `queries can't have differently shaped types - completely different types`() {
    `fragment testing fails for`(
      schema = """
        type A {
          id: String!
        }
        
        type B {
          id: Int!
        }
        
        union U = A | B
        
        type Query {
          q: U
        }
      """.trimIndent(),
      query = """
        query {
          q {
            ... on A {
              id
            }
            ... on B {
              id
            }
          }
        }
      """.trimIndent(),
      expectedErrors = listOf(
        "`id` cannot be merged with `id` (at null: (7, 7)): they have different shapes. Use different aliases on the fields to fetch both if this was intentional.",
        "`id` cannot be merged with `id` (at null: (4, 7)): they have different shapes. Use different aliases on the fields to fetch both if this was intentional."
      )
    )
  }

  @Test
  fun `smoke test fragment meta-testing`() {
    `fragment testing succeeds for`(
      schema = """
        type Query {
          name: String!
        }
      """.trimIndent(),
      query = """
        query {
          name
        }
      """.trimIndent()
    )
  }

  //region: Boilerplate
  private fun simple(clazz: Class<*>, variablesObject: Map<String, Any?> = mapOf()): GQLQuery<*> =
    GQLQuery.Parsed("", "", variablesObject, clazz)
  private fun traversed(clazz: Class<*>, vararg pathFromData: String): GQLQuery<*> =
    GQLQuery.TraversedParsed("", "", mapOf(), clazz, *pathFromData)

  private fun `fragment testing fails for`(query: String, schema: String, expectedErrors: List<String>? = null) {
    val test = createQueryTest(query, schema)
    assertDoesNotThrow {
      test.`test query text is not null`()
    }
    runCatching {
      test.`test fragment is valid in schema`()
    }.onSuccess {
      assertThat(false)
        .withFailMessage { "Expected test to fail" }
        .isTrue()
    }.onFailure {
      if (expectedErrors != null) {
        assertThat(test.issues.map { it.message }.toSet())
          .isEqualTo(expectedErrors.toSet())
      }
    }
  }

  private fun `fragment testing succeeds for`(query: String, schema: String) {
    val test = createQueryTest(query, schema)
    assertDoesNotThrow {
      test.`test query text is not null`()
      test.`test fragment is valid in schema`()
    }
  }

  private fun `dto testing fails for`(pluginQuery: PluginQuery<*>, query: String, schema: String, expectedErrors: List<String>? = null) {
    val test = createDTOTest(pluginQuery, query, schema)
    assertDoesNotThrow {
      test.`test query exists and is not empty`()
    }
    runCatching {
      test.`test dto class matches the query + fragments + schema`()
    }.onSuccess {
      assertThat(false)
        .withFailMessage { "Expected test to fail" }
        .isTrue()
    }.onFailure {
      if (expectedErrors != null) {
        assertThat(test.errors.toSet())
          .isEqualTo(expectedErrors.toSet())
      }
    }
  }

  private fun `dto testing succeeds for`(pluginQuery: PluginQuery<*>, query: String, schema: String) {
    val test = createDTOTest(pluginQuery, query, schema)
    assertDoesNotThrow {
      test.`test query exists and is not empty`()
      test.`test dto class matches the query + fragments + schema`()
    }
  }

  @OptIn(ApolloExperimental::class)
  private fun createQueryTest(query: String, schema: String): GHGQLQueryTest {
    val schemaWithQueryPlaceholder = """
      ${schema}
    """.trimIndent()

    return GHGQLQueryTest(GHGQLQueryTest.TestCase(
      queryName = "<meta-query>",
      schemaHolder = GraphQLSchemaAndQueryLoaderHolder(
        "<meta-schema>", "<inline>",
        GHGQLTestSchemas.parseSchemaFromString(schemaWithQueryPlaceholder),
        GHGQLQueryLoader
      ),
      queryText = query
    ))
  }

  @OptIn(ApolloExperimental::class)
  private fun createDTOTest(pluginQuery: PluginQuery<*>, query: String, schema: String): GHGQLDTOTest {
    val definitions = query.parseAsGQLDocument().getOrThrow().definitions
    val gqlQuery = definitions.filterIsInstance<GQLOperationDefinition>().first()
    val fragments = definitions.filterIsInstance<GQLFragmentDefinition>()

    val parsedSchema = GHGQLTestSchemas.parseSchemaFromString(schema)

    return GHGQLDTOTest(GHGQLDTOTest.TestCase(
      queryName = "<meta-query>",
      pluginQuery = pluginQuery,
      gqlQuery = gqlQuery,
      auxiliaryFragments = fragments,
      schemaHolder = GraphQLSchemaAndQueryLoaderHolder("<meta-schema>", "<inline>", parsedSchema, GHGQLQueryLoader),
    ))
  }
  //endregion
}
