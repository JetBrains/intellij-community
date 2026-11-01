// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import com.intellij.collaboration.api.dto.GraphQLErrorDTO
import com.intellij.collaboration.api.dto.GraphQLResponseDTO
import com.intellij.collaboration.api.graphql.GraphQLDataDeserializer
import com.intellij.collaboration.api.json.JsonDataSerializer
import org.jetbrains.plugins.gitlab.api.GitLabRestJsonDataDeSerializer.gitlabJacksonMapperBuilder
import tools.jackson.databind.JavaType
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.PropertyNamingStrategies
import java.io.InputStream
import java.io.Reader
import java.nio.charset.Charset
import java.text.SimpleDateFormat

object GitLabGQLDataDeSerializer : JsonDataSerializer, GraphQLDataDeserializer {

  private val mapper: ObjectMapper = gitlabJacksonMapperBuilder()
    .defaultDateFormat(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX"))
    .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
    .build()

  override fun toJsonBytes(content: Any): ByteArray = mapper.writeValueAsBytes(content)

  override fun <T> readAndMapGQLResponse(bodyReader: Reader, pathFromData: Array<out String>, clazz: Class<T>)
    : GraphQLResponseDTO<T?, GraphQLErrorDTO> =
    readAndMapGQLResponse(pathFromData, clazz) {
      mapper.readValue(bodyReader, it)
    }

  override fun <T : Any> readAndMapGQLResponse(
    stream: InputStream,
    charset: Charset,
    pathFromData: Array<out String>,
    clazz: Class<T>,
  ): GraphQLResponseDTO<T?, GraphQLErrorDTO> {
    return readAndMapGQLResponse(pathFromData, clazz) {
      mapper.readValue(stream, it)
    }
  }

  private fun <T> readAndMapGQLResponse(
    pathFromData: Array<out String>,
    clazz: Class<T>,
    responseSupplier: (JavaType) -> GraphQLResponseDTO<out JsonNode, GraphQLErrorDTO>,
  )
    : GraphQLResponseDTO<T?, GraphQLErrorDTO> {
    val responseType = mapper.typeFactory
      .constructParametricType(GraphQLResponseDTO::class.java, JsonNode::class.java, GraphQLErrorDTO::class.java)
    val gqlResponse: GraphQLResponseDTO<out JsonNode, GraphQLErrorDTO> = responseSupplier(responseType)
    return mapGQLResponse(gqlResponse, pathFromData, clazz)
  }

  private fun <T> mapGQLResponse(
    result: GraphQLResponseDTO<out JsonNode, GraphQLErrorDTO>,
    pathFromData: Array<out String>,
    clazz: Class<T>,
  ): GraphQLResponseDTO<T?, GraphQLErrorDTO> {
    val data = result.data
    if (data != null && !data.isNull) {
      var node: JsonNode = data
      for (path in pathFromData) {
        node = node[path] ?: break
      }
      if (!node.isNull) {
        val value = mapper.readValue(node.toString(), clazz)
        return GraphQLResponseDTO(value, result.errors)
      }
    }
    return GraphQLResponseDTO(null, result.errors)
  }
}
