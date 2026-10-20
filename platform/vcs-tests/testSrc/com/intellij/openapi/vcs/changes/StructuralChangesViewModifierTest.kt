// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.util.PropertiesComponent
import com.intellij.lang.LanguageStructureViewBuilder
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserChangeNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserStructuralNode
import com.intellij.openapi.vcs.changes.ui.NoneChangesGroupingFactory
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.backend.navigation.impl.SourceNavigationRequest
import com.intellij.platform.vcs.VcsUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.virtualFileFixture
import com.intellij.ui.icons.HiDPIImage
import com.intellij.ui.paint.PaintUtil
import com.intellij.util.IconUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.Icon
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel

@TestApplication
internal class StructuralChangesViewModifierTest {
  private val project get() = projectFixture.get()
  private val fileFixture = sourceRootFixture.virtualFileFixture("changes.txt", "")
  private var presentationIcon: Icon? = null
  private val factory = PsiStructureViewFactory { file ->
    object : TreeBasedStructureViewBuilder() {
      override fun createStructureViewModel(editor: Editor?): StructureViewModel =
        StructureViewModelBase(file, editor, FileElement(file, presentationIcon))
    }
  }

  @BeforeEach
  fun setUp() {
    PropertiesComponent.getInstance(project).setValue(OPTION_ID, false)
    LanguageStructureViewBuilder.getInstance().addExplicitExtension(PlainTextLanguage.INSTANCE, factory)
  }

  @AfterEach
  fun tearDown() {
    LanguageStructureViewBuilder.getInstance().removeExplicitExtension(PlainTextLanguage.INSTANCE, factory)
    PropertiesComponent.getInstance(project).unsetValue(OPTION_ID)
  }

  @Test
  fun `structural changes are hidden by default`() {
    assertFalse(StructuralChangesViewSettings.isAvailable())
    assertToggleVisible(false)
  }

  @Test
  fun `registry gate preserves a saved option without adding children`(): Unit = timeoutRunBlocking {
    PropertiesComponent.getInstance(project).setValue(OPTION_ID, true)

    assertFalse(StructuralChangesViewSettings.isEnabled(project))
    assertToggleVisible(false)
    assertTrue(build(change(null, "first=1\nsecond=2")).isEmpty())
    assertTrue(PropertiesComponent.getInstance(project).getBoolean(OPTION_ID))
  }

  @Test
  @RegistryKey(key = StructuralChangesViewSettings.REGISTRY_KEY, value = "true")
  fun `toggle controls the structural children`(): Unit = timeoutRunBlocking {
    assertToggleVisible(true)
    val change = change(null, "first=1\nsecond=2")
    assertTrue(build(change).isEmpty())

    setSelected(true)
    assertEquals(listOf("first", "second"), build(change).map { it.textPresentation })

    setSelected(false)
    assertTrue(build(change).isEmpty())
  }

  @Test
  @RegistryKey(key = StructuralChangesViewSettings.REGISTRY_KEY, value = "true")
  fun `registry changes refresh the tree and retain the saved option`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    setSelected(true)
    val change = change(null, "first=1")
    assertEquals(1, build(change).size)
    val updates = AtomicInteger()
    project.messageBus.connect(disposable).subscribe(ChangesViewModifier.TOPIC, ChangesViewModifier.ChangesViewModifierListener {
      updates.incrementAndGet()
    })
    val registryValue = RegistryManager.getInstance().get(StructuralChangesViewSettings.REGISTRY_KEY)

    registryValue.setValue(false)
    assertEquals(1, updates.get())
    assertFalse(StructuralChangesViewSettings.isEnabled(project))
    assertTrue(PropertiesComponent.getInstance(project).getBoolean(OPTION_ID))
    assertTrue(build(change).isEmpty())

    registryValue.setValue(true)
    assertEquals(2, updates.get())
    assertTrue(StructuralChangesViewSettings.isEnabled(project))
    assertEquals(1, build(change).size)
  }

  @Test
  @RegistryKey(key = StructuralChangesViewSettings.REGISTRY_KEY, value = "true")
  fun `added files show their complete structure with navigation`(): Unit = timeoutRunBlocking {
    setSelected(true)
    val text = "first=1\nsecond=2"
    val nodes = build(change(null, text))

    assertEquals(listOf("first", "second"), nodes.map { it.textPresentation })
    val descriptor = nodes.last().getNavigatable() as OpenFileDescriptor
    assertEquals(fileFixture.get(), descriptor.file)
    assertEquals(text.indexOf("second"), descriptor.offset)
  }

  @Test
  @RegistryKey(key = StructuralChangesViewSettings.REGISTRY_KEY, value = "true")
  fun `preview navigation keeps explicit navigation separate`(): Unit = timeoutRunBlocking {
    setSelected(true)
    val text = "first=1\nsecond=2"
    val node = build(change(null, text)).last()
    val explicit = node.getNavigatable() as OpenFileDescriptor
    val preview = readAction { node.getPreviewNavigationRequest() as SourceNavigationRequest }

    assertFalse(explicit.isUsePreviewTab)
    assertEquals(fileFixture.get(), preview.file)
    assertEquals(text.indexOf("second"), preview.offsetMarker?.startOffset)
    assertEquals(explicit.offset, preview.offsetMarker?.startOffset)
    assertEquals(TextRange(text.indexOf("second"), text.length), node.getPreviewHighlightRange())
  }

  @Test
  @RegistryKey(key = StructuralChangesViewSettings.REGISTRY_KEY, value = "true")
  fun `preview ranges reject document edits after the model is built`(): Unit = timeoutRunBlocking {
    setSelected(true)
    val text = "first=1\nsecond=2"
    val change = change(null, text)
    val node = build(change).last()
    assertEquals(TextRange(text.indexOf("second"), text.length), node.getPreviewHighlightRange())

    val prefix = "inserted=0\n"
    edtWriteAction {
      CommandProcessor.getInstance().runUndoTransparentAction {
        FileDocumentManager.getInstance().getDocument(fileFixture.get())!!.insertString(0, prefix)
      }
    }

    assertNull(node.getPreviewHighlightRange())
    val current = build(change).last()
    assertEquals(TextRange(prefix.length + text.indexOf("second"), prefix.length + text.length), current.getPreviewHighlightRange())
  }

  @Test
  @RegistryKey(key = StructuralChangesViewSettings.REGISTRY_KEY, value = "true")
  fun `modified files omit unchanged structure elements`(): Unit = timeoutRunBlocking {
    setSelected(true)
    val before = "changed=1\nunchanged=2\nremoved=3"
    val after = "changed=4\nunchanged=2\nadded=5"
    val nodes = build(change(before, after))

    assertEquals(listOf("changed", "added", "removed"), nodes.map { it.textPresentation })
    assertNull(nodes.last().getNavigatable())
    assertNull(readAction { nodes.last().getPreviewNavigationRequest() })
    assertNull(nodes.last().getPreviewHighlightRange())
    assertEquals(after.indexOf("added"), (nodes[1].getNavigatable() as OpenFileDescriptor).offset)
  }

  @Test
  @RegistryKey(key = StructuralChangesViewSettings.REGISTRY_KEY, value = "true")
  fun `unavailable baseline does not mark the file as added`(): Unit = timeoutRunBlocking {
    setSelected(true)
    val added = change(null, "first=1\nsecond=2")
    val missingBaseline = revision(added.afterRevision!!.file, null)

    assertTrue(build(Change(missingBaseline, added.afterRevision)).isEmpty())
  }

  @Test
  @RegistryKey(key = StructuralChangesViewSettings.REGISTRY_KEY, value = "true")
  fun `structure uses the unsaved document text`(): Unit = timeoutRunBlocking {
    setSelected(true)
    val change = change("first=1\nsecond=2", "first=1\nsecond=2")
    val text = "first=1\nsecond=3\nunsaved=4"
    edtWriteAction {
      FileDocumentManager.getInstance().getDocument(fileFixture.get())!!.setText(text)
    }
    val nodes = build(change)

    assertEquals(listOf("second", "unsaved"), nodes.map { it.textPresentation })
    assertEquals(text.indexOf("unsaved"), (nodes.last().getNavigatable() as OpenFileDescriptor).offset)
  }

  @Test
  @RegistryKey(key = StructuralChangesViewSettings.REGISTRY_KEY, value = "true")
  fun `structural icons keep their logical size and full paint at device scales`(): Unit = timeoutRunBlocking {
    setSelected(true)
    val change = change(null, "element=1")
    for (width in listOf(16, 32)) {
      var referenceSize: Dimension? = null
      for (scale in listOf(1, 2)) {
        presentationIcon = createPresentationIcon(width, scale)
        val node = build(change).single()
        val size = withContext(Dispatchers.EDT) {
          val tree = JTree(DefaultTreeModel(node))
          val renderer = ChangesBrowserNodeRenderer(project, { false }, false)
          renderer.getTreeCellRendererComponent(tree, node, false, false, true, 0, false)
          val icon = checkNotNull(renderer.icon)
          val description = "$width px icon at ${scale}x"
          assertEquals(width, icon.iconWidth, description)
          assertEquals(16, icon.iconHeight, description)

          val size = renderer.preferredSize
          renderer.size = size
          val painted = BufferedImage(size.width * scale, size.height * scale, BufferedImage.TYPE_INT_ARGB)
          val graphics = painted.createGraphics()
          try {
            graphics.scale(scale.toDouble(), scale.toDouble())
            renderer.paint(graphics)
          }
          finally {
            graphics.dispose()
          }
          assertIconPaint(painted, width * scale, 16 * scale, description)
          size
        }
        if (referenceSize == null) referenceSize = size
        else assertEquals(referenceSize, size, "The device scale must not change the row size")
      }
    }
  }

  private fun createPresentationIcon(width: Int, scale: Int): Icon {
    val image = HiDPIImage(scale.toDouble(), width.toDouble(), 16.0, BufferedImage.TYPE_INT_ARGB, PaintUtil.RoundingMode.ROUND)
    val graphics = image.createGraphics()
    try {
      graphics.color = Color.MAGENTA
      graphics.fillRect(0, 0, width / 2, 16)
      graphics.color = Color.CYAN
      graphics.fillRect(width / 2, 0, width / 2, 16)
    }
    finally {
      graphics.dispose()
    }
    return IconUtil.createImageIcon(image)
  }

  private fun assertIconPaint(image: BufferedImage, width: Int, height: Int, description: String) {
    var bounds: Rectangle? = null
    var pixelCount = 0
    for (y in 0 until image.height) {
      for (x in 0 until image.width) {
        val color = image.getRGB(x, y)
        if (color == Color.MAGENTA.rgb || color == Color.CYAN.rgb) {
          val pixel = Rectangle(x, y, 1, 1)
          bounds = bounds?.union(pixel) ?: pixel
          pixelCount++
        }
      }
    }
    assertEquals(Dimension(width, height), bounds?.size, description)
    assertEquals(width * height, pixelCount, "The icon must paint every pixel: $description")
  }

  private fun assertToggleVisible(visible: Boolean) {
    val action = ActionManager.getInstance().getAction(OPTION_ID) as ToggleAction
    val event = TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(project))
    action.update(event)
    assertEquals(visible, event.presentation.isVisible)
    assertEquals(visible, event.presentation.isEnabled)
  }

  private suspend fun setSelected(selected: Boolean) {
    withContext(Dispatchers.EDT) {
      val action = ActionManager.getInstance().getAction(OPTION_ID) as ToggleAction
      val event = TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(project))
      action.setSelected(event, selected)
      assertEquals(selected, action.isSelected(event))
    }
  }

  private suspend fun change(before: String?, after: String): Change {
    val file = fileFixture.get()
    edtWriteAction { VfsUtil.saveText(file, after) }
    val path = VcsUtil.getFilePath(file)
    return Change(before?.let { revision(path, it) }, CurrentContentRevision(path))
  }

  private fun revision(path: FilePath, text: String?): ContentRevision = object : ContentRevision {
    override fun getFile(): FilePath = path
    override fun getContent(): String? = text
    override fun getRevisionNumber(): VcsRevisionNumber = VcsRevisionNumber.NULL
  }

  private suspend fun build(change: Change): List<ChangesBrowserStructuralNode<*>> {
    IndexingTestUtil.suspendUntilIndexesAreReady(project)
    smartReadAction(project) { }
    val builder = TreeModelBuilder(project, NoneChangesGroupingFactory)
    val fileNode = ChangesBrowserChangeNode(project, change, null)
    builder.myRoot.add(fileNode)
    val modifier = ChangesViewModifier.KEY.getExtensions(project).single { it.javaClass.simpleName == "StructuralChangesViewModifier" }
    modifier.modifyTreeModelBuilder(builder)
    assertEquals(1, fileNode.fileCount)
    return (0 until fileNode.childCount).map { fileNode.getChildAt(it) as ChangesBrowserStructuralNode<*> }
  }

  private class FileElement(private val file: PsiFile, private val icon: Icon?) : StructureViewTreeElement {
    override fun getValue(): PsiFile = file
    override fun getPresentation(): ItemPresentation = file.presentation!!
    override fun navigate(requestFocus: Boolean) {}
    override fun canNavigate(): Boolean = false
    override fun canNavigateToSource(): Boolean = false

    override fun getChildren(): Array<StructureViewTreeElement> {
      var offset = 0
      return file.text.lines().mapNotNull { text ->
        val element = if (text.isEmpty()) null else LineTreeElement(LineElement(file, text, offset, icon))
        offset += text.length + 1
        element
      }.toTypedArray()
    }
  }

  private class LineElement(
    private val file: PsiFile,
    private val line: String,
    private val offset: Int,
    private val icon: Icon?,
  ) : FakePsiElement() {
    override fun getParent(): PsiElement = file
    override fun getContainingFile(): PsiFile = file
    override fun getName(): String = line.substringBefore('=')
    override fun getText(): String = line
    override fun getTextOffset(): Int = offset
    override fun getTextRange(): TextRange = TextRange.from(offset, line.length)
    override fun getLocationString(): String? = null
    override fun getIcon(open: Boolean): Icon? = icon
  }

  private class LineTreeElement(private val element: LineElement) : StructureViewTreeElement {
    override fun getValue(): PsiElement = element
    override fun getPresentation(): ItemPresentation = element
    override fun getChildren(): Array<StructureViewTreeElement> = emptyArray()
    override fun navigate(requestFocus: Boolean) {}
    override fun canNavigate(): Boolean = false
    override fun canNavigateToSource(): Boolean = false
  }

  companion object {
    private const val OPTION_ID = "ChangesView.ShowStructuralChanges"
    private val projectFixture = projectFixture()
    private val moduleFixture = projectFixture.moduleFixture("structural-changes")
    private val sourceRootFixture = moduleFixture.sourceRootFixture()
  }
}
