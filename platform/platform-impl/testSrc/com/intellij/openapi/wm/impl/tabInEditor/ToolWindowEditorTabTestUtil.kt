// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.KeyedExtensionCollector
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.RegisterToolWindowTaskData
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.platform.util.coroutines.childScope
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.toolWindow.ToolWindowButtonManager
import com.intellij.toolWindow.ToolWindowDefaultLayoutManager
import com.intellij.toolWindow.ToolWindowPaneOldButtonManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * A configurable [ToolWindowEditorTabSupport] used by the `tabInEditor` tests.
 *
 * [presentationFlow] drives the tab presentation, [canClose] controls the default
 * [filterTabsToClose] behavior, and [filterTabsToCloseAction] can emulate partial close decisions.
 * [filterTabsToCloseInvocations] records the [Content] groups passed to [filterTabsToClose].
 *
 * [canBeMovedToEditorAction] decides per [Content] whether it may be moved to the editor and
 * defaults to accepting everything. [presentationFlowRequests] records contents passed to [getTabPresentationFlow],
 * so tests can assert that the presentation flow is requested only for accepted contents.
 */
internal class FakeToolWindowEditorTabSupport(
  private val presentationFlow: Flow<ToolWindowEditorTabPresentation>,
  private val canClose: Boolean = true,
  private val filterTabsToCloseAction: ((List<Content>) -> List<Content>)? = null,
  private val canBeMovedToEditorAction: ((Content) -> Boolean)? = null,
) : ToolWindowEditorTabSupport {
  val filterTabsToCloseInvocations: MutableList<List<Content>> = mutableListOf()
  val presentationFlowRequests: MutableList<Content> = mutableListOf()

  override fun filterTabsToClose(project: Project, contents: List<Content>): List<Content> {
    filterTabsToCloseInvocations += contents
    return filterTabsToCloseAction?.invoke(contents) ?: if (canClose) contents else emptyList()
  }

  override fun canBeMovedToEditor(content: Content): Boolean {
    return canBeMovedToEditorAction?.invoke(content) ?: true
  }

  override fun getTabPresentationFlow(project: Project, content: Content): Flow<ToolWindowEditorTabPresentation> {
    presentationFlowRequests += content
    return presentationFlow
  }
}

/**
 * Registers [support] for [toolWindowId] on the `com.intellij.toolWindowEditorTabSupport` keyed
 * extension point so that [ToolWindowEditorTabSupportUtil.getSupport] resolves it.
 *
 * The point uses a [com.intellij.util.KeyedLazyInstanceEP] bean that instantiates its
 * implementation by FQN (which cannot carry a pre-built instance), and the backing collector is
 * private, so the test registers the live instance through [KeyedExtensionCollector.addExplicitExtension].
 */
internal fun registerFakeToolWindowEditorTabSupport(
  toolWindowId: String,
  support: ToolWindowEditorTabSupport,
  disposable: Disposable,
) {
  ToolWindowEditorTabSupportUtil.registerForTest(toolWindowId, support, disposable)
}

internal fun createTabContent(component: JComponent = JPanel(), displayName: String = "tab"): Content =
  ContentFactory.getInstance().createContent(component, displayName, false)

/**
 * Builds a transient tool window editor tab with its content already attached, which is the state a tab moved out of a
 * tool window is in. Transient is enough for every test here: none of them registers a
 * [ToolWindowEditorTabPersistenceProvider], so no tab would be restorable anyway.
 *
 * [presentationFlow] drives the tab presentation directly, so a test can both push presentations of its own and get a
 * tab with a session before it registers any [ToolWindowEditorTabSupport].
 */
internal fun createTabFile(
  project: Project,
  toolWindowId: String,
  content: Content = createTabContent(),
  presentationFlow: Flow<ToolWindowEditorTabPresentation> = flowOf(ToolWindowEditorTabPresentation("Tab")),
): ToolWindowEditorTabFile =
  ToolWindowEditorTabManager
    .getInstance(project)
    .createTransientEditorTabFileForTest(
      toolWindowId = toolWindowId,
      content = content,
      presentationFlow = presentationFlow,
    )

/**
 * The state of a tool window editor tab lives in [ToolWindowEditorTabSession], not in the file, so the tests
 * reach it through the owning [ToolWindowEditorTabManager].
 */
internal fun ToolWindowEditorTabFile.session(project: Project): ToolWindowEditorTabSession? =
  ToolWindowEditorTabManager.getInstance(project).getSession(this)

internal fun ToolWindowEditorTabFile.attachedContent(project: Project): Content? = session(project)?.content

internal fun ToolWindowEditorTabFile.tabTitle(project: Project): String? = session(project)?.presentation?.title

internal fun ToolWindowEditorTabFile.tabIcon(project: Project): Icon? = session(project)?.presentation?.icon

internal fun registerLocalToolWindow(
  project: Project,
  toolWindowId: String,
  disposable: Disposable,
  component: JComponent = JPanel(),
): ToolWindowImpl {
  val paneId = WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
  val buttonManager = ToolWindowPaneOldButtonManager(paneId)
  val manager = object : ToolWindowManagerImpl(
    project = project,
    isNewUi = false,
    isEdtRequired = false,
    coroutineScope = (project as ComponentManagerEx).getCoroutineScope(),
  ) {
    override fun getButtonManager(toolWindow: ToolWindow): ToolWindowButtonManager = buttonManager
  }

  val layoutManager = ToolWindowDefaultLayoutManager(isNewUi = false)
  layoutManager.noStateLoaded()
  manager.setLayoutOnInit(layoutManager.getLayoutCopy())
  Disposer.register(disposable, manager)

  return manager.registerToolWindow(
    task = RegisterToolWindowTaskData(
      id = toolWindowId,
      component = component,
    ),
    buttonManager = buttonManager,
  ).toolWindow
}

internal fun findDecorator(content: Content): InternalDecoratorImpl {
  val contentManager = requireNotNull(content.manager) { "Content is not attached to a ContentManager" }
  return requireNotNull(InternalDecoratorImpl.findNearestDecorator(contentManager.component)) {
    "No InternalDecoratorImpl found for content '${content.displayName}'"
  }
}

internal class RecordingFileEditorManager private constructor(
  project: Project,
  private val scope: CoroutineScope,
) : FileEditorManagerImpl(project, scope) {
  constructor(project: Project) : this(
    project,
    (project as ComponentManagerEx).getCoroutineScope().childScope("RecordingFileEditorManager"),
  )

  val closeRequests = mutableListOf<VirtualFile>()
  val closeInWindowRequests = mutableListOf<Pair<VirtualFile, EditorWindow>>()

  var currentWindowOverride: EditorWindow? = null
  var windowsOverride: Array<EditorWindow> = emptyArray()

  override var currentWindow: EditorWindow?
    get() = currentWindowOverride
    set(window) {
      currentWindowOverride = window
    }

  override val windows: Array<EditorWindow>
    get() = windowsOverride

  override fun closeFile(file: VirtualFile) {
    closeRequests += file
  }

  override fun closeFile(file: VirtualFile, window: EditorWindow) {
    closeInWindowRequests += file to window
  }

  override fun dispose() {
    super.dispose()
    // The real dispose() manages editor composites this test double never creates,
    // so only tear down the child scope to avoid leaking it past the test.
    scope.cancel()
  }
}
