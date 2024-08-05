// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ai.assistedReview

fun extractJsonFromResponse(response: String): String {
  val extractJsonRegex = Regex("```json\n([\\s\\S]*?)\n```")
  val matches = extractJsonRegex.findAll(response)
  val jsonString = matches.firstOrNull()?.value ?: response
  val openSymbol = jsonString.indexOfFirst { it == '[' || it == '{' }
  val closeSymbol = jsonString.indexOfLast { it == ']' || it == '}' }
  if (openSymbol != -1 && closeSymbol != -1) {
    return jsonString.substring(openSymbol, closeSymbol + 1)
  }
  return jsonString
}
