// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.context

import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.number
import com.intellij.agent.workbench.prompt.core.objOrNull
import com.intellij.agent.workbench.prompt.core.string
import com.intellij.agent.workbench.prompt.ui.AgentPromptBundle
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.actions.PasteAction
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.Producer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.awt.Color
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.DataFlavor.imageFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentPromptImagePasteProviderTest {
  @Test
  fun copiedImageFileListIsPreferredOverImageFlavorIcon() {
    val imageFile = createTempImageFile("agent-prompt-paste-real-", filledImage(4, 5, Color.BLUE))
    val iconImage = filledImage(1, 1, Color.GRAY)
    val addedItems = pasteWithProvider(fileListTransferable(listOf(imageFile), imageFlavorImage = iconImage))

    try {
      assertThat(addedItems).hasSize(1)
      assertPastedImageContextItem(addedItems.single(), width = 4, height = 5)
    }
    finally {
      deleteContextFiles(addedItems)
      Files.deleteIfExists(imageFile)
    }
  }

  @Test
  fun rawImageFlavorPasteAddsImageContext() {
    val addedItems = pasteWithProvider(imageTransferable(filledImage(2, 3, Color.RED)))

    try {
      assertThat(addedItems).hasSize(1)
      assertPastedImageContextItem(addedItems.single(), width = 2, height = 3)
    }
    finally {
      deleteContextFiles(addedItems)
    }
  }

  @Test
  fun nonImageFileListWithImageFlavorIconIsIgnored() {
    val textFile = Files.createTempFile("agent-prompt-paste-text-", ".txt")
    val iconImage = filledImage(1, 1, Color.GRAY)

    try {
      val addedItems = pasteWithProvider(
        fileListTransferable(listOf(textFile), imageFlavorImage = iconImage),
        expectEnabled = false,
      )

      assertThat(addedItems).isEmpty()
    }
    finally {
      Files.deleteIfExists(textFile)
    }
  }

  @Test
  fun multipleCopiedImageFilesAddMultipleContextItems() {
    val firstImage = createTempImageFile("agent-prompt-paste-first-", filledImage(6, 7, Color.GREEN))
    val secondImage = createTempImageFile("agent-prompt-paste-second-", filledImage(8, 9, Color.ORANGE))
    val textFile = Files.createTempFile("agent-prompt-paste-text-", ".txt")
    val addedItems = pasteWithProvider(fileListTransferable(listOf(firstImage, textFile, secondImage)))

    try {
      assertThat(addedItems).hasSize(2)
      assertPastedImageContextItem(addedItems[0], width = 6, height = 7)
      assertPastedImageContextItem(addedItems[1], width = 8, height = 9)
    }
    finally {
      deleteContextFiles(addedItems)
      listOf(firstImage, secondImage, textFile).forEach(Files::deleteIfExists)
    }
  }

  private fun pasteWithProvider(transferable: Transferable, expectEnabled: Boolean = true): List<AgentPromptContextItem> {
    val provider = AgentPromptImagePasteProvider()
    val addedItems = ArrayList<AgentPromptContextItem>()
    runInEdtAndWait {
      withPromptEditor { editor ->
        val dataContext = promptPasteDataContext(editor, transferable, addedItems)

        assertThat(provider.isPastePossible(dataContext)).isEqualTo(expectEnabled)
        assertThat(provider.isPasteEnabled(dataContext)).isEqualTo(expectEnabled)

        provider.performPaste(dataContext)
      }
    }
    return addedItems
  }

  private fun withPromptEditor(block: (Editor) -> Unit) {
    val editorFactory = EditorFactory.getInstance()
    val editor = editorFactory.createEditor(editorFactory.createDocument(""))
    try {
      block(editor)
    }
    finally {
      editorFactory.releaseEditor(editor)
    }
  }

  private fun promptPasteDataContext(
    editor: Editor,
    transferable: Transferable,
    addedItems: MutableList<AgentPromptContextItem>,
  ): DataContext {
    editor.putUserData(AGENT_PROMPT_IMAGE_PASTE_HANDLER_KEY, AgentPromptImagePasteHandler { items ->
      addedItems.addAll(items)
    })
    return SimpleDataContext.builder()
      .add(CommonDataKeys.EDITOR, editor)
      .add(PasteAction.TRANSFERABLE_PROVIDER, Producer { transferable })
      .build()
  }

  private fun assertPastedImageContextItem(item: AgentPromptContextItem, width: Int, height: Int) {
    val payload = checkNotNull(item.payload.objOrNull())
    val filePath = checkNotNull(payload.string("filePath"))

    assertThat(item.rendererId).isEqualTo(AgentPromptContextRendererIds.SNIPPET)
    assertThat(item.title).isEqualTo(AgentPromptBundle.message("manual.context.paste.image.title"))
    assertThat(item.itemId).isEqualTo(IMAGE_PASTE_SOURCE_ID)
    assertThat(item.source).isEqualTo("pastedImage")
    assertThat(payload.string("type")).isEqualTo("screenshot")
    assertThat(payload.number("width")).isEqualTo(width.toString())
    assertThat(payload.number("height")).isEqualTo(height.toString())
    assertThat(Files.exists(Path.of(filePath))).isTrue()
  }

  private fun createTempImageFile(prefix: String, image: BufferedImage): Path {
    val path = Files.createTempFile(prefix, ".png")
    Files.newOutputStream(path).use { output -> ImageIO.write(image, "png", output) }
    return path
  }

  private fun deleteContextFiles(items: List<AgentPromptContextItem>) {
    items.mapNotNull { item -> item.payload.objOrNull()?.string("filePath") }
      .forEach { filePath -> Files.deleteIfExists(Path.of(filePath)) }
  }

  private fun filledImage(width: Int, height: Int, color: Color): BufferedImage {
    return BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also { image ->
      val graphics = image.createGraphics()
      try {
        graphics.color = color
        graphics.fillRect(0, 0, width, height)
      }
      finally {
        graphics.dispose()
      }
    }
  }
}

private fun imageTransferable(image: BufferedImage): Transferable {
  return object : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(imageFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == imageFlavor

    override fun getTransferData(flavor: DataFlavor): Any {
      if (!isDataFlavorSupported(flavor)) {
        throw UnsupportedFlavorException(flavor)
      }
      return image
    }
  }
}

private fun fileListTransferable(paths: List<Path>, imageFlavorImage: BufferedImage? = null): Transferable {
  return object : Transferable {
    private val files = paths.map(Path::toFile)

    override fun getTransferDataFlavors(): Array<DataFlavor> {
      return if (imageFlavorImage == null) arrayOf(DataFlavor.javaFileListFlavor) else arrayOf(DataFlavor.javaFileListFlavor, imageFlavor)
    }

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor in transferDataFlavors

    override fun getTransferData(flavor: DataFlavor): Any {
      return when (flavor) {
        DataFlavor.javaFileListFlavor -> files
        imageFlavor -> imageFlavorImage ?: throw UnsupportedFlavorException(flavor)
        else -> throw UnsupportedFlavorException(flavor)
      }
    }
  }
}
