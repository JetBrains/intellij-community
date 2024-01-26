// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

internal class GithubServerPathSerializer : KSerializer<GithubServerPath> {
  private val serializer = JsonElement.serializer()
  override val descriptor: SerialDescriptor = serializer.descriptor

  override fun deserialize(decoder: Decoder): GithubServerPath {
    val jsonElement = serializer.deserialize(decoder)
    val jsonObject = jsonElement.jsonObject
    val useHttp = jsonObject[USE_HTTP]?.jsonPrimitive?.booleanOrNull
    val host = jsonObject[HOST]?.jsonPrimitive?.content!!
    val port = jsonObject[PORT]?.jsonPrimitive?.intOrNull
    val suffix = jsonObject[SUFFIX]?.jsonPrimitive?.contentOrNull
    return GithubServerPath(useHttp, host, port, suffix)
  }

  override fun serialize(encoder: Encoder, value: GithubServerPath) {
    val jsonElement = JsonObject(buildMap {
      this[USE_HTTP] = JsonPrimitive(value.schema == "http")
      this[HOST] = JsonPrimitive(value.host)
      this[PORT] = JsonPrimitive(value.port)
      this[SUFFIX] = JsonPrimitive(value.suffix)
    })
    JsonElement.serializer().serialize(encoder, jsonElement)
  }
}

private const val USE_HTTP = "useHttp"
private const val HOST = "host"
private const val PORT = "port"
private const val SUFFIX = "suffix"