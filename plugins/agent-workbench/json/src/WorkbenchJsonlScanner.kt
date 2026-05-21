// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PackageDirectoryMismatch", "unused")

package com.intellij.agent.workbench.json

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

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

  fun <S> scanTailLines(
    path: Path,
    jsonFactory: JsonFactory,
    tailBytes: Long = 16_384L,
    newState: () -> S,
    onObject: (JsonParser, S) -> Boolean,
  ): S {
    val state = newState()
    try {
      FileChannel.open(path, StandardOpenOption.READ).use { channel ->
        val fileSize = channel.size()
        val seekPos = maxOf(0L, fileSize - tailBytes)
        channel.position(seekPos)
        val reader = BufferedReader(InputStreamReader(Channels.newInputStream(channel), Charsets.UTF_8))
        // If we seeked mid-file, skip the first partial line.
        if (seekPos > 0) {
          reader.readLine()
        }
        while (true) {
          val line = reader.readLine() ?: break
          val trimmed = line.trim()
          if (trimmed.isEmpty()) continue
          val shouldContinue = parseLineObject(trimmed, jsonFactory, state, onObject) ?: continue
          if (!shouldContinue) break
        }
      }
    }
    catch (_: Exception) {
      // Tail scan is best-effort; return whatever state we collected.
    }
    return state
  }

  /**
   * Removes lines from a JSONL file where [shouldRemove] returns `true`.
   * Deletes the file entirely if no lines remain after removal.
   *
   * @param shouldRemove receives a [JsonParser] positioned at `START_OBJECT`; return `true` to remove the line
   */
  fun removeLines(
    path: Path,
    jsonFactory: JsonFactory,
    shouldRemove: (JsonParser) -> Boolean,
  ) {
    if (!Files.exists(path)) return
    val lines = Files.readAllLines(path)
    val remaining = lines.filter { line ->
      val trimmed = line.trim()
      if (trimmed.isEmpty()) return@filter false
      val remove = try {
        jsonFactory.createParser(trimmed).use { parser ->
          if (parser.nextToken() != JsonToken.START_OBJECT) false
          else shouldRemove(parser)
        }
      }
      catch (_: Throwable) {
        false
      }
      !remove
    }
    if (remaining.isEmpty()) {
      Files.deleteIfExists(path)
    }
    else {
      Files.writeString(path, remaining.joinToString("\n", postfix = "\n"))
    }
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
