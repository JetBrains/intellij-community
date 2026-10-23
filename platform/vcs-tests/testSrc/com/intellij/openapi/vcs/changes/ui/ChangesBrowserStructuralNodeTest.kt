// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.openapi.vcs.changes.LocalChangeListImpl
import com.intellij.openapi.vcs.changes.LocalChangesListView
import com.intellij.openapi.vcs.changes.StructuralChangesViewSettings
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.platform.ide.navigation.requestNavigate
import com.intellij.platform.vcs.VcsSharedDataKeys
import com.intellij.pom.Navigatable
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.replaceService
import com.intellij.ui.ClickListener
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.Processor
import com.intellij.util.ui.ThreeStateCheckBox
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mockito.mock
import java.awt.event.FocusEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
@RegistryKey(key = StructuralChangesViewSettings.REGISTRY_KEY, value = "true")
internal class ChangesBrowserStructuralNodeTest {
  private val project: Project get() = projectFixture.get()

  @BeforeEach
  fun enableStructuralChanges() {
    StructuralChangesViewSettings.setEnabled(project, true)
  }

  @AfterEach
  fun disableStructuralChanges() {
    StructuralChangesViewSettings.setEnabled(project, false)
  }

  @Test
  fun `test structural children preserve file counts and inclusion`(): Unit = timeoutRunBlocking {
    val fixture = createFixture()
    withContext(Dispatchers.UI) {
      fixture.view.setInclusionModel(DefaultInclusionModel().apply { setInclusion(listOf(fixture.change)) })
    }

    assertEquals(2, fixture.root.fileCount)
    assertEquals(1, fixture.fileNode.fileCount)
    assertEquals(0, fixture.first.fileCount)
    assertEquals(0, fixture.first.directoryCount)
    assertFalse(fixture.first.isMeaningfulNode)
    assertTrue(fixture.first.getUserData(ChangesBrowserNodeKeys.HIDE_INCLUSION_CHECKBOX) == true)

    val inclusion = withContext(Dispatchers.UI) {
      Triple(fixture.view.canInclude(fixture.first), fixture.view.canInclude(fixture.fileNode), fixture.view.status(fixture.fileNode))
    }
    assertFalse(inclusion.first)
    assertTrue(inclusion.second)
    assertEquals(ThreeStateCheckBox.State.SELECTED, inclusion.third)
  }

  @Test
  fun `test structural selection uses only its enclosing change`(): Unit = timeoutRunBlocking {
    val fixture = createFixture()
    val (selected, exact, actionSelection) = withContext(Dispatchers.UI) {
      fixture.view.selectionPaths = arrayOf(TreePath(fixture.first.path), TreePath(fixture.second.path))
      SelectionResult(
        fixture.view.selectedChanges.toList(),
        VcsTreeModelData.exactlySelected(fixture.view).iterateRawNodes().toList(),
        VcsTreeModelData.exactlySelectedForActions(fixture.view).iterateRawNodes().toList(),
      )
    }

    assertEquals(listOf(fixture.change), selected)
    assertEquals(listOf(fixture.first, fixture.second), exact)
    assertEquals(listOf(fixture.fileNode), actionSelection)
  }

  @Test
  fun `test structural selection supplies element navigation and file actions`(): Unit = timeoutRunBlocking {
    val fixture = createFixture()
    val snapshot = withContext(Dispatchers.UI) {
      fixture.view.selectionPath = TreePath(fixture.first.path)
      val snapshot = Snapshot()
      fixture.view.uiDataSnapshot(snapshot.sink)
      snapshot
    }

    assertEquals(listOf(fixture.change), snapshot[VcsSharedDataKeys.CHANGES]?.toList())
    assertEquals(listOf(fixture.change), snapshot[VcsSharedDataKeys.CHANGE_LEAD_SELECTION]?.toList())
    assertSame(fixture.first.getNavigatable(), snapshot[CommonDataKeys.NAVIGATABLE])
    assertEquals(listOf(fixture.first.getNavigatable()), snapshot[CommonDataKeys.NAVIGATABLE_ARRAY]?.toList())
  }

  @Test
  fun `test selecting a file excludes structural children from action data`(): Unit = timeoutRunBlocking {
    val fixture = createFixture()
    val selected = withContext(Dispatchers.UI) {
      fixture.view.selectionPath = TreePath(fixture.fileNode.path)
      VcsTreeModelData.selected(fixture.view).userObjects()
    }

    assertEquals(listOf(fixture.change), selected)
  }

  @Test
  fun `test structural nodes with children receive enter and do not toggle on double click`(): Unit = timeoutRunBlocking {
    val fixture = createFixture()
    val (enterHandled, enterConsumed, toggles) = withContext(Dispatchers.EDT) {
      fixture.view.model.insertNodeInto(StructuralNode("nested", fixture.first.getNavigatable()), fixture.first, 0)
      fixture.view.selectionPath = TreePath(fixture.first.path)
      var enterHandled = false
      fixture.view.enterKeyHandler = Processor {
        enterHandled = true
        true
      }
      val enter = KeyEvent(fixture.view, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ENTER, '\n')
      fixture.view.keyListeners.forEach { it.keyPressed(enter) }
      val doubleClick = MouseEvent(fixture.view, MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, 2, false, MouseEvent.BUTTON1)
      Triple(enterHandled, enter.isConsumed, EditSourceOnDoubleClickHandler.isToggleEvent(fixture.view, doubleClick))
    }

    assertTrue(enterHandled)
    assertTrue(enterConsumed)
    assertFalse(toggles)
  }

  @Test
  fun `test structural selection previews source without requesting focus`(): Unit = timeoutRunBlocking {
    val navigation = PreviewNavigatable()
    val fixture = createPreviewFixture(navigation)

    previewSelection(fixture) {
      view.selectionPath = TreePath(first.path)
    }

    assertEquals(listOf(false), navigation.focusRequests)
  }

  @Test
  fun `test preview highlight follows structural selection and clears for a file`(@TestDisposable disposable: Disposable): Unit =
    timeoutRunBlocking {
      val highlights = recordHighlights(disposable)
      val file = LightVirtualFile("highlight.txt", "first\nsecond\n")
      val fixture = createHighlightFixture(file)
      try {
        coroutineScope {
          val previewScope = this
          withContext(Dispatchers.EDT) {
            fixture.view.previewShowing = true
            fixture.view.previewFocus = true
            StructuralChangesPreview(fixture.view, previewScope).install()
            fixture.view.selectionPath = TreePath(fixture.first.path)
          }
          val first = highlights.receive()
          assertSame(file, first.file)
          assertEquals(TextRange(0, 5), first.range)

          withContext(Dispatchers.EDT) {
            fixture.view.selectionPath = TreePath(fixture.second.path)
          }
          first.disposed.await()
          val second = highlights.receive()
          assertEquals(TextRange(6, 12), second.range)

          withContext(Dispatchers.EDT) {
            fixture.view.selectionPath = TreePath(fixture.fileNode.path)
          }
          second.disposed.await()
        }
        assertTrue(highlights.tryReceive().isFailure)
      }
      finally {
        withContext(NonCancellable + Dispatchers.EDT) {
          FileEditorManager.getInstance(project).closeFile(file)
        }
      }
    }

  @Test
  fun `test preview highlight clears on escape and survives focus loss`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val highlights = recordHighlights(disposable)
    val file = LightVirtualFile("highlight-cleanup.txt", "first\nsecond\n")
    val fixture = createHighlightFixture(file)
    try {
      coroutineScope {
        val previewScope = this
        withContext(Dispatchers.EDT) {
          fixture.view.previewShowing = true
          fixture.view.previewFocus = true
          StructuralChangesPreview(fixture.view, previewScope).install()
          fixture.view.selectionPath = TreePath(fixture.first.path)
        }
        val first = highlights.receive()
        withContext(Dispatchers.EDT) {
          val escape = KeyEvent(fixture.view, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ESCAPE, '\u001b')
          fixture.view.keyListeners.forEach { it.keyPressed(escape) }
        }
        first.disposed.await()

        withContext(Dispatchers.EDT) {
          fixture.view.selectionPath = TreePath(fixture.second.path)
        }
        val second = highlights.receive()
        withContext(Dispatchers.EDT) {
          fixture.view.previewFocus = false
          val event = FocusEvent(fixture.view, FocusEvent.FOCUS_LOST)
          fixture.view.focusListeners.forEach { it.focusLost(event) }
        }
        // One round trip through the event thread runs the work that the focus loss scheduled.
        withContext(Dispatchers.EDT) { }
        assertFalse(second.disposed.isCompleted, "The dim follows the selection, not the focus")
        withContext(Dispatchers.EDT) {
          val escape = KeyEvent(fixture.view, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ESCAPE, '\u001b')
          fixture.view.keyListeners.forEach { it.keyPressed(escape) }
        }
        second.disposed.await()
      }
    }
    finally {
      withContext(NonCancellable + Dispatchers.EDT) {
        FileEditorManager.getInstance(project).closeFile(file)
      }
    }
  }

  @Test
  fun `test structural selection opens source at element offset`(): Unit = timeoutRunBlocking {
    val fixture = createFixture()
    val descriptor = fixture.second.getNavigatable() as OpenFileDescriptor
    val manager = FileEditorManager.getInstance(project)
    try {
      previewSelection(fixture) {
        view.selectionPath = TreePath(second.path)
      }

      val offset = withContext(Dispatchers.EDT) {
        (manager.getSelectedEditor(descriptor.file) as? TextEditor)?.editor?.caretModel?.offset
      }
      assertEquals(6, offset)
    }
    finally {
      withContext(NonCancellable + Dispatchers.EDT) {
        manager.closeFile(descriptor.file)
      }
    }
  }

  @Test
  fun `test structural selection reuses a preview tab when global preview is disabled`(): Unit = timeoutRunBlocking {
    val permanentFile = LightVirtualFile("permanent.txt", "permanent\n")
    val firstFile = LightVirtualFile("first.txt", "first\nsecond\n")
    val secondFile = LightVirtualFile("second.txt", "first\nsecond\n")
    val firstDescriptor = OpenFileDescriptor(project, firstFile, 0)
    val secondDescriptor = OpenFileDescriptor(project, secondFile, 6)
    val fixture = createPreviewFixture(firstDescriptor, secondDescriptor)

    withPreviewTabsDisabled { manager ->
      requestNavigate(project, OpenFileDescriptor(project, permanentFile, 0), coroutineScope = this).await()
      previewSelection(fixture) {
        view.selectionPath = TreePath(first.path)
      }
      val firstIsPreview = withContext(Dispatchers.EDT) { manager.getComposite(firstFile)!!.isPreview }
      assertTrue(firstIsPreview)

      previewSelection(fixture) {
        view.selectionPath = TreePath(second.path)
      }
      val (openFiles, previewFiles, offset) = withContext(Dispatchers.EDT) {
        Triple(manager.openFiles.toSet(), manager.openFiles.filter { manager.getComposite(it)!!.isPreview },
               (manager.getSelectedEditor(secondFile) as TextEditor).editor.caretModel.offset)
      }
      assertEquals(setOf(permanentFile, secondFile), openFiles)
      assertEquals(listOf(secondFile), previewFiles)
      assertEquals(6, offset)
      assertFalse(firstDescriptor.isUsePreviewTab)
      assertFalse(secondDescriptor.isUsePreviewTab)
    }
  }

  @Test
  fun `test changed and added files use preview tabs`(): Unit = timeoutRunBlocking {
    for (added in listOf(false, true)) {
      val file = LightVirtualFile("file-$added.txt", "first\nsecond\n")
      val fixture = createFilePreviewFixture(file, added)

      withPreviewTabsDisabled { manager ->
        previewSelection(fixture) {
          view.selectionPath = TreePath(fileNode.path)
        }

        val (selected, isPreview) = withContext(Dispatchers.EDT) {
          manager.selectedFiles.toList() to manager.getComposite(file)?.isPreview
        }
        assertEquals(listOf(file), selected)
        assertEquals(true, isPreview)
      }
    }
  }

  @Test
  fun `test unversioned file rows use preview tabs`(): Unit = timeoutRunBlocking {
    val fixture = createFixture()
    val pathFile = LightVirtualFile("unversioned-path.txt", "first\nsecond\n")
    val virtualFile = LightVirtualFile("unversioned-virtual.txt", "first\nsecond\n")
    val nodes = withContext(Dispatchers.EDT) {
      listOf(ChangesBrowserFilePathNode(filePath(pathFile), FileStatus.UNKNOWN), ChangesBrowserFileNode(project, virtualFile))
        .onEach { fixture.view.model.insertNodeInto(it, fixture.root, fixture.root.childCount) }
    }

    withPreviewTabsDisabled { manager ->
      for ((node, file) in nodes.zip(listOf(pathFile, virtualFile))) {
        previewSelection(fixture) {
          view.selectionPath = TreePath(node.path)
        }

        val (openFiles, isPreview) = withContext(Dispatchers.EDT) {
          manager.openFiles.toList() to manager.getComposite(file)?.isPreview
        }
        assertEquals(listOf(file), openFiles)
        assertEquals(true, isPreview)
      }
    }
  }

  @Test
  fun `test switching files and members preserves caret and permanent tabs`(): Unit = timeoutRunBlocking {
    val permanentFile = LightVirtualFile("permanent.txt", "first\nsecond\n")
    val firstFile = LightVirtualFile("first.txt", "first\nsecond\n")
    val secondFile = LightVirtualFile("second.txt", "first\nsecond\n")
    val fixture = createFilePreviewFixture(firstFile)
    val (secondFileNode, permanentFileNode) = withContext(Dispatchers.EDT) {
      val second = ChangesBrowserFilePathNode(filePath(secondFile), FileStatus.UNKNOWN)
      val permanent = ChangesBrowserFilePathNode(filePath(permanentFile), FileStatus.UNKNOWN)
      fixture.view.model.insertNodeInto(second, fixture.root, fixture.root.childCount)
      fixture.view.model.insertNodeInto(permanent, fixture.root, fixture.root.childCount)
      second to permanent
    }

    withPreviewTabsDisabled { manager ->
      requestNavigate(project, OpenFileDescriptor(project, permanentFile, 2), coroutineScope = this).await()
      for (node in listOf(fixture.fileNode, fixture.second, fixture.fileNode)) {
        previewSelection(fixture) {
          view.selectionPath = TreePath(node.path)
        }
      }
      val fileOffset = withContext(Dispatchers.EDT) {
        (manager.getSelectedEditor(firstFile) as TextEditor).editor.caretModel.offset
      }
      assertEquals(6, fileOffset)

      previewSelection(fixture) { view.selectionPath = TreePath(secondFileNode.path) }
      assertEquals(setOf(permanentFile, secondFile), withContext(Dispatchers.EDT) { manager.openFiles.toSet() })
      previewSelection(fixture) { view.selectionPath = TreePath(second.path) }
      previewSelection(fixture) { view.selectionPath = TreePath(permanentFileNode.path) }

      val (openFiles, previewFiles, permanentOffset) = withContext(Dispatchers.EDT) {
        Triple(manager.openFiles.toSet(), manager.openFiles.filter { manager.getComposite(it)!!.isPreview },
               (manager.getSelectedEditor(permanentFile) as TextEditor).editor.caretModel.offset)
      }
      assertEquals(setOf(permanentFile, firstFile), openFiles)
      assertEquals(listOf(firstFile), previewFiles)
      assertEquals(2, permanentOffset)
    }
  }

  @Test
  fun `test clicking a selected file restores its preview`(): Unit = timeoutRunBlocking {
    val file = LightVirtualFile("selected.txt", "first\nsecond\n")
    val fixture = createFilePreviewFixture(file)

    withPreviewTabsDisabled { manager ->
      previewSelection(fixture) { view.selectionPath = TreePath(fileNode.path) }
      withContext(Dispatchers.EDT) { manager.closeFile(file) }
      previewSelection(fixture) { clickSelectedElement(view) }

      assertEquals(listOf(file), withContext(Dispatchers.EDT) { manager.selectedFiles.toList() })
      assertTrue(withContext(Dispatchers.EDT) { manager.getComposite(file)!!.isPreview })
    }
  }

  @Test
  fun `test disabled structural option prevents file and member previews`(): Unit = timeoutRunBlocking {
    StructuralChangesViewSettings.setEnabled(project, false)
    checkDisabledPreviews()
  }

  @Test
  @RegistryKey(key = StructuralChangesViewSettings.REGISTRY_KEY, value = "false")
  fun `test disabled registry prevents file and member previews`(): Unit = timeoutRunBlocking {
    checkDisabledPreviews()
  }

  private suspend fun checkDisabledPreviews() {
    val file = LightVirtualFile("disabled.txt", "first\nsecond\n")
    val fixture = createFilePreviewFixture(file)

    withPreviewTabsDisabled { manager ->
      for (node in listOf(fixture.fileNode, fixture.second)) {
        previewSelection(fixture) { view.selectionPath = TreePath(node.path) }
        assertTrue(withContext(Dispatchers.EDT) { manager.openFiles.isEmpty() })
      }
    }
  }

  @Test
  fun `test disabling structural option cancels delayed file and member previews`(): Unit = timeoutRunBlocking {
    val file = LightVirtualFile("disabled-during-delay.txt", "first\nsecond\n")
    val fixture = createFilePreviewFixture(file)

    withPreviewTabsDisabled { manager ->
      for (node in listOf(fixture.fileNode, fixture.second)) {
        StructuralChangesViewSettings.setEnabled(project, true)
        previewSelection(fixture) {
          view.selectionPath = TreePath(node.path)
          StructuralChangesViewSettings.setEnabled(project, false)
        }
        assertTrue(withContext(Dispatchers.EDT) { manager.openFiles.isEmpty() })
      }
    }
  }

  @Test
  fun `test disabling structural option during request preparation prevents preview`(): Unit = timeoutRunBlocking {
    val file = LightVirtualFile("disabled-during-request.txt", "first\nsecond\n")
    val request = readAction { NavigationRequest.sourceNavigationRequest(project, file, 6) }
    val navigation = PendingSourceNavigatable()
    val fixture = createPreviewFixture(navigation)

    withPreviewTabsDisabled { manager ->
      previewSelection(fixture) {
        view.selectionPath = TreePath(first.path)
        navigation.started.await()
        StructuralChangesViewSettings.setEnabled(project, false)
        navigation.request.complete(request)
      }
      assertTrue(withContext(Dispatchers.EDT) { manager.openFiles.isEmpty() })
    }
  }

  @Test
  fun `test directories deleted files and unavailable revisions do not preview`(): Unit = timeoutRunBlocking {
    val fixture = createFixture()
    val deletedFile = LightVirtualFile("deleted.txt", "first\nsecond\n")
    val directory = object : LightVirtualFile("directory") {
      override fun isDirectory(): Boolean = true
    }
    val invalidFile = object : LightVirtualFile("invalid.txt") {
      override fun isValid(): Boolean = false
    }
    val nodes = withContext(Dispatchers.EDT) {
      listOf(
        ChangesBrowserChangeNode(project, Change(revision(filePath(deletedFile)), null), null),
        ChangesBrowserFilePathNode(filePath(directory)),
        ChangesBrowserFileNode(project, directory),
        ChangesBrowserChangeNode(project, Change(null, CurrentContentRevision(filePath(directory))), null),
        ChangesBrowserFileNode(project, invalidFile),
        ChangesBrowserFilePathNode(LocalFilePath("/ChangesViewTest/unavailable.txt", false)),
        ChangesBrowserChangeNode(project, Change(null, CurrentContentRevision(LocalFilePath("/ChangesViewTest/unavailable.txt", false))), null),
      ).onEach { fixture.view.model.insertNodeInto(it, fixture.root, fixture.root.childCount) } + fixture.fileNode
    }

    withPreviewTabsDisabled { manager ->
      for (node in nodes) {
        previewSelection(fixture) { view.selectionPath = TreePath(node.path) }
        assertTrue(withContext(Dispatchers.EDT) { manager.openFiles.isEmpty() }, node.textPresentation)
      }
    }
  }

  @Test
  fun `test structural selection preserves an existing permanent tab`(): Unit = timeoutRunBlocking {
    val permanentFile = LightVirtualFile("permanent.txt", "first\nsecond\n")
    val previewFile = LightVirtualFile("preview.txt", "first\nsecond\n")
    val permanentDescriptor = OpenFileDescriptor(project, permanentFile, 6)
    val fixture = createPreviewFixture(permanentDescriptor, OpenFileDescriptor(project, previewFile, 0))

    withPreviewTabsDisabled { manager ->
      requestNavigate(project, permanentDescriptor, coroutineScope = this).await()
      previewSelection(fixture) {
        view.selectionPath = TreePath(first.path)
      }
      val permanentIsPreview = withContext(Dispatchers.EDT) { manager.getComposite(permanentFile)!!.isPreview }
      assertFalse(permanentIsPreview)

      previewSelection(fixture) {
        view.selectionPath = TreePath(second.path)
      }
      val (openFiles, remainsPermanent, previewIsPreview) = withContext(Dispatchers.EDT) {
        Triple(manager.openFiles.toSet(), !manager.getComposite(permanentFile)!!.isPreview, manager.getComposite(previewFile)!!.isPreview)
      }
      assertEquals(setOf(permanentFile, previewFile), openFiles)
      assertTrue(remainsPermanent)
      assertTrue(previewIsPreview)
    }
  }

  @Test
  fun `test explicit navigation without focus makes the preview tab permanent`(): Unit = timeoutRunBlocking {
    checkExplicitNavigation(requestFocus = false)
  }

  @Test
  fun `test explicit navigation with focus makes the preview tab permanent`(): Unit = timeoutRunBlocking {
    checkExplicitNavigation(requestFocus = true)
  }

  private suspend fun checkExplicitNavigation(requestFocus: Boolean): Unit = coroutineScope {
    val explicitFile = LightVirtualFile("explicit.txt", "first\nsecond\n")
    val previewFile = LightVirtualFile("preview.txt", "first\nsecond\n")
    val descriptor = OpenFileDescriptor(project, explicitFile, 6)
    val fixture = createPreviewFixture(descriptor, OpenFileDescriptor(project, previewFile, 0))

    withPreviewTabsDisabled { manager ->
      previewSelection(fixture) {
        view.selectionPath = TreePath(first.path)
      }
      val initiallyPreview = withContext(Dispatchers.EDT) { manager.getComposite(explicitFile)!!.isPreview }
      assertTrue(initiallyPreview)

      val options = NavigationOptions.defaultOptions().requestFocus(requestFocus)
      requestNavigate(project, descriptor, options, coroutineScope = this).await()
      val afterExplicitNavigation = withContext(Dispatchers.EDT) { manager.getComposite(explicitFile)!!.isPreview }
      assertFalse(afterExplicitNavigation)

      previewSelection(fixture) {
        view.selectionPath = TreePath(second.path)
      }
      val openFiles = withContext(Dispatchers.EDT) { manager.openFiles.toSet() }
      assertEquals(setOf(explicitFile, previewFile), openFiles)
      assertFalse(descriptor.isUsePreviewTab)
    }
  }

  @Test
  fun `test structural selection respects files that prohibit preview`(): Unit = timeoutRunBlocking {
    val fixture = createFixture()
    val descriptor = fixture.first.getNavigatable() as OpenFileDescriptor
    descriptor.file.putUserData(FileEditorManagerKeys.FORBID_PREVIEW_TAB, true)

    withPreviewTabsDisabled { manager ->
      previewSelection(fixture) {
        view.selectionPath = TreePath(first.path)
      }

      val isPreview = withContext(Dispatchers.EDT) { manager.getComposite(descriptor.file)!!.isPreview }
      assertFalse(isPreview)
    }
  }

  @Test
  fun `test clicking selected structural element restores source offset`(): Unit = timeoutRunBlocking {
    val fixture = createFixture()
    val descriptor = fixture.second.getNavigatable() as OpenFileDescriptor
    val manager = FileEditorManager.getInstance(project)
    try {
      previewSelection(fixture) {
        view.selectionPath = TreePath(second.path)
      }
      withContext(Dispatchers.EDT) {
        (manager.getSelectedEditor(descriptor.file) as TextEditor).editor.caretModel.moveToOffset(0)
      }

      previewSelection(fixture) {
        clickSelectedElement(view)
      }

      val offset = withContext(Dispatchers.EDT) {
        (manager.getSelectedEditor(descriptor.file) as TextEditor).editor.caretModel.offset
      }
      assertEquals(6, offset)
    }
    finally {
      withContext(NonCancellable + Dispatchers.EDT) {
        manager.closeFile(descriptor.file)
      }
    }
  }

  @Test
  fun `test secondary and double clicks do not preview source`(): Unit = timeoutRunBlocking {
    val navigation = PreviewNavigatable()
    val fixture = createPreviewFixture(navigation)
    withContext(Dispatchers.UI) {
      fixture.view.selectionPath = TreePath(fixture.first.path)
    }

    for ((button, count) in listOf(MouseEvent.BUTTON3 to 1, MouseEvent.BUTTON1 to 2)) {
      previewSelection(fixture) {
        clickSelectedElement(view, button = button, clickCount = count)
      }
    }

    assertTrue(navigation.focusRequests.isEmpty())
  }

  @Test
  fun `test expand controls and multiple selections do not preview source`(): Unit = timeoutRunBlocking {
    val navigation = PreviewNavigatable()
    val fixture = createPreviewFixture(navigation)
    withContext(Dispatchers.EDT) {
      fixture.view.model.insertNodeInto(StructuralNode("nested", navigation), fixture.first, 0)
      fixture.view.selectionPath = TreePath(fixture.first.path)
    }

    previewSelection(fixture) {
      val path = view.selectionPath
      val bounds = view.getPathBounds(path)!!
      val y = bounds.y + bounds.height / 2
      val x = (0 until bounds.x).first { TreeUtil.isLocationInExpandControl(view, path, it, y) }
      clickSelectedElement(view, x = x)
    }
    previewSelection(fixture) {
      view.addSelectionPath(TreePath(second.path))
      clickSelectedElement(view)
    }

    assertTrue(navigation.focusRequests.isEmpty())
  }

  @Test
  fun `test rapid structural selection previews only the last element`(): Unit = timeoutRunBlocking {
    val firstNavigation = PreviewNavigatable()
    val secondNavigation = PreviewNavigatable()
    val fixture = createPreviewFixture(firstNavigation, secondNavigation)

    previewSelection(fixture) {
      view.selectionPath = TreePath(first.path)
      view.selectionPath = TreePath(second.path)
    }

    assertTrue(firstNavigation.focusRequests.isEmpty())
    assertEquals(listOf(false), secondNavigation.focusRequests)
  }

  @Test
  fun `test selecting a file cancels structural preview`(): Unit = timeoutRunBlocking {
    val navigation = PreviewNavigatable()
    val fixture = createPreviewFixture(navigation)

    previewSelection(fixture) {
      view.selectionPath = TreePath(first.path)
      view.selectionPath = TreePath(fileNode.path)
    }

    assertTrue(navigation.focusRequests.isEmpty())
  }

  @Test
  fun `test canceling pending preview preserves immediate explicit navigation`(): Unit = timeoutRunBlocking {
    for (requestFocus in listOf(false, true)) {
      val explicitNavigation = PreviewNavigatable()
      val previewNavigation = PreviewNavigatable()
      val fixture = withContext(Dispatchers.EDT) {
        createFixture(StructuralNode("first", explicitNavigation, previewNavigation), StructuralNode("second", PreviewNavigatable()))
      }

      coroutineScope {
        val navigationScope = this
        withContext(Dispatchers.EDT) {
          fixture.view.previewShowing = true
          fixture.view.previewFocus = true
          val preview = StructuralChangesPreview(fixture.view, navigationScope)
          preview.install()
          fixture.view.selectionPath = TreePath(fixture.first.path)
          preview.cancelPendingPreview()
          val options = NavigationOptions.defaultOptions().requestFocus(requestFocus)
          requestNavigate(project, explicitNavigation, options, coroutineScope = navigationScope).await()
        }
      }

      assertTrue(previewNavigation.focusRequests.isEmpty())
      assertEquals(listOf(requestFocus), explicitNavigation.focusRequests)
    }
  }

  @Test
  fun `test multiple structural selections cancel preview`(): Unit = timeoutRunBlocking {
    val navigation = PreviewNavigatable()
    val fixture = createPreviewFixture(navigation)

    previewSelection(fixture) {
      view.selectionPath = TreePath(first.path)
      view.addSelectionPath(TreePath(second.path))
    }

    assertTrue(navigation.focusRequests.isEmpty())
  }

  @Test
  fun `test model refresh after a finished preview keeps a closed file closed`(): Unit = timeoutRunBlocking {
    val file = LightVirtualFile("closed.txt", "first\nsecond\n")
    val fixture = createFilePreviewFixture(file)

    withPreviewTabsDisabled { manager ->
      coroutineScope {
        val previewScope = this
        withContext(Dispatchers.EDT) {
          fixture.view.previewShowing = true
          fixture.view.previewFocus = true
          StructuralChangesPreview(fixture.view, previewScope).install()
          fixture.view.selectionPath = TreePath(fixture.fileNode.path)
        }
        awaitPreviews(previewScope)
        withContext(Dispatchers.EDT) {
          assertEquals(listOf(file), manager.selectedFiles.toList())
          manager.closeFile(file)
          fixture.view.updateTreeModel(DefaultTreeModel(fixture.root), ChangesTree.ALWAYS_KEEP)
        }
        // The model listener restarts a preview in a later event.
        delay(100.milliseconds)
        awaitPreviews(previewScope)

        assertTrue(withContext(Dispatchers.EDT) { manager.selectedFiles.isEmpty() })
      }
    }
  }

  @Test
  fun `test model refresh does not preview restored structural selection`(): Unit = timeoutRunBlocking {
    val navigation = PreviewNavigatable()
    val fixture = createPreviewFixture(navigation)

    previewSelection(fixture) {
      view.selectionPath = TreePath(first.path)
      view.updateTreeModel(DefaultTreeModel(root), ChangesTree.ALWAYS_KEEP)
    }

    assertTrue(navigation.focusRequests.isEmpty())
    val selected = withContext(Dispatchers.UI) { fixture.view.selectionPath?.lastPathComponent }
    assertSame(fixture.first, selected)
  }

  @Test
  fun `test unavailable structural source is not previewed`(): Unit = timeoutRunBlocking {
    val navigation = PreviewNavigatable(canNavigateToSource = false)
    val fixture = createPreviewFixture(navigation)

    previewSelection(fixture) {
      view.selectionPath = TreePath(first.path)
    }

    assertTrue(navigation.focusRequests.isEmpty())
  }

  @Test
  fun `test losing tree focus cancels structural preview`(): Unit = timeoutRunBlocking {
    val navigation = PreviewNavigatable()
    val fixture = createPreviewFixture(navigation)

    previewSelection(fixture) {
      view.selectionPath = TreePath(first.path)
      view.previewFocus = false
    }

    assertTrue(navigation.focusRequests.isEmpty())
  }

  @Test
  fun `test losing tree focus cancels an active source request`(): Unit = timeoutRunBlocking {
    val original = LightVirtualFile("original.txt", "original\n")
    val target = LightVirtualFile("target.txt", "first\nsecond\n")
    val request = readAction { NavigationRequest.sourceNavigationRequest(project, target, 6) }
    val navigation = PendingSourceNavigatable()
    val fixture = createPreviewFixture(navigation)
    val manager = FileEditorManager.getInstance(project)
    try {
      requestNavigate(project, OpenFileDescriptor(project, original, 2), coroutineScope = this).await()

      previewSelection(fixture) {
        view.selectionPath = TreePath(first.path)
        navigation.started.await()
        view.previewFocus = false
        val event = FocusEvent(view, FocusEvent.FOCUS_LOST)
        view.focusListeners.forEach { it.focusLost(event) }
        navigation.request.complete(request)
      }

      val (selectedFiles, offset) = withContext(Dispatchers.EDT) {
        manager.selectedFiles.toList() to (manager.getSelectedEditor(original) as TextEditor).editor.caretModel.offset
      }
      assertEquals(listOf(original), selectedFiles)
      assertEquals(2, offset)
      assertFalse(manager.isFileOpen(target))
    }
    finally {
      withContext(NonCancellable + Dispatchers.EDT) {
        manager.closeFile(original)
        manager.closeFile(target)
      }
    }
  }

  @Test
  fun `test structural selection previews after tree gains focus`(): Unit = timeoutRunBlocking {
    val navigation = PreviewNavigatable()
    val fixture = createPreviewFixture(navigation)

    previewSelection(fixture) {
      view.previewFocus = false
      view.selectionPath = TreePath(first.path)
      view.previewFocus = true
    }

    assertEquals(listOf(false), navigation.focusRequests)
  }

  private suspend fun createPreviewFixture(first: Navigatable, second: Navigatable = PreviewNavigatable()): Fixture =
    withContext(Dispatchers.EDT) { createFixture(StructuralNode("first", first), StructuralNode("second", second)) }

  private suspend fun createHighlightFixture(file: VirtualFile): Fixture = withContext(Dispatchers.EDT) {
    createFixture(
      StructuralNode("first", OpenFileDescriptor(project, file, 0), highlightRange = TextRange(0, 5)),
      StructuralNode("second", OpenFileDescriptor(project, file, 6), highlightRange = TextRange(6, 12)),
    )
  }

  private fun recordHighlights(disposable: Disposable): Channel<HighlightRequest> {
    val requests = Channel<HighlightRequest>(Channel.UNLIMITED)
    project.replaceService(StructuralChangesPreviewHighlighter::class.java, object : StructuralChangesPreviewHighlighter {
      override fun highlight(file: VirtualFile, range: TextRange, modificationStamp: Long): Disposable {
        val request = HighlightRequest(file, range)
        requests.trySend(request).getOrThrow()
        return Disposable { request.disposed.complete(Unit) }
      }
    }, disposable)
    return requests
  }

  private suspend fun createFilePreviewFixture(file: VirtualFile, added: Boolean = false): Fixture = withContext(Dispatchers.EDT) {
    val path = filePath(file)
    createFixture(StructuralNode("first", OpenFileDescriptor(project, file, 0)),
                  StructuralNode("second", OpenFileDescriptor(project, file, 6)),
                  Change(if (added) null else revision(path), CurrentContentRevision(path)))
  }

  private suspend fun withPreviewTabsDisabled(test: suspend (FileEditorManagerImpl) -> Unit) {
    val manager = fileEditorManagerFixture.get()
    val settings = UISettings.getInstance()
    val wasEnabled = withContext(Dispatchers.UI) {
      settings.openInPreviewTabIfPossible.also { settings.openInPreviewTabIfPossible = false }
    }
    try {
      test(manager)
      assertFalse(withContext(Dispatchers.UI) { settings.openInPreviewTabIfPossible })
    }
    finally {
      withContext(NonCancellable + Dispatchers.EDT) {
        manager.closeAllFiles()
        settings.openInPreviewTabIfPossible = wasEnabled
      }
    }
  }

  private suspend fun previewSelection(fixture: Fixture, select: suspend Fixture.() -> Unit) {
    coroutineScope {
      val previewScope = this
      withContext(Dispatchers.EDT) {
        fixture.view.previewShowing = true
        fixture.view.previewFocus = true
        StructuralChangesPreview(fixture.view, previewScope).install()
        fixture.select()
      }
    }
  }

  /** Waits until the previews that the scope launched end. Call it from the scope itself, outside of its child coroutines. */
  private suspend fun awaitPreviews(scope: CoroutineScope) {
    while (scope.coroutineContext.job.children.any()) delay(10.milliseconds)
  }

  private fun clickSelectedElement(view: TestChangesView, button: Int = MouseEvent.BUTTON1, clickCount: Int = 1, x: Int? = null) {
    val bounds = view.getPathBounds(view.selectionPath)!!
    val event = ClickListener.SyntheticClickEvent(
      view, 0, 0, x ?: (bounds.x + 2), bounds.y + bounds.height / 2, clickCount, false, button,
    )
    view.mouseListeners.forEach { it.mouseClicked(event) }
  }

  private suspend fun createFixture(): Fixture {
    val (first, second) = readAction {
      val file = LightVirtualFile("changed.txt", "first\nsecond\n")
      StructuralNode("first", OpenFileDescriptor(project, file, 0)) to
        StructuralNode("second", OpenFileDescriptor(project, file, 6))
    }
    return withContext(Dispatchers.EDT) { createFixture(first, second) }
  }

  private fun createFixture(
    first: StructuralNode,
    second: StructuralNode,
    change: Change = Change(null, revision("changed.txt")),
  ): Fixture {
    val another = Change(null, revision("another.txt"))
    val list = LocalChangeListImpl.Builder(project, "Default")
      .setDefault(true)
      .setChanges(listOf(change, another))
      .build()
    val listNode = ChangesBrowserChangeListNode(project, list, ChangeListRemoteState()).apply { markAsHelperNode() }
    val root = ChangesBrowserNode.createRoot().apply { add(listNode) }
    val fileNode = ChangesBrowserChangeNode(project, change, null)
    listNode.add(fileNode)
    listNode.add(ChangesBrowserChangeNode(project, another, null))
    fileNode.add(first)
    fileNode.add(second)
    val view = TestChangesView(project)
    view.updateTreeModel(DefaultTreeModel(root), ChangesTree.ALWAYS_RESET)
    return Fixture(view, root, change, fileNode, first, second)
  }

  private fun filePath(file: VirtualFile): FilePath = object : LocalFilePath(file.path, file.isDirectory) {
    override fun getVirtualFile(): VirtualFile = file
  }

  private fun revision(name: String): ContentRevision = revision(LocalFilePath("/ChangesViewTest/$name", false))

  private fun revision(path: FilePath): ContentRevision = object : ContentRevision {
    override fun getContent(): String = ""
    override fun getFile(): FilePath = path
    override fun getRevisionNumber(): VcsRevisionNumber = VcsRevisionNumber.NULL
  }

  private class TestChangesView(project: Project) : LocalChangesListView(project) {
    var previewShowing = false
    var previewFocus = false

    override fun isShowing(): Boolean = previewShowing || super.isShowing()
    override fun hasFocus(): Boolean = previewFocus || super.hasFocus()
    fun canInclude(node: ChangesBrowserNode<*>): Boolean = isIncludable(node)
    fun status(node: ChangesBrowserNode<*>): ThreeStateCheckBox.State = getNodeStatus(node)
  }

  private class PreviewNavigatable(private val canNavigateToSource: Boolean = true) : Navigatable {
    val focusRequests: MutableList<Boolean> = ArrayList()

    override fun navigate(requestFocus: Boolean) {
      focusRequests.add(requestFocus)
    }

    override fun canNavigate(): Boolean = canNavigateToSource
    override fun canNavigateToSource(): Boolean = canNavigateToSource
  }

  private class PendingSourceNavigatable : Navigatable {
    val started = CompletableDeferred<Unit>()
    val request = CompletableDeferred<NavigationRequest?>()

    override fun canNavigateToSource(): Boolean = true

    override fun navigationRequest(): NavigationRequest? = runBlockingCancellable {
      started.complete(Unit)
      request.await()
    }
  }

  private class StructuralNode(
    private val name: String,
    private val navigatable: Navigatable,
    preview: Navigatable? = null,
    private val highlightRange: TextRange? = null,
  ) : ChangesBrowserStructuralNode<String>(name) {
    private val previewNavigatable = preview ?: navigatable

    override fun getTextPresentation(): String = name
    override fun getNavigatable(): Navigatable = navigatable
    override fun getPreviewHighlightRange(): TextRange? = highlightRange
    override fun getPreviewFile(): VirtualFile? = (previewNavigatable as? OpenFileDescriptor)?.file
    override fun getPreviewNavigationRequest(): NavigationRequest? {
      val target = previewNavigatable.takeIf { it.canNavigateToSource() } ?: return null
      return if (target is OpenFileDescriptor) NavigationRequest.sourceNavigationRequest(target.project, target.file, target.offset)
      else target.navigationRequest()
    }
  }

  private class Snapshot {
    private val values = HashMap<DataKey<*>, Any?>()
    private val lazyValues = HashMap<DataKey<*>, () -> Any?>()

    val sink: DataSink = mock(DataSink::class.java) { invocation ->
      when (invocation.method.name) {
        "set" -> {
          values[invocation.getArgument(0)] = invocation.getArgument(1)
          null
        }
        "lazy" -> {
          lazyValues[invocation.getArgument(0)] = invocation.getArgument(1)
          null
        }
        else -> Answers.RETURNS_DEFAULTS.answer(invocation)
      }
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(key: DataKey<T>): T? = (lazyValues[key]?.invoke() ?: values[key]) as T?
  }

  private data class Fixture(
    val view: TestChangesView,
    val root: ChangesBrowserNode<*>,
    val change: Change,
    val fileNode: ChangesBrowserChangeNode,
    val first: StructuralNode,
    val second: StructuralNode,
  )

  private data class SelectionResult(
    val selected: List<Change>,
    val exact: List<ChangesBrowserNode<*>>,
    val actionSelection: List<ChangesBrowserNode<*>>,
  )

  private data class HighlightRequest(
    val file: VirtualFile,
    val range: TextRange,
    val disposed: CompletableDeferred<Unit> = CompletableDeferred(),
  )

  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val fileEditorManagerFixture = projectFixture.fileEditorManagerFixture()
  }
}
