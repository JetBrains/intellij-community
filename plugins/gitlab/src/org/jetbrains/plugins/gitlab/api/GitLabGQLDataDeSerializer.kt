// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.collaboration.api.dto.GraphQLErrorDTO
import com.intellij.collaboration.api.dto.GraphQLResponseDTO
import com.intellij.collaboration.api.graphql.GraphQLDataDeserializer
import com.intellij.collaboration.api.graphql.GraphQLErrorException
import com.intellij.collaboration.api.json.JsonDataSerializer
import org.jetbrains.plugins.gitlab.api.GitLabRestJsonDataDeSerializer.genericConfig
import java.io.Reader

object GitLabGQLDataDeSerializer : JsonDataSerializer, GraphQLDataDeserializer {

  private val mapper: ObjectMapper = jacksonObjectMapper()
    .genericConfig()
    .setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)

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
