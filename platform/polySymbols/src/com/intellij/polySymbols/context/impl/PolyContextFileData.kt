// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.context.impl

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileCachedValue
import com.intellij.openapi.vfs.getCachedValue
import com.intellij.polySymbols.PolyContextKind
import com.intellij.polySymbols.PolyContextName
import com.intellij.polySymbols.context.PolyContext
import com.intellij.util.text.CharSequenceReader
import com.intellij.util.text.minimatch.Minimatch
import com.intellij.util.text.minimatch.MinimatchOptions
import java.io.IOException

internal class PolyContextFileData private constructor(
  private val file: VirtualFile,
  private val contexts: List<ContextEntry>,
) {

  fun getContextsInDirectory(directory: VirtualFile, priorityOffset: Int): List<DirectoryContext> {
    val path = VfsUtil.getRelativePath(directory, file.parent)?.let { "/$it/" }
               ?: return emptyList()
    return contexts
      .filter { it.dirPattern?.match(path) ?: true }
      .map { DirectoryContext(it.filePattern, it.context, it.priority + priorityOffset + (if (it.filePattern != null) 0.5f else 0f)) }
  }

  class DirectoryContext(
    private val filePattern: Regex?,
    val context: Map<PolyContextKind, PolyContextName>,
    val priority: Float,
  ) {
    fun matches(fileName: String?): Boolean =
      filePattern == null || (fileName != null && filePattern.matches(fileName))

    override fun toString(): String =
      "DirectoryContext(filePattern=$filePattern, context=$context, priority=$priority)"
  }

  private data class ContextEntry(
    val dirPattern: Minimatch?,
    val filePattern: Regex?,
    val context: Map<PolyContextKind, PolyContextName>,
    val priority: Int,
  )

  companion object {
    private val POLY_SYMBOLS_CONTEXT_FILE_DATA = Key<VirtualFileCachedValue<PolyContextFileData>>("poly-symbols-context-file-data")

    fun getOrCreate(file: VirtualFile): PolyContextFileData =
      file.getCachedValue(POLY_SYMBOLS_CONTEXT_FILE_DATA, provider = ::parseSafely)

    private fun parseSafely(file: VirtualFile, contents: CharSequence?): PolyContextFileData {
      if (!contents.isNullOrEmpty()) {
        try {
          return parse(file, contents)
        }
        catch (e: Exception) {
          thisLogger().debug("Failed to parse " + file.path, e)
        }
      }
      return PolyContextFileData(file, emptyList())
    }

    private fun parse(file: VirtualFile, contents: CharSequence): PolyContextFileData {
      val reader = JsonReader(CharSequenceReader(contents))
      reader.isLenient = true
      if (reader.peek() != JsonToken.BEGIN_OBJECT) {
        throw IOException("Top level element should be an object")
      }
      val contexts = mutableListOf<ContextEntry>()
      readContextPattern(reader, "", contexts)
      return PolyContextFileData(file, contexts)
    }

    private fun readContextPattern(reader: JsonReader, dirPattern: String, contexts: MutableList<ContextEntry>) {
      reader.beginObject()
      val dirContext = mutableMapOf<PolyContextKind, PolyContextName>()
      while (reader.hasNext()) {
        val key = reader.nextName()
        if (reader.peek() == JsonToken.BEGIN_OBJECT) {
          val nestedPattern = if (dirPattern.isNotEmpty() && !dirPattern.endsWith("/"))
            "$dirPattern/${key.removePrefix("/")}"
          else
            dirPattern + key
          readContextPattern(reader, nestedPattern, contexts)
        }
        else if (reader.peek() == JsonToken.STRING) {
          dirContext[key] = reader.nextString()
        }
        else if (reader.peek() == JsonToken.NULL) {
          reader.nextNull()
          dirContext[key] = PolyContext.VALUE_NONE
        }
        else
          reader.skipValue()
      }
      reader.endObject()
      if (dirContext.isNotEmpty()) {
        contexts.add(
          if (dirPattern.isEmpty())
            ContextEntry(null, null, dirContext, 0)
          else
            buildContextEntry(dirPattern, dirContext))
      }
    }

    private fun buildContextEntry(pattern: String, dirContext: MutableMap<PolyContextKind, PolyContextName>): ContextEntry {
      val lastSlash = pattern.lastIndexOf('/')
      val filePattern = pattern.substring(lastSlash + 1).takeIf { it != "**" }
      val dirPattern = (if (filePattern == null) pattern else if (lastSlash >= 0) pattern.substring(0, lastSlash) else "/")
        .let { if (it.startsWith("/")) it else "/$it" }
      val priority = dirPattern.count { it == '/' } - StringUtil.getOccurrenceCount(dirPattern, "/**/") -
                     (if (dirPattern.endsWith("/**")) 1 else 0) - 1

      return ContextEntry(
        Minimatch(if (!dirPattern.endsWith("/**")) "$dirPattern/" else dirPattern,
                  MinimatchOptions(nonegate = true, nocomment = true, nobrace = true, dot = true)),
        filePattern?.split('*')?.joinToString(".*") { if (it.isNotEmpty()) Regex.escape(it) else it }?.let { Regex(it) },
        dirContext, priority)
    }
  }

}