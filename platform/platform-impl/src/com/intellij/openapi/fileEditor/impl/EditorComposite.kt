// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.openapi.fileEditor.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.ClientId.Companion.isLocal
import com.intellij.ide.impl.DataValidators
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.ClientFileEditorManager.Companion.assignClientId
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.Weighted
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.FocusWatcher
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.*
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.tabs.JBTabs
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.EDT
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * An abstraction over one or several file editors opened in the same tab (e.g. designer and code-behind).
 * It's a composite that can be pinned in the tab list or opened as a preview, not concrete file editors.
 * It also manages the internal UI structure: bottom and top components, panels, labels, actions for navigating between editors it owns.
 */
@Suppress("LeakingThis")
open class EditorComposite internal constructor(
  val file: VirtualFile,
  editorsWithProviders: List<FileEditorWithProvider>,
  internal val project: Project,
) : FileEditorComposite, Disposable {
  private val clientId: ClientId

  private var tabbedPaneWrapper: TabbedPaneWrapper? = null
  private var compositePanel: EditorCompositePanel
  private val focusWatcher: FocusWatcher?

  /**
   * Currently selected editor
   */
  protected val selectedEditorWithProviderMutable: MutableStateFlow<FileEditorWithProvider?> = MutableStateFlow(null)
  internal val selectedEditorWithProvider: StateFlow<FileEditorWithProvider?> = selectedEditorWithProviderMutable.asStateFlow()

  private val topComponents = HashMap<FileEditor, JComponent>()
  private val bottomComponents = HashMap<FileEditor, JComponent>()
  private val displayNames = HashMap<FileEditor, String>()

  /**
   * Editors opened in the composite
   */
  private val editorsWithProviders = CopyOnWriteArrayList(editorsWithProviders)
  private val dispatcher = EventDispatcher.create(EditorCompositeListener::class.java)
  private var selfBorder = false

  init {
    EDT.assertIsEdt()
    clientId = ClientId.current
    for (editorWithProvider in editorsWithProviders) {
      val editor = editorWithProvider.fileEditor
      FileEditor.FILE_KEY.set(editor, file)
      if (!clientId.isLocal) {
        assignClientId(editor, clientId)
      }
    }

    when {
      editorsWithProviders.size > 1 -> {
        tabbedPaneWrapper = createTabbedPaneWrapper(component = null)
        val component = tabbedPaneWrapper!!.component
        compositePanel = EditorCompositePanel(realComponent = component, composite = this, focusComponent = { component })
      }
      editorsWithProviders.size == 1 -> {
        tabbedPaneWrapper = null
        val editor = editorsWithProviders[0].fileEditor
        compositePanel = EditorCompositePanel(realComponent = createEditorComponent(editor),
                                              composite = this,
                                              focusComponent = { editor.preferredFocusedComponent })
      }
      else -> throw IllegalArgumentException("editor array cannot be empty")
    }

    selectedEditorWithProviderMutable.value = editorsWithProviders[0]
    focusWatcher = FocusWatcher()
    focusWatcher.install(compositePanel)
  }

  companion object {
    private val LOG = logger<EditorComposite>()

    private fun calcComponentInsertionIndex(newComponent: JComponent, container: JComponent): Int {
      var i = 0
      val max = container.componentCount
      while (i < max) {
        val childWrapper = container.getComponent(i)
        val childComponent = if (childWrapper is Wrapper) childWrapper.targetComponent else childWrapper
        val weighted1 = newComponent is Weighted
        val weighted2 = childComponent is Weighted
        if (!weighted2) {
          i++
          continue
        }
        if (!weighted1) return i
        val w1 = (newComponent as Weighted).weight
        val w2 = (childComponent as Weighted).weight
        if (w1 < w2) return i
        i++
      }
      return -1
    }

    @JvmStatic
    fun isEditorComposite(component: Component): Boolean = component is EditorCompositePanel

    private fun createTopBottomSideBorder(top: Boolean, borderColor: Color?): SideBorder {
      return object : SideBorder(null, if (top) BOTTOM else TOP) {
        override fun getLineColor(): Color {
          if (borderColor != null) {
            return borderColor
          }

          val scheme = EditorColorsManager.getInstance().globalScheme
          if (ExperimentalUI.isNewUI()) {
            return scheme.defaultBackground
          }
          else {
            return scheme.getColor(EditorColors.TEARLINE_COLOR) ?: JBColor.BLACK
          }
        }
      }
    }

    /**
     * A mapper for old API with arrays and pairs
     */
    fun retrofit(composite: FileEditorComposite?): Pair<Array<FileEditor>, Array<FileEditorProvider>> {
      if (composite == null) {
        return Pair(FileEditor.EMPTY_ARRAY, FileEditorProvider.EMPTY_ARRAY)
      }
      else {
        return Pair(composite.allEditors.toTypedArray(), composite.allProviders.toTypedArray())
      }
    }
  }

  @get:Deprecated("use {@link #getAllEditorsWithProviders()}", ReplaceWith("allProviders"), level = DeprecationLevel.ERROR)
  val providers: Array<FileEditorProvider>
    get() = allProviders.toTypedArray()

  override val allProviders: List<FileEditorProvider>
    get() = providerSequence.toList()

  internal val providerSequence: Sequence<FileEditorProvider>
    get() = editorsWithProviders.asSequence().map { it.provider }

  private fun createTabbedPaneWrapper(component: EditorCompositePanel?): TabbedPaneWrapper {
    val descriptor = PrevNextActionsDescriptor(IdeActions.ACTION_NEXT_EDITOR_TAB, IdeActions.ACTION_PREVIOUS_EDITOR_TAB)
    val wrapper = TabbedPaneWrapper.createJbTabs(project, SwingConstants.BOTTOM, descriptor, this)
    var firstEditor = true
    for (editorWithProvider in editorsWithProviders) {
      val editor = editorWithProvider.fileEditor
      wrapper.addTab(
        getDisplayName(editor),
        if (firstEditor && component != null) component.getComponent(0) as JComponent else createEditorComponent(editor),
      )
      firstEditor = false
    }

    // handles changes of selected editor
    wrapper.addChangeListener {
      val selectedIndex = tabbedPaneWrapper!!.selectedIndex
      require(selectedIndex != -1)
      selectedEditorWithProviderMutable.value = editorsWithProviders.get(selectedIndex)
    }
    return wrapper
  }

  private fun createEditorComponent(editor: FileEditor): JComponent {
    val component = JPanel(BorderLayout())
    var editorComponent = editor.component
    if (!FileEditorManagerImpl.isDumbAware(editor)) {
      editorComponent = DumbService.getInstance(project).wrapGently(editorComponent, editor)
    }
    component.add(editorComponent, BorderLayout.CENTER)
    val topPanel = TopBottomPanel()
    topComponents.put(editor, topPanel)
    component.add(topPanel, BorderLayout.NORTH)
    val bottomPanel = TopBottomPanel()
    bottomComponents.put(editor, bottomPanel)
    component.add(bottomPanel, BorderLayout.SOUTH)
    return component
  }

  /**
   * @return whether myEditor composite is pinned
   */
  var isPinned: Boolean = false
    /**
     * Sets new "pinned" state
     */
    set(pinned) {
      val oldPinned = field
      field = pinned
       (compositePanel.parent as? JComponent)?.let {
        ClientProperty.put(it, JBTabsImpl.PINNED, if (field) true else null)
      }
      if (pinned != oldPinned) {
        dispatcher.multicaster.isPinnedChanged(this, pinned)
      }
    }

  /**
   * Whether the composite is opened as a preview tab or not
   */
  override var isPreview: Boolean = false
    set(preview) {
      if (preview != field) {
        field = preview
        dispatcher.multicaster.isPreviewChanged(this, preview)
      }
    }

  fun addListener(listener: EditorCompositeListener, disposable: Disposable?) {
    dispatcher.addListener(listener, disposable!!)
  }

  /**
   * @return preferred focused component inside myEditor composite. Composite uses FocusWatcher to track focus movement inside the editor.
   */
  open val preferredFocusedComponent: JComponent?
    get() {
      if (selectedEditorWithProvider.value == null) {
        return null
      }

      val component = focusWatcher!!.focusedComponent
      if (component !is JComponent || !component.isShowing() || !component.isEnabled() || !component.isFocusable()) {
        return selectedEditor?.preferredFocusedComponent
      }
      else {
        return component
      }
    }

  @Deprecated(message = "Use FileEditorManager.getInstance()",
              replaceWith = ReplaceWith("FileEditorManager.getInstance()"),
              level = DeprecationLevel.ERROR)
  val fileEditorManager: FileEditorManager
    get() = FileEditorManager.getInstance(project)

  @get:Deprecated("use {@link #getAllEditors()}", ReplaceWith("allEditors"), level = DeprecationLevel.ERROR)
  val editors: Array<FileEditor>
    get() = allEditors.toTypedArray()

  final override val allEditors: List<FileEditor>
    get() = editorsWithProviders.map { it.fileEditor }

  val allEditorsWithProviders: List<FileEditorWithProvider>
    get() = java.util.List.copyOf(editorsWithProviders)

  fun getTopComponents(editor: FileEditor): List<JComponent> {
    return topComponents.get(editor)!!.components.mapNotNull { (it as? NonOpaquePanel)?.targetComponent }
  }

  internal fun containsFileEditor(editor: FileEditor): Boolean {
    return editorsWithProviders.any { it.fileEditor === editor }
  }

  open val tabs: JBTabs?
    get() = tabbedPaneWrapper?.let { (it.tabbedPane as JBTabsPaneImpl).tabs }

  fun addTopComponent(editor: FileEditor, component: JComponent) {
    manageTopOrBottomComponent(editor = editor, component = component, top = true, remove = false)
  }

  fun removeTopComponent(editor: FileEditor, component: JComponent) {
    manageTopOrBottomComponent(editor = editor, component = component, top = true, remove = true)
  }

  fun addBottomComponent(editor: FileEditor, component: JComponent) {
    manageTopOrBottomComponent(editor = editor, component = component, top = false, remove = false)
  }

  fun removeBottomComponent(editor: FileEditor, component: JComponent) {
    manageTopOrBottomComponent(editor = editor, component = component, top = false, remove = true)
  }

  private fun manageTopOrBottomComponent(editor: FileEditor, component: JComponent, top: Boolean, remove: Boolean) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val container = (if (top) topComponents.get(editor) else bottomComponents.get(editor))!!
    selfBorder = false
    if (remove) {
      container.remove(component.parent)
      val multicaster = dispatcher.multicaster
      if (top) {
        multicaster.topComponentRemoved(editor, component, container)
      }
      else {
        multicaster.bottomComponentRemoved(editor, component, container)
      }
    }
    else {
      val wrapper = NonOpaquePanel(component)
      if (component.getClientProperty(FileEditorManager.SEPARATOR_DISABLED) != true) {
        val border = ClientProperty.get(component, FileEditorManager.SEPARATOR_BORDER)
        selfBorder = border != null
        wrapper.border = border ?: createTopBottomSideBorder(top = top,
                                                             borderColor = ClientProperty.get(component, FileEditorManager.SEPARATOR_COLOR))
      }
      val index = calcComponentInsertionIndex(component, container)
      container.add(wrapper, index)
      val multicaster = dispatcher.multicaster
      if (top) {
        multicaster.topComponentAdded(editor, index, component, container)
      }
      else {
        multicaster.bottomComponentAdded(editor, index, component, container)
      }
    }
    container.revalidate()
  }

  fun selfBorder(): Boolean = selfBorder

  fun setDisplayName(editor: FileEditor, name: @NlsContexts.TabTitle String) {
    val index = editorsWithProviders.indexOfFirst { it.fileEditor == editor }
    assert(index != -1)
    displayNames.set(editor, name)
    tabbedPaneWrapper?.setTitleAt(index, name)
    dispatcher.multicaster.displayNameChanged(editor, name)
  }

  @Suppress("HardCodedStringLiteral")
  protected fun getDisplayName(editor: FileEditor): @NlsContexts.TabTitle String = displayNames.get(editor) ?: editor.name

  internal fun deselectNotify() {
    val selected = selectedEditorWithProvider.value ?: return
    selectedEditorWithProviderMutable.value = null
    selected.fileEditor.deselectNotify()
  }

  val selectedEditor: FileEditor?
    get() = selectedWithProvider?.fileEditor

  val selectedWithProvider: FileEditorWithProvider?
    get() = selectedEditorWithProvider.value

  fun setSelectedEditor(providerId: String) {
    val fileEditorWithProvider = editorsWithProviders.firstOrNull { it.provider.editorTypeId == providerId } ?: return
    setSelectedEditor(fileEditorWithProvider)
  }

  fun setSelectedEditor(editor: FileEditor) {
    val newSelection = editorsWithProviders.firstOrNull { it.fileEditor == editor }
    LOG.assertTrue(newSelection != null, "Unable to find editor=$editor")
    setSelectedEditor(newSelection!!)
  }

  open fun setSelectedEditor(editorWithProvider: FileEditorWithProvider) {
    if (editorsWithProviders.size == 1) {
      LOG.assertTrue(tabbedPaneWrapper == null)
      LOG.assertTrue(editorWithProvider == editorsWithProviders[0])
      return
    }

    val index = editorsWithProviders.indexOf(editorWithProvider)
    LOG.assertTrue(index != -1)
    LOG.assertTrue(tabbedPaneWrapper != null)
    tabbedPaneWrapper!!.selectedIndex = index
  }

  open val component: JComponent
    /**
     * @return component which represents a set of file editors in the UI
     */
    get() = compositePanel

  open val focusComponent: JComponent?
    /**
     * @return component which represents the component that is supposed to be focusable
     */
    get() = compositePanel.focusComponent()

  val isModified: Boolean
    /**
     * @return `true` if the composite contains at least one modified myEditor
     */
    get() = allEditors.any { it.isModified }

  override fun dispose() {
    selectedEditorWithProviderMutable.value = null
    for (editor in editorsWithProviders) {
      @Suppress("DEPRECATION")
      if (!Disposer.isDisposed(editor.fileEditor)) {
        Disposer.dispose(editor.fileEditor)
      }
    }
    focusWatcher?.deinstall(focusWatcher.topComponent)
  }

  fun addEditor(editor: FileEditor, provider: FileEditorProvider) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val editorWithProvider = FileEditorWithProvider(editor, provider)
    editorsWithProviders.add(editorWithProvider)
    FileEditor.FILE_KEY.set(editor, file)
    if (!clientId.isLocal) {
      assignClientId(editor, clientId)
    }
    if (tabbedPaneWrapper == null) {
      tabbedPaneWrapper = createTabbedPaneWrapper(compositePanel)
      compositePanel.setComponent(tabbedPaneWrapper!!.component)
    }
    else {
      val component = createEditorComponent(editor)
      tabbedPaneWrapper!!.addTab(getDisplayName(editor), component)
    }
    focusWatcher!!.deinstall(focusWatcher.topComponent)
    focusWatcher.install(compositePanel)
    dispatcher.multicaster.editorAdded(editorWithProvider)
  }

  fun currentStateAsHistoryEntry(): HistoryEntry {
    val editors = allEditors
    val states = editors.map { it.getState(FileEditorStateLevel.FULL) }
    val selectedProviderIndex = editors.indexOf(selectedEditorWithProvider.value?.fileEditor)
    LOG.assertTrue(selectedProviderIndex != -1)
    val providers = allProviders
    return HistoryEntry.createLight(file, providers, states, providers.get(selectedProviderIndex), isPreview)
  }
}

private class EditorCompositePanel(realComponent: JComponent,
                                   private val composite: EditorComposite,
                                   @JvmField var focusComponent: () -> JComponent?) : JPanel(BorderLayout()), DataProvider {
  init {
    isFocusable = false
    add(realComponent, BorderLayout.CENTER)
  }

  fun setComponent(newComponent: JComponent) {
    add(newComponent, BorderLayout.CENTER)
    focusComponent = { newComponent }
  }

  override fun requestFocusInWindow(): Boolean = focusComponent()?.requestFocusInWindow() ?: false

  override fun requestFocus() {
    val focusComponent = focusComponent() ?: return
    val focusManager = IdeFocusManager.getGlobalInstance()
    focusManager.doWhenFocusSettlesDown {
      focusManager.requestFocus(focusComponent, true)
    }
  }

  @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
  override fun requestDefaultFocus(): Boolean = focusComponent()?.requestDefaultFocus() ?: false

  override fun getData(dataId: String): Any? {
    return when {
      CommonDataKeys.PROJECT.`is`(dataId) -> composite.project
      PlatformCoreDataKeys.FILE_EDITOR.`is`(dataId) -> composite.selectedEditor
      CommonDataKeys.VIRTUAL_FILE.`is`(dataId) -> composite.file
      CommonDataKeys.VIRTUAL_FILE_ARRAY.`is`(dataId) -> arrayOf(composite.file)
      else -> {
        val component = composite.preferredFocusedComponent
        if (component is DataProvider && component !== this) {
          val data = component.getData(dataId)
          if (data == null) null else DataValidators.validOrNull(data, dataId, component)
        }
        else {
          null
        }
      }
    }
  }
}

private class TopBottomPanel : JPanel() {
  init {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
  }

  override fun getBackground(): Color {
    val globalScheme = EditorColorsManager.getInstance().globalScheme
    if (ExperimentalUI.isNewUI()) {
      return globalScheme.defaultBackground
    }
    else {
      return globalScheme.getColor(EditorColors.GUTTER_BACKGROUND) ?: EditorColors.GUTTER_BACKGROUND.defaultColor
    }
  }
}