package com.intellij.cce.actions

import com.google.gson.Gson
import com.intellij.cce.core.TokenProperties
import com.intellij.cce.util.FileTextUtil

//todo move to sub-package and split into files
class AiPlatformDatasetParser {
  fun parse(text: String): List<AiPlatformDatasetItem> {
    val gson = Gson()

    return text.lines()
      .filter { it.isNotEmpty() }
      .map { gson.fromJson(it, AiPlatformDatasetItem::class.java) }
  }
}

data class AiPlatformDatasetItem(
  val suffix: String,
  val golden: String,
  val prefix: String,
  val metadata: AiPlatformDatasetMetadata,
)

data class AiPlatformDatasetMetadata(
  val path: String,
  val source: String,
)

interface FileContentProvider {
  fun getContent(path: String): String
}

class AiPlatformDatasetConverter(private val fileContentProvider: FileContentProvider) {
  fun convert(items: List<AiPlatformDatasetItem>): List<FileActions> {
    return items.groupBy { it.metadata.path }
      .toList()
      .sortedBy { it.first }
      .map { (filePath, itemsForFile) ->
        val fileContent = fileContentProvider.getContent(filePath)
        val checksum = FileTextUtil.computeChecksum(fileContent)
        val actions = itemsForFile.map { item -> item.convert(fileContent) }
          .sortedBy { it.callFeatureOffset }
          .map { it.actions }
          .flatten()
        FileActions(filePath, checksum, itemsForFile.size, actions)
      }
  }

  private fun AiPlatformDatasetItem.convert(content: String): ItemActions {
    val item = this
    val originalPrefix = item.prefix
    val originalSuffixStart = content.indexOf(originalPrefix)
    if (originalSuffixStart < 0) {
      //todo log error
      return ItemActions(emptyList(), -1)
    }

    val offset = originalSuffixStart + originalPrefix.length
    val endOffset = offset + item.golden.length
    val expectedText = item.golden

    val actions = with(ActionsBuilder.SessionBuilder()) {
      moveCaret(offset)
      deleteRange(offset, endOffset)
      callFeature(expectedText, offset, TokenProperties.UNKNOWN)
      printText(expectedText)
      this.build()
    }
    return ItemActions(actions, offset)
  }
}

private data class ItemActions(val actions: List<Action>, val callFeatureOffset: Int)

data class AiPlatformFileRefData(val file: String, val repo: String)