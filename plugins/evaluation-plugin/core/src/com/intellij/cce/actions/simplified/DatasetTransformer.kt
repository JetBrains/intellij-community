package com.intellij.cce.actions.simplified

import com.intellij.cce.actions.*
import com.intellij.cce.actions.simplified.SimplifiedDatasetSerializer.parseLineRange
import com.intellij.cce.core.SimpleTokenProperties
import com.intellij.cce.core.SymbolLocation
import com.intellij.cce.core.TypeProperty
import com.intellij.cce.evaluable.FILE_CHANGED_LINES_PREFIX
import com.intellij.cce.evaluable.FILE_PATTERN_PROPERTY_PREFIX
import com.intellij.cce.evaluable.FILE_UNCHANGED_LINES_PREFIX
import com.intellij.cce.evaluable.PROMPT_PROPERTY
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

fun main(args: Array<String>) {
  if (args.size < 3) {
    error("All arguments are required: <projectPath> <simplifiedActionsPath> <actionsPath>")
    error("Example: command <projectPath> <simplifiedActionsPath> <actionsPath>")
    return
  }
  val (rootPath, inputPath, outputPath) = args

  val datasetContent = Files.readString(Paths.get(inputPath))
  val simplifiedDataset = SimplifiedDatasetSerializer.parseJson(datasetContent).toList()

  val transformer = DatasetTransformer(FileOffsetProvider(rootPath))
  val transformedDataset = transformer.transform(simplifiedDataset)

  val resultContent = ActionArraySerializer.serialize(transformedDataset.toTypedArray())
  Files.writeString(Paths.get(outputPath), resultContent)
}

interface OffsetProvider {
  fun getLineStartOffset(filePath: String, line: Int): Int
  fun getLineEndOffset(filePath: String, line: Int): Int
}

class DatasetTransformer(private val offsetProvider: OffsetProvider) {
  fun transform(simplifiedDataset: List<SimplifiedDatasetSerializer.InteractionData>): List<FileActions> {
    val fileGroups: Map<String, List<SimplifiedDatasetSerializer.InteractionData>> = simplifiedDataset.groupBy { interaction -> interaction.position.path }
    return fileGroups.map { (path, interactions) -> createFileActions(path, interactions) }
  }

  fun createFileActions(path: String, interactions: List<SimplifiedDatasetSerializer.InteractionData>): FileActions {
    val actions = interactions.flatMap(::createInteractionActions)
    return FileActions(path, null, interactions.size, actions)
  }

  fun createInteractionActions(interaction: SimplifiedDatasetSerializer.InteractionData): List<Action> {
    val uuidString = with (interaction) { position.path + position.caretLine + position.selectionLines + userPrompt }
    val sessionId = UUID.nameUUIDFromBytes(uuidString.toByteArray(Charsets.UTF_8))

    return openFileActions(sessionId, interaction) +
           positionActions(sessionId, interaction.position) +
           generateActions(sessionId, interaction)
  }

  private fun openFileActions(sessionId: UUID, interaction: SimplifiedDatasetSerializer.InteractionData): List<OpenFileInBackground> {
    return interaction.openFiles.map { path -> OpenFileInBackground(sessionId, path) }
  }

  private fun generateActions(sessionId: UUID, interaction: SimplifiedDatasetSerializer.InteractionData): List<CallFeature> {
    val fileValidation = interaction.fileValidations.firstOrNull()
    val nodeProperties = SimpleTokenProperties.create(TypeProperty.FILE, SymbolLocation.PROJECT) {
      put(PROMPT_PROPERTY,interaction.userPrompt)
      fileValidation?.patterns.orEmpty().forEachIndexed { i, pattern ->
        put("${FILE_PATTERN_PROPERTY_PREFIX}_${i+1}", pattern)
      }
      fileValidation?.changedLines.orEmpty().forEachIndexed { i, pattern ->
        put("${FILE_CHANGED_LINES_PREFIX}_${i+1}", pattern)
      }
      fileValidation?.unchangedLines.orEmpty().forEachIndexed { i, pattern ->
        put("${FILE_UNCHANGED_LINES_PREFIX}_${i+1}", pattern)
      }
    }
    val position = interaction.position
    val defaultLine = position.caretLine ?: parseLineRange(position.selectionLines)?.first ?: 0
    val offset = offsetProvider.getLineEndOffset(position.path, defaultLine)
    return listOf(CallFeature(sessionId, "", offset, nodeProperties))
  }

  private fun positionActions(sessionId: UUID, position: SimplifiedDatasetSerializer.Position): List<Action> {
    val actions = mutableListOf<Action>()
    val caretLine = position.caretLine
    if (caretLine != null) {
      actions.add(MoveCaret(sessionId, offsetProvider.getLineEndOffset(position.path, caretLine)))
    }
    val selectionLines = parseLineRange(position.selectionLines ?: "")
    if (selectionLines != null) {
      val startOffset = offsetProvider.getLineStartOffset(position.path, selectionLines.first)
      val endOffset = offsetProvider.getLineEndOffset(position.path, selectionLines.last)
      actions.add(SelectRange(sessionId, startOffset, endOffset))
    }
    return actions
  }

}

private class FileOffsetProvider(private val rootPath: String) : OffsetProvider {

  override fun getLineStartOffset(filePath: String, line: Int): Int {
    val systemLine = line - 1
    val path = Paths.get(rootPath, filePath)
    val lines = path.takeIf { Files.exists(it) }?.let { Files.readAllLines(it) } ?: return -1
    return lines.take(systemLine).sumOf { it.length + 1 }
  }

  override fun getLineEndOffset(filePath: String, line: Int): Int {
    val systemLine = line - 1
    val path = Paths.get(rootPath, filePath)
    val lines = path.takeIf { Files.exists(it) }?.let { Files.readAllLines(it) } ?: return -1
    // Subtract 1 because sumOf includes the newline after the last line
    return lines.take(systemLine + 1).sumOf { it.length + 1 } - 1
  }
}