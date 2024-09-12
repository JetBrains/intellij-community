package com.intellij.cce.actions

import com.intellij.cce.core.SimpleTokenProperties
import com.intellij.cce.core.SymbolLocation
import com.intellij.cce.core.TypeProperty
import com.intellij.cce.workspace.storages.storage.ActionsStorageFactory
import com.intellij.cce.workspace.storages.storage.ActionsStorageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

private const val file1 = "file1"
private const val file2 = "file2"

class ActionsMultiplyFilesStorageTest : ActionsStorageTestBase(ActionsStorageType.MULTIPLY_FILES)

class ActionsSingleFileStorageTest : ActionsStorageTestBase(ActionsStorageType.SINGLE_FILE)

abstract class ActionsStorageTestBase(private val storageType: ActionsStorageType)  {
  @field:TempDir
  lateinit var tempDir: File

  @Test
  fun `test actions for 2 files`() {
    val storage = ActionsStorageFactory.create(tempDir.absolutePath, storageType)
    val actions = createFileActionsCollection()
    actions.forEach {
      storage.saveActions(it)
    }

    val actionFiles = storage.getActionFiles()

    assertEquals(listOf(file1, file2).count(), actionFiles.count())
    repeat(2) { index ->
      assertEquals(actions[index], storage.getActions(actionFiles[index]))
    }
  }

  @Test
  fun `compute session files`() {
    val storage = ActionsStorageFactory.create(tempDir.absolutePath, storageType)
    val actions = createFileActionsCollection()
    actions.forEach {
      storage.saveActions(it)
    }

    assertEquals(3, storage.computeSessionsCount())
  }

  private fun createFileActionsCollection(): List<FileActions> {
    return listOf(
      createSingleFileActions(
        file1,
        ActionsBuilder().also {
          it.session {
            moveCaret(5)
            callFeature("expectedText1", 300, SimpleTokenProperties.create(TypeProperty.TOKEN, SymbolLocation.UNKNOWN) {})
          }
        }.build()
      ),
      createSingleFileActions(
        file2,
        ActionsBuilder().also {
          it.session {
            moveCaret(15)
            callFeature("expectedText2", 350, SimpleTokenProperties.create(TypeProperty.TOKEN, SymbolLocation.UNKNOWN) {})
          }
          it.session {
            printText("some text")
            callFeature("expectedText3", 550, SimpleTokenProperties.create(TypeProperty.TOKEN, SymbolLocation.UNKNOWN) {})
          }
        }.build()
      ),
    )
  }

  private fun createSingleFileActions(path: String, actions: List<Action>) =
    FileActions(path, "checksum_for_$path", actions.count { it is CallFeature}, actions)
}