// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PackageDirectoryMismatch", "unused")

package com.intellij.agent.workbench.json

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import java.nio.file.Files
import java.nio.file.Path

object WorkbenchJsonlScanner {
  fun <S> scanJsonObjects(
    path: Path,
    jsonFactory: JsonFactory,
    maxObjects: Int = Int.MAX_VALUE,
    newState: () -> S,
    onObject: (JsonParser, S) -> Boolean,
  ): S {
    if (maxObjects <= 0) return newState()

    val fastState = newState()
    val parsedWithSingleParser = try {
      parseWithSingleParser(path, jsonFactory, maxObjects, fastState, onObject)
      true
    }
    catch (_: Throwable) {
      false
    }
    if (parsedWithSingleParser) {
      return fastState
    }

    val fallbackState = newState()
    parseLineByLine(path, jsonFactory, maxObjects, fallbackState, onObject)
    return fallbackState
  }

  private fun <S> parseWithSingleParser(
    path: Path,
    jsonFactory: JsonFactory,
    maxObjects: Int,
    state: S,
    onObject: (JsonParser, S) -> Boolean,
  ) {
    Files.newBufferedReader(path).use { reader ->
      jsonFactory.createParser(reader).use { parser ->
        var parsedObjects = 0
        while (parsedObjects < maxObjects) {
          val token = parser.nextToken() ?: return
          if (token != JsonToken.START_OBJECT) {
            parser.skipChildren()
            continue
          }

          parsedObjects++
          if (!onObject(parser, state)) {
            return
          }
        }
      }
    }
  }

  private fun <S> parseLineByLine(
    path: Path,
    jsonFactory: JsonFactory,
    maxObjects: Int,
    state: S,
    onObject: (JsonParser, S) -> Boolean,
  ) {
    Files.newBufferedReader(path).use { reader ->
      var parsedObjects = 0
      while (parsedObjects < maxObjects) {
        val line = reader.readLine() ?: return
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue

        val shouldContinue = parseLineObject(trimmed, jsonFactory, state, onObject)
        if (shouldContinue == null) {
          continue
        }

        parsedObjects++
        if (!shouldContinue) {
          return
        }
      }
    }
  }

  private fun <S> parseLineObject(
    line: String,
    jsonFactory: JsonFactory,
    state: S,
    onObject: (JsonParser, S) -> Boolean,
  ): Boolean? {
    return try {
      jsonFactory.createParser(line).use { parser ->
        if (parser.nextToken() != JsonToken.START_OBJECT) {
          return null
        }
        onObject(parser, state)
      }
    }
    catch (_: Throwable) {
      null
    }
  }
}
