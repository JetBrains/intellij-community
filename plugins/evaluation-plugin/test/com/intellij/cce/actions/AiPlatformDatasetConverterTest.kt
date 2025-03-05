package com.intellij.cce.actions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AiPlatformDatasetConverterTest {
  @Test
  fun convert() {
    val fileName1 = "file1"
    val fileName2 = "file2"
    val fileContentProvider = object : FileContentProvider {
      override fun getContent(path: String): String {
        return when (path) {
          fileName1 -> "prefix1golden1suffix1"
          fileName2 -> "prefix2_1golden2_1suffix2_1 prefix2_2golden2_2suffix2_2"
          else -> error("wrong $path")
        }
      }
    }
    val actions = AiPlatformDatasetConverter(fileContentProvider).convert(listOf(
        AiPlatformDatasetItem("suffix2_1", "golden2_1", "prefix2_1", AiPlatformDatasetMetadata(fileName2, "source")),
        AiPlatformDatasetItem("suffix2_2", "golden2_2", "prefix2_2", AiPlatformDatasetMetadata(fileName2, "source")),
        AiPlatformDatasetItem("suffix1", "golden1", "prefix1", AiPlatformDatasetMetadata(fileName1, "source")),
      )
    )
    assertEquals(2, actions.size)
    assertEquals(1, actions.first().sessionsCount)
    assertEquals(2, actions.last().sessionsCount)
  }
}