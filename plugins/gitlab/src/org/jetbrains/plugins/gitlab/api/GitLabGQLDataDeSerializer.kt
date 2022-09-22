// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.introspect.VisibilityChecker
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.collaboration.api.dto.GraphQLErrorDTO
import com.intellij.collaboration.api.dto.GraphQLResponseDTO
import com.intellij.collaboration.api.graphql.GraphQLDataDeserializer
import com.intellij.collaboration.api.graphql.GraphQLErrorException
import com.intellij.collaboration.api.json.JsonDataSerializer
import java.io.Reader
import java.text.SimpleDateFormat
import java.util.*

object GitLabGQLDataDeSerializer : JsonDataSerializer, GraphQLDataDeserializer {

  private val mapper: ObjectMapper = jacksonObjectMapper()
    .genericConfig()
    .setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)

  private fun ObjectMapper.genericConfig(): ObjectMapper =
    this.setDateFormat(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX"))
      .setTimeZone(TimeZone.getDefault())
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
      .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
      .setSerializationInclusion(JsonInclude.Include.NON_NULL)
      .setVisibility(VisibilityChecker.Std(JsonAutoDetect.Visibility.NONE,
                                           JsonAutoDetect.Visibility.NONE,
                                           JsonAutoDetect.Visibility.NONE,
                                           JsonAutoDetect.Visibility.NONE,
                                           JsonAutoDetect.Visibility.ANY))

  override fun toJsonBytes(content: Any): ByteArray = mapper.writeValueAsBytes(content)

  override fun <T> readAndTraverseGQLResponse(bodyReader: Reader, pathFromData: Array<out String>, clazz: Class<T>): T? =
    readAndTraverseGQLResponse(pathFromData, clazz) {
      mapper.readValue(bodyReader, it)
    }

  private fun <T> readAndTraverseGQLResponse(pathFromData: Array<out String>,
                                             clazz: Class<T>,
                                             responseSupplier: (JavaType) -> GraphQLResponseDTO<out JsonNode, GraphQLErrorDTO>): T? {
    val responseType = mapper.typeFactory
      .constructParametricType(GraphQLResponseDTO::class.java, JsonNode::class.java, GraphQLErrorDTO::class.java)
    val gqlResponse: GraphQLResponseDTO<out JsonNode, GraphQLErrorDTO> = responseSupplier(responseType)
    return traverseGQLResponse(gqlResponse, pathFromData, clazz)
  }

  private fun <T> traverseGQLResponse(result: GraphQLResponseDTO<out JsonNode, GraphQLErrorDTO>,
                                      pathFromData: Array<out String>,
                                      clazz: Class<T>): T? {
    val data = result.data
    if (data != null && !data.isNull) {
      var node: JsonNode = data
      for (path in pathFromData) {
        node = node[path] ?: break
      }
      if (!node.isNull) return mapper.readValue(node.toString(), clazz)
    }
    val errors = result.errors
    if (errors == null) return null
    else throw GraphQLErrorException(errors)
  }
}
