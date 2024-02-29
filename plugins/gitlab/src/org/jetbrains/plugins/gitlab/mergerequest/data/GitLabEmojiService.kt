// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.components.Service
import com.intellij.openapi.util.NlsSafe
import kotlinx.coroutines.*

@Service(Service.Level.APP)
internal class GitLabEmojiService(cs: CoroutineScope) {
  private val mapper = jacksonObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)

  val emojis: Deferred<List<ParsedGitLabEmoji>> = cs.async(Dispatchers.IO, CoroutineStart.LAZY) {
    parseEmojisFile()
  }

  private fun parseEmojisFile(): List<ParsedGitLabEmoji> =
    GitLabEmojiService::class.java.classLoader.getResourceAsStream("emoji/index.json")?.use {
      mapper.readValue(it, object : TypeReference<Map<String, ParsedGitLabEmoji>>() {})
    }?.values?.toList() ?: error("File was not found")
}

data class ParsedGitLabEmoji(
  @JsonProperty("name") val description: String,
  val shortname: String,
  val category: String,
  val moji: @NlsSafe String
) {
  @JsonIgnore
  val name = shortname.removePrefix(":").removeSuffix(":")
}