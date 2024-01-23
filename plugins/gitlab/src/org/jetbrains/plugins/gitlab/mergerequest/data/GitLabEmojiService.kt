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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.io.File

@Service(Service.Level.APP)
internal class GitLabEmojiService(cs: CoroutineScope) {
  private val mapper = jacksonObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)

  val emojis: Deferred<List<ParsedGitLabEmoji>> = cs.async {
    parseEmojisFile()
  }

  private fun parseEmojisFile(): List<ParsedGitLabEmoji> {
    val path = GitLabEmojiService::class.java.classLoader.getResource("emoji/index.json") ?: error("File was not found")
    val json = File(path.toURI())
    val parsedData = mapper.readValue(json, object : TypeReference<Map<String, ParsedGitLabEmoji>>() {})

    return parsedData.values.toList()
  }
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