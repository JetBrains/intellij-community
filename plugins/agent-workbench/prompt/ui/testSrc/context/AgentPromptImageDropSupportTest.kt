// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.context

import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.number
import com.intellij.agent.workbench.prompt.core.objOrNull
import com.intellij.agent.workbench.prompt.core.string
import com.intellij.agent.workbench.prompt.ui.AgentPromptBundle
import com.intellij.ide.dnd.DnDAction
import com.intellij.ide.dnd.DnDDropHandler
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDManager
import com.intellij.ide.dnd.DnDNativeTarget
import com.intellij.ide.dnd.DnDSource
import com.intellij.ide.dnd.DnDTarget
import com.intellij.ide.dnd.DropActionHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.awt.RelativeRectangle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Point
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.DataFlavor.imageFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JPanel

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentPromptImageDropSupportTest {
  @TestDisposable
  lateinit var disposable: Disposable

  @Test
  fun installsDndTargetWithoutTransferHandler() {
    val component = JPanel()
    val dndManager = RecordingDnDManager()
    ApplicationManager.getApplication().replaceService(DnDManager::class.java, dndManager, disposable)

    installAgentPromptImageDropSupport(component, dropHandler = { true }, parentDisposable = disposable)

    assertThat(dndManager.targetFor(component)).isNotNull
    assertThat(component.transferHandler).isNull()
  }

  @Test
  fun installsDialogDndTargetsRecursively() {
    val root = JPanel()
    val child = JPanel()
    val nestedChild = JPanel()
    child.add(nestedChild)
    root.add(child)
    val dndManager = RecordingDnDManager()
    ApplicationManager.getApplication().replaceService(DnDManager::class.java, dndManager, disposable)

    installAgentPromptDialogImageDropSupport(root, dropHandler = { true }, parentDisposable = disposable)

    assertThat(dndManager.targetFor(root)).isNotNull
    assertThat(dndManager.targetFor(child)).isNotNull
    assertThat(dndManager.targetFor(nestedChild)).isNotNull
  }

  @Test
  fun installsDialogDndTargetsForDynamicallyAddedComponents() {
    val root = JPanel()
    val dndManager = RecordingDnDManager()
    ApplicationManager.getApplication().replaceService(DnDManager::class.java, dndManager, disposable)
    installAgentPromptDialogImageDropSupport(root, dropHandler = { true }, parentDisposable = disposable)

    val child = JPanel()
    root.add(child)
    val nestedChild = JPanel()
    child.add(nestedChild)

    assertThat(dndManager.targetFor(child)).isNotNull
    assertThat(dndManager.targetFor(nestedChild)).isNotNull
  }

  @Test
  fun nativeImageFlavorDropAddsImageContext() {
    val addedItems = ArrayList<AgentPromptContextItem>()
    val target = installAndGetTarget { items ->
      addedItems.addAll(items)
      true
    }
    val event = FakeDnDEvent(
      attachedObject = DnDNativeTarget.EventInfo(arrayOf(imageFlavor), imageTransferable(filledImage(2, 3, Color.RED))),
      transferDataFlavors = arrayOf(imageFlavor),
    )

    assertThat(target.update(event)).isFalse()
    assertThat(event.isDropPossible).isTrue()

    val handled = (target as DnDDropHandler.WithResult).tryDrop(event)

    try {
      assertThat(handled).isTrue()
      assertThat(addedItems).hasSize(1)
      assertDroppedImageContextItem(addedItems.single(), width = 2, height = 3)
    }
    finally {
      deleteContextFiles(addedItems)
    }
  }

  @Test
  fun nativeImageFileDropAddsReadableImagesOnly() {
    val firstImage = createTempImageFile("agent-prompt-drop-first-", filledImage(4, 5, Color.BLUE))
    val textFile = Files.createTempFile("agent-prompt-drop-text-", ".txt")
    val secondImage = createTempImageFile("agent-prompt-drop-second-", filledImage(6, 7, Color.GREEN))
    val addedItems = ArrayList<AgentPromptContextItem>()
    val target = installAndGetTarget { items ->
      addedItems.addAll(items)
      true
    }
    val event = FakeDnDEvent(
      attachedObject = DnDNativeTarget.EventInfo(
        arrayOf(DataFlavor.javaFileListFlavor),
        fileListTransferable(listOf(firstImage, textFile, secondImage)),
      ),
      transferDataFlavors = arrayOf(DataFlavor.javaFileListFlavor),
    )

    assertThat(target.update(event)).isFalse()
    assertThat(event.isDropPossible).isTrue()

    val handled = (target as DnDDropHandler.WithResult).tryDrop(event)

    try {
      assertThat(handled).isTrue()
      assertThat(addedItems).hasSize(2)
      assertDroppedImageContextItem(addedItems[0], width = 4, height = 5)
      assertDroppedImageContextItem(addedItems[1], width = 6, height = 7)
    }
    finally {
      deleteContextFiles(addedItems)
      listOf(firstImage, textFile, secondImage).forEach(Files::deleteIfExists)
    }
  }

  @Test
  fun nonImageFileDropIsIgnored() {
    val textFile = Files.createTempFile("agent-prompt-drop-text-", ".txt")
    val addedItems = ArrayList<AgentPromptContextItem>()
    val target = installAndGetTarget { items ->
      addedItems.addAll(items)
      true
    }
    val event = FakeDnDEvent(
      attachedObject = DnDNativeTarget.EventInfo(arrayOf(DataFlavor.javaFileListFlavor), fileListTransferable(listOf(textFile))),
      transferDataFlavors = arrayOf(DataFlavor.javaFileListFlavor),
    )

    assertThat(target.update(event)).isTrue()
    assertThat(event.isDropPossible).isFalse()

    val handled = (target as DnDDropHandler.WithResult).tryDrop(event)

    assertThat(handled).isFalse()
    assertThat(addedItems).isEmpty()
    Files.deleteIfExists(textFile)
  }

  @Test
  fun editorImageFlavorDropAddsImageContext() {
    val addedItems = ArrayList<AgentPromptContextItem>()
    val handler = createEditorDropHandler { items ->
      addedItems.addAll(items)
      true
    }
    val transferable = imageTransferable(filledImage(2, 3, Color.RED))

    assertThat(handler.canHandleDrop(transferable.transferDataFlavors)).isTrue()
    handler.handleDrop(transferable, null, null)

    try {
      assertThat(addedItems).hasSize(1)
      assertDroppedImageContextItem(addedItems.single(), width = 2, height = 3)
    }
    finally {
      deleteContextFiles(addedItems)
    }
  }

  @Test
  fun editorImageFileDropAddsReadableImagesOnly() {
    val firstImage = createTempImageFile("agent-prompt-editor-drop-first-", filledImage(4, 5, Color.BLUE))
    val textFile = Files.createTempFile("agent-prompt-editor-drop-text-", ".txt")
    val secondImage = createTempImageFile("agent-prompt-editor-drop-second-", filledImage(6, 7, Color.GREEN))
    val addedItems = ArrayList<AgentPromptContextItem>()
    val handler = createEditorDropHandler { items ->
      addedItems.addAll(items)
      true
    }
    val transferable = fileListTransferable(listOf(firstImage, textFile, secondImage))

    assertThat(handler.canHandleDrop(transferable.transferDataFlavors)).isTrue()
    handler.handleDrop(transferable, null, null)

    try {
      assertThat(addedItems).hasSize(2)
      assertDroppedImageContextItem(addedItems[0], width = 4, height = 5)
      assertDroppedImageContextItem(addedItems[1], width = 6, height = 7)
    }
    finally {
      deleteContextFiles(addedItems)
      listOf(firstImage, textFile, secondImage).forEach(Files::deleteIfExists)
    }
  }

  @Test
  fun editorNonImageFileDropIsIgnored() {
    val textFile = Files.createTempFile("agent-prompt-editor-drop-text-", ".txt")
    val addedItems = ArrayList<AgentPromptContextItem>()
    val handler = createEditorDropHandler { items ->
      addedItems.addAll(items)
      true
    }
    val transferable = fileListTransferable(listOf(textFile))

    assertThat(handler.canHandleDrop(transferable.transferDataFlavors)).isTrue()
    handler.handleDrop(transferable, null, null)

    assertThat(addedItems).isEmpty()
    Files.deleteIfExists(textFile)
  }

  private fun installAndGetTarget(addItems: (List<AgentPromptContextItem>) -> Boolean): DnDTarget {
    val dndManager = RecordingDnDManager()
    val component = JPanel()
    ApplicationManager.getApplication().replaceService(DnDManager::class.java, dndManager, disposable)
    installAgentPromptImageDropSupport(component, dropHandler = addItems::invoke, parentDisposable = disposable)
    return checkNotNull(dndManager.targetFor(component))
  }

  private fun createEditorDropHandler(addItems: (List<AgentPromptContextItem>) -> Boolean): AgentPromptImageEditorDropHandler {
    return AgentPromptImageEditorDropHandler(AgentPromptImageDropHandler { items -> addItems(items) })
  }

  private fun assertDroppedImageContextItem(item: AgentPromptContextItem, width: Int, height: Int) {
    val payload = checkNotNull(item.payload.objOrNull())
    val filePath = checkNotNull(payload.string("filePath"))

    assertThat(item.rendererId).isEqualTo(AgentPromptContextRendererIds.SNIPPET)
    assertThat(item.title).isEqualTo(AgentPromptBundle.message("manual.context.paste.image.title"))
    assertThat(item.itemId).isEqualTo(IMAGE_DROP_SOURCE_ID)
    assertThat(item.source).isEqualTo("droppedImage")
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

private class RecordingDnDManager : DnDManager() {
  private val registeredTargets = LinkedHashMap<JComponent, DnDTarget>()

  fun targetFor(component: JComponent): DnDTarget? = registeredTargets[component]

  override fun registerTarget(target: DnDTarget?, component: JComponent?) {
    if (target != null && component != null) {
      registeredTargets[component] = target
    }
  }

  override fun registerTarget(target: DnDTarget, component: JComponent, parentDisposable: Disposable) {
    registeredTargets[component] = target
  }

  override fun unregisterTarget(target: DnDTarget?, component: JComponent?) {
    if (component != null) {
      registeredTargets.remove(component)
    }
  }

  override fun registerSource(source: DnDSource, component: JComponent) = Unit

  override fun registerSource(source: DnDSource, component: JComponent, parentDisposable: Disposable) = Unit

  override fun registerSource(source: com.intellij.ide.dnd.AdvancedDnDSource) = Unit

  override fun unregisterSource(source: DnDSource, component: JComponent) = Unit

  override fun unregisterSource(source: com.intellij.ide.dnd.AdvancedDnDSource) = Unit

  override fun getLastDropHandler(): Component? = null
}

private class FakeDnDEvent(
  private val attachedObject: Any?,
  private val transferDataFlavors: Array<DataFlavor>,
) : UserDataHolderBase(), DnDEvent {
  private var dropPossible: Boolean = false
  private var action: DnDAction = DnDAction.COPY
  private var orgPoint: Point = Point()
  private var localPoint: Point = Point()
  private var cursor: Cursor = Cursor.getDefaultCursor()

  override fun getAction(): DnDAction = action

  override fun updateAction(action: DnDAction) {
    this.action = action
  }

  override fun getAttachedObject(): Any? = attachedObject

  override fun setDropPossible(possible: Boolean, aExpectedResult: String?) {
    dropPossible = possible
  }

  override fun setDropPossible(possible: Boolean) {
    dropPossible = possible
  }

  override fun setDropPossible(aExpectedResult: String, aHandler: DropActionHandler) {
    dropPossible = true
  }

  override fun getExpectedDropResult(): String? = null

  override fun getTransferDataFlavors(): Array<DataFlavor> = transferDataFlavors

  override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = transferDataFlavors.contains(flavor)

  override fun getTransferData(flavor: DataFlavor): Any {
    if (!isDataFlavorSupported(flavor)) {
      throw UnsupportedFlavorException(flavor)
    }
    return when (val value = attachedObject) {
      is Transferable -> value.getTransferData(flavor)
      is DnDNativeTarget.EventInfo -> value.transferable.getTransferData(flavor)
      else -> throw UnsupportedFlavorException(flavor)
    }
  }

  override fun isDropPossible(): Boolean = dropPossible

  override fun getOrgPoint(): Point = orgPoint

  override fun setOrgPoint(orgPoint: Point) {
    this.orgPoint = orgPoint
  }

  override fun getPoint(): Point = localPoint

  override fun getPointOn(aComponent: Component): Point = localPoint

  override fun canHandleDrop(): Boolean = dropPossible

  override fun getHandlerComponent(): Component? = null

  override fun getCurrentOverComponent(): Component? = null

  override fun setHighlighting(aComponent: Component, aType: Int) = Unit

  override fun setHighlighting(rectangle: RelativeRectangle, aType: Int) = Unit

  override fun setHighlighting(layeredPane: JLayeredPane, rectangle: RelativeRectangle, aType: Int) = Unit

  override fun setAutoHideHighlighterInDrop(aValue: Boolean) = Unit

  override fun hideHighlighter() = Unit

  override fun setLocalPoint(localPoint: Point) {
    this.localPoint = localPoint
  }

  override fun getLocalPoint(): Point = localPoint

  override fun getRelativePoint(): RelativePoint = RelativePoint(JPanel(), localPoint)

  override fun clearDelegatedTarget() = Unit

  override fun wasDelegated(): Boolean = false

  override fun getDelegatedTarget(): DnDTarget? = null

  override fun delegateUpdateTo(target: DnDTarget): Boolean = false

  override fun delegateDropTo(target: DnDTarget) = Unit

  override fun getCursor(): Cursor = cursor

  override fun setCursor(cursor: Cursor) {
    this.cursor = cursor
  }

  override fun cleanUp() = Unit
}

private fun imageTransferable(image: BufferedImage): Transferable {
  return object : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(imageFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == imageFlavor

    override fun getTransferData(flavor: DataFlavor): Any = image
  }
}

private fun fileListTransferable(paths: List<Path>): Transferable {
  return object : Transferable {
    private val files = paths.map(Path::toFile)

    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.javaFileListFlavor

    override fun getTransferData(flavor: DataFlavor): Any = files
  }
}
