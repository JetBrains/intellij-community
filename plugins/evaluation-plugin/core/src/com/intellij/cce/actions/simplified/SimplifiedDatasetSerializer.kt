package com.intellij.cce.actions.simplified

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken

object SimplifiedDatasetSerializer {
  private val gson = GsonBuilder().create()

  fun parseFileValidations(jsonData: String): List<FileValidation> {
    val listType = object : TypeToken<List<FileValidation>>() {}.type
    return gson.fromJson(jsonData, listType)
  }

  fun serializeFileValidations(fileValidations: List<FileValidation>): String {
    return gson.toJson(fileValidations)
  }

  /**
   * Parses a string representing a range of integers in the format "start-end" and returns it as an IntRange object.
   */
  fun parseLineRange(value: String?): IntRange? {
    if (value == null) return null
    val numbers: List<Int?> = value.trim().split("-").map { int -> int.toIntOrNull() }
    if (numbers.size != 2) return null
    val (start, end) = numbers
    if (start == null || end == null) return null
    return IntRange(start, end)
  }

  fun parseJson(jsonData: String): Array<InteractionData> {
    return gson.fromJson(jsonData, Array<InteractionData>::class.java)
  }

  data class Position(
    val path: String,
    val caretLine: Int? = null,
    val selectionLines: String? = null,
  )

  data class FileValidation(
    val path: String,
    val patterns: List<String>,
    val changedLines: List<String>,
    val unchangedLines: List<String>,
  )

  data class InteractionData(
    val openFiles: List<String>,
    val position: Position,
    val userPrompt: String,
    val fileValidations: List<FileValidation>,
    val referenceImplementation: String?,
  )
}