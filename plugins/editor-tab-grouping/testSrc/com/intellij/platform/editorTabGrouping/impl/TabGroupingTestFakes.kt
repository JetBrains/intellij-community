// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.editorTabGrouping.impl

import com.intellij.mock.MockProjectEx
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.OrderEntry
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.impl.RootDescriptor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.tabs.JBTabsBorder
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.impl.TabListOptions
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Graphics
import java.awt.Insets
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.jps.model.module.JpsModuleSourceRootType

/**
 * A [ProjectFileIndex] fake that returns one content root.
 *
 * It counts every [getContentRootForFile] call, so a test can prove that the tab component
 * memoizes the group of a file.
 */
internal class StubProjectFileIndex(var contentRootForFile: VirtualFile? = null) : ProjectFileIndex {
  var edtLookupCount: Int = 0
    private set

  var contentRootCallCount: Int = 0
    private set

  fun resetCounters() {
    contentRootCallCount = 0
  }

  override fun getContentRootForFile(file: VirtualFile): VirtualFile? {
    contentRootCallCount++
    if (javax.swing.SwingUtilities.isEventDispatchThread()) edtLookupCount++
    return contentRootForFile
  }

  override fun getContentRootForFile(file: VirtualFile, honorExclusion: Boolean): VirtualFile? {
    contentRootCallCount++
    if (javax.swing.SwingUtilities.isEventDispatchThread()) edtLookupCount++
    return contentRootForFile
  }

  override fun getModuleForFile(file: VirtualFile): Module? = null
  override fun getModuleForFile(file: VirtualFile, honorExclusion: Boolean): Module? = null
  override fun isInProject(file: VirtualFile): Boolean = contentRootForFile != null
  override fun isInProjectOrExcluded(file: VirtualFile): Boolean = contentRootForFile != null
  override fun getModulesForFile(file: VirtualFile, honorExclusion: Boolean): Set<Module> = emptySet()
  override fun getOrderEntriesForFile(file: VirtualFile): List<OrderEntry> = emptyList()
  override fun getClassRootForFile(file: VirtualFile): VirtualFile? = null
  override fun getSourceRootForFile(file: VirtualFile): VirtualFile? = null
  override fun isInContent(fileOrDir: VirtualFile): Boolean = contentRootForFile != null
  override fun isInSourceContent(fileOrDir: VirtualFile): Boolean = false
  override fun isInTestSourceContent(fileOrDir: VirtualFile): Boolean = false
  override fun isUnderSourceRootOfType(fileOrDir: VirtualFile, rootTypes: Set<JpsModuleSourceRootType<*>>): Boolean = false
  override fun isInLibraryClasses(fileOrDir: VirtualFile): Boolean = false
  override fun isInLibrary(fileOrDir: VirtualFile): Boolean = false
  override fun isInLibrarySource(fileOrDir: VirtualFile): Boolean = false
  override fun isExcluded(file: VirtualFile): Boolean = false
  override fun findContainingLibraries(fileOrDir: VirtualFile): Collection<LibraryEntity> = emptyList()
  override fun findContainingSdks(fileOrDir: VirtualFile): Collection<SdkEntity> = emptyList()
  override fun isUnderIgnored(file: VirtualFile): Boolean = false
  override fun getContainingSourceRootType(file: VirtualFile): JpsModuleSourceRootType<*>? = null
  override fun isInGeneratedSources(file: VirtualFile): Boolean = false
  override fun getUnloadedModuleNameForFile(fileOrDir: VirtualFile): String? = null
  override fun getWorkspaceContentFileSetRoot(fileOrDir: VirtualFile): VirtualFile? = null
  override fun getModuleSourceOrLibraryClassesRoot(file: VirtualFile): VirtualFile? = null
  override fun getModuleSourceOrLibraryClassesRoots(file: VirtualFile): Collection<RootDescriptor> = emptyList()
  override fun iterateContent(processor: ContentIterator): Boolean = true
  override fun iterateContent(processor: ContentIterator, filter: VirtualFileFilter?): Boolean = true
  override fun iterateContentUnderDirectory(dir: VirtualFile, processor: ContentIterator): Boolean = true
  override fun iterateContentUnderDirectory(dir: VirtualFile, processor: ContentIterator, customFilter: VirtualFileFilter?): Boolean = true

  @Suppress("OVERRIDE_DEPRECATION")
  override fun getPackageNameByDirectory(dir: VirtualFile): String? = null

  // The interface still declares this method, so the fake must implement it.
  @Suppress("MarkedForRemoval", "OVERRIDE_DEPRECATION", "removal")
  override fun isLibraryClassFile(file: VirtualFile): Boolean = false
  override fun isInSource(fileOrDir: VirtualFile): Boolean = false
}

/**
 * Creates an [EditorWindow] of a new [FileEditorManagerImpl] for the [project].
 */
internal fun createTestEditorWindow(project: Project, parentDisposable: Disposable): EditorWindow {
  @Suppress("SSBasedInspection")
  val coroutineScope = CoroutineScope(SupervisorJob())
  Disposer.register(parentDisposable) { coroutineScope.cancel() }
  val manager = FileEditorManagerImpl(project, coroutineScope)
  // The constructor registers a listener under the manager itself. Without a parent the manager
  // stays under the root disposable and the leak check fails after the test class.
  Disposer.register(parentDisposable, manager)
  return EditorWindow(manager, coroutineScope)
}

/** Resolves the groups at once, or after the [gate] completes. */
internal class TestGroupResolver(private val project: MockProjectEx) : DirectoryGroupResolver(project) {
  var gate: CompletableDeferred<Unit>? = null

  override suspend fun resolve(files: List<VirtualFile>): Map<VirtualFile, TabGroup?> {
    gate?.await()
    return files.associateWith { EditorTabGroupingProvider.resolveDirectoryGroup(it, project) }
  }
}

/**
 * A [GroupingJBEditorTabs] subclass that opens the internal state to a test.
 *
 * It counts the work that the performance fixes remove from a hot path.
 */
internal class TestGroupingJBEditorTabs private constructor(
  project: MockProjectEx,
  parentDisposable: Disposable,
  coroutineScope: CoroutineScope,
  tabListOptions: TabListOptions,
  isIslandsTheme: Boolean,
  private val testResolver: TestGroupResolver,
) : GroupingJBEditorTabs(
  project, parentDisposable, coroutineScope, tabListOptions, createTestEditorWindow(project, parentDisposable),
  isIslandsTheme = { isIslandsTheme },
  resolver = testResolver,
) {
  constructor(
    project: MockProjectEx,
    parentDisposable: Disposable,
    coroutineScope: CoroutineScope,
    tabListOptions: TabListOptions,
    isIslandsTheme: Boolean,
  ) : this(project, parentDisposable, coroutineScope, tabListOptions, isIslandsTheme, TestGroupResolver(project))

  /** While it is set and not complete, the groups of new tabs stay unresolved, as they do in the IDE for a moment. */
  var resolutionGate: CompletableDeferred<Unit>?
    get() = testResolver.gate
    set(value) {
      testResolver.gate = value
    }

  var groupNamesBuildCount: Int = 0
    private set

  override fun groupNamesBuilt() {
    groupNamesBuildCount++
  }

  var outlineBuildCount: Int = 0
    private set
  var fontHeightComputeCount: Int = 0
    private set
  var tabMoveCount: Int = 0
    private set
  var visibleInfosCallCount: Int = 0
    private set

  /** One entry per painted group label: the area x, the area width, and the drawn width. */
  val labelPaints: MutableList<Triple<Int, Int, Int>> = ArrayList()

  fun resetCounters() {
    outlineBuildCount = 0
    fontHeightComputeCount = 0
    tabMoveCount = 0
    visibleInfosCallCount = 0
    labelPaints.clear()
  }

  override fun outlineDataBuilt() {
    outlineBuildCount++
  }

  override fun groupLabelPainted(areaX: Int, areaWidth: Int, drawnWidth: Int) {
    labelPaints.add(Triple(areaX, areaWidth, drawnWidth))
  }

  override fun computeGroupHeaderFontHeight(): Int {
    fontHeightComputeCount++
    return super.computeGroupHeaderFontHeight()
  }

  override fun tabMoved() {
    tabMoveCount++
  }

  // The production border asks the selected tab for its EditorCompositePanel, which a test tab
  // does not have. A test paints only the group decoration, so the border may do nothing.
  override fun createTabBorder(): JBTabsBorder = object : JBTabsBorder(this) {
    override val effectiveBorder: Insets get() = JBUI.emptyInsets()
    override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {}
  }

  // The production choice asks the window for the EditorCompositePanel of the tab, which a test tab
  // does not have. A test selects the previous neighbor, or the next one for the first tab.
  override fun getToSelectOnRemoveOf(tab: TabInfo): TabInfo? {
    val visibleInfos = super.getVisibleInfos()
    if (selectedInfo != tab || visibleInfos.size == 1) return null
    val index = visibleInfos.indexOf(tab)
    if (index < 0) return null
    return visibleInfos.getOrNull(index - 1) ?: visibleInfos.getOrNull(index + 1)
  }

  /** The test window has no editor, so a test sets this to stand for the selection that the window makes at startup. */
  var windowSelection: Boolean = false

  override fun hasWindowSelection(): Boolean = windowSelection

  override fun getVisibleInfos(): List<TabInfo> {
    visibleInfosCallCount++
    return super.getVisibleInfos()
  }

  fun isDropIndexAllowedForTest(dropIndex: Int, draggedInfo: TabInfo): Boolean = isDropIndexAllowed(dropIndex, draggedInfo)
  fun visibleTabTexts(): List<String> = getVisibleInfos().map { it.text }

  /** The text that each visible tab label shows. It differs from the tab text for a group tab. */
  fun visibleLabelTexts(): List<String> = getVisibleInfos().map { info ->
    (info.tabLabel?.labelComponent as SimpleColoredComponent).getCharSequence(false).toString()
  }
}
