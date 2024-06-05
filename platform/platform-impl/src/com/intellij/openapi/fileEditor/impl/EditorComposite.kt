// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.openapi.fileEditor.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.ClientId.Companion.isLocal
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.EdtDataProvider
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.ClientFileEditorManager.Companion.assignClientId
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl.Companion.DUMB_AWARE
import com.intellij.openapi.fileEditor.impl.HistoryEntry.Companion.FILE_ATTRIBUTE
import com.intellij.openapi.fileEditor.impl.HistoryEntry.Companion.TAG
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.PossiblyDumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Weighted
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.FocusWatcher
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.platform.fileEditor.FileEntry
import com.intellij.ui.*
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.tabs.JBTabs
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.EDT
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.UiNotifyConnector
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.*
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.*

private val LOG = logger<EditorComposite>()

@Internal
data class EditorCompositeModel internal constructor(
  @JvmField val fileEditorAndProviderList: List<FileEditorWithProvider>,
  @JvmField internal val state: FileEntry?,
) {
  @Internal
  constructor(fileEditorAndProviderList: List<FileEditorWithProvider>)
    : this(fileEditorAndProviderList = fileEditorAndProviderList, state = null)
}

/**
 * An abstraction over one or several file editors opened in the same tab (e.g., designer and code-behind).
 * It's a composite that can be pinned in the tab list or opened as a preview, not concrete file editors.
 * It also manages the internal UI structure: bottom and top components, panels, labels, actions for navigating between editors it owns.
 */
@Suppress("LeakingThis")
open class EditorComposite internal constructor(
  val file: VirtualFile,
  model: Flow<EditorCompositeModel>,
  @JvmField internal val project: Project,
  @JvmField internal val coroutineScope: CoroutineScope,
) : FileEditorComposite, Disposable {
  private val clientId: ClientId = ClientId.current

  private var tabbedPaneWrapper: TabbedPaneWrapper? = null
  private var compositePanel: EditorCompositePanel = EditorCompositePanel(composite = this)
  private val focusWatcher: FocusWatcher = FocusWatcher().also {
    it.install(compositePanel)
  }

  private val _selectedEditorWithProvider = MutableStateFlow<FileEditorWithProvider?>(null)

  /**
   * Currently selected editor
   */
  @JvmField
  @Internal
  val selectedEditorWithProvider: StateFlow<FileEditorWithProvider?> = _selectedEditorWithProvider.asStateFlow()

  private val topComponents = HashMap<FileEditor, JComponent>()
  private val bottomComponents = HashMap<FileEditor, JComponent>()
  private val displayNames = HashMap<FileEditor, String>()

  /**
   * Editors opened in the composite
   */
  private val fileEditorWithProviderList = ContainerUtil.createLockFreeCopyOnWriteList<FileEditorWithProvider>()
  private val dispatcher = EventDispatcher.create(EditorCompositeListener::class.java)

  internal var selfBorder: Boolean = false
    private set

  @JvmField
  internal val shownDeferred = CompletableDeferred<Unit>()

  init {
    EDT.assertIsEdt()

    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      shownDeferred.complete(Unit)
    }
    else {
      UiNotifyConnector.doWhenFirstShown(compositePanel, isDeferred = false) {
        shownDeferred.complete(Unit)
      }
    }

    coroutineScope.launch(ModalityState.any().asContextElement()) {
      shownDeferred.await()
      model.collect {
        handleModel(it)
      }
    }
  }

  private suspend fun handleModel(model: EditorCompositeModel) {
    val fileEditorWithProviders = model.fileEditorAndProviderList

    for (editorWithProvider in fileEditorWithProviders) {
      val editor = editorWithProvider.fileEditor
      FileEditor.FILE_KEY.set(editor, file)
      if (!clientId.isLocal) {
        assignClientId(editor, clientId)
      }
    }

    // TODO comment this and log a warning or log something
    if (fileEditorWithProviders.isEmpty()) {
      withContext(Dispatchers.EDT) {
        compositePanel.removeAll()
        _selectedEditorWithProvider.value = null
      }
      return
    }

    coroutineScope {
      val startTime = System.nanoTime()
      val deferredPublishers = async(CoroutineName("FileOpenedSyncListener computing")) {
        val messageBus = project.messageBus
        messageBus.syncAndPreloadPublisher(FileOpenedSyncListener.TOPIC) to
          messageBus.syncAndPreloadPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER)
      }
      val beforePublisher = project.messageBus.syncAndPreloadPublisher(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER)

      val selectedFileEditor = if (model.state == null) {
        (serviceAsync<FileEditorProviderManager>() as FileEditorProviderManagerImpl).getSelectedFileEditorProvider(
          file = file,
          providers = fileEditorWithProviders.map { it.provider },
          editorHistoryManager = project.serviceAsync<EditorHistoryManager>(),
        )
      }
      else {
        model.state.selectedProvider?.let { selectedProvider ->
          fileEditorWithProviders.firstOrNull { it.provider.editorTypeId == selectedProvider }?.provider
        }
      }

      val fileEditorManager = project.serviceAsync<FileEditorManager>()

      span("file opening in EDT and repaint", Dispatchers.EDT) {
        span("beforeFileOpened event executing") {
          blockingContext {
            beforePublisher!!.beforeFileOpened(fileEditorManager, file)
          }
        }

        applyFileEditorsInEdt(
          fileEditorWithProviders = fileEditorWithProviders,
          model = model,
          selectedFileEditorProvider = selectedFileEditor,
        )

        // Only after applyFileEditorsInEdt - for external clients composite API should use _actual_ _applied_ state, not intermediate.
        // For example, see EditorHistoryManager -
        // we will get assertion if we return a non-empty list of editors but do not set selected file editor.
        fileEditorWithProviderList.clear()
        fileEditorWithProviderList.addAll(fileEditorWithProviders)

        val (goodPublisher, deprecatedPublisher) = deferredPublishers.await()
        blockingContext {
          goodPublisher.fileOpenedSync(fileEditorManager, file, fileEditorWithProviders)
          @Suppress("DEPRECATION")
          deprecatedPublisher.fileOpenedSync(fileEditorManager, file, fileEditorWithProviders)
        }
      }

      triggerStatOpen(
        project = project,
        file = file,
        start = startTime,
        composite = this@EditorComposite,
        coroutineScope = coroutineScope,
      )

      val publisher = project.messageBus.syncAndPreloadPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER)
      span("fileOpened event executing", Dispatchers.EDT) {
        blockingContext {
          publisher.fileOpened(fileEditorManager, file)
        }
      }
    }
  }

  @RequiresEdt
  private fun applyFileEditorsInEdt(
    fileEditorWithProviders: List<FileEditorWithProvider>,
    model: EditorCompositeModel,
    selectedFileEditorProvider: FileEditorProvider?,
  ) {
    var fileEditorWithProviderToSelect = fileEditorWithProviders.firstOrNull()
    if (fileEditorWithProviders.size == 1) {
      setEditorComponent(fileEditorWithProviderToSelect!!.fileEditor)
    }
    else {
      val tabbedPaneWrapper = createTabbedPaneWrapper(component = null, fileEditorWithProviders = fileEditorWithProviders)
      setTabbedPaneComponent(tabbedPaneWrapper = tabbedPaneWrapper)

      if (selectedFileEditorProvider != null) {
        val index = fileEditorWithProviders.indexOfFirst { it.provider === selectedFileEditorProvider }
        if (index != -1) {
          tabbedPaneWrapper.selectedIndex = index
          fileEditorWithProviderToSelect = fileEditorWithProviders.get(index)
        }
      }
    }

    // select and focus before restoring state, to reduce the chance of focus stealing
    _selectedEditorWithProvider.value = fileEditorWithProviderToSelect

    for (fileEditorWithProvider in fileEditorWithProviders) {
      val provider = fileEditorWithProvider.provider
      val stateData = model.state?.providers?.get(provider.editorTypeId)
      val state = stateData?.let { provider.readState(stateData, project, file) }

      restoreEditorState(
        file = file,
        fileEditorWithProvider = fileEditorWithProvider,
        storedState = state,
        isNewEditor = true,
        exactState = false,
        project = project,
      )
    }

    fileEditorWithProviderToSelect?.fileEditor?.selectNotify()
  }

  private fun setTabbedPaneComponent(tabbedPaneWrapper: TabbedPaneWrapper) {
    val component = tabbedPaneWrapper.component
    this.tabbedPaneWrapper = tabbedPaneWrapper
    compositePanel.setComponent(component, focusComponent = { component })
  }

  private fun setEditorComponent(fileEditor: FileEditor) {
    compositePanel.setComponent(
      newComponent = createEditorComponent(fileEditor),
      focusComponent = { fileEditor.preferredFocusedComponent },
    )
  }

  @get:Deprecated("use {@link #getAllEditorsWithProviders()}", ReplaceWith("allProviders"), level = DeprecationLevel.ERROR)
  val providers: Array<FileEditorProvider>
    get() = allProviders.toTypedArray()

  override val allProviders: List<FileEditorProvider>
    get() = providerSequence.toList()

  internal val providerSequence: Sequence<FileEditorProvider>
    get() = fileEditorWithProviderList.asSequence().map { it.provider }

  private fun createTabbedPaneWrapper(
    component: EditorCompositePanel?,
    fileEditorWithProviders: List<FileEditorWithProvider>,
  ): TabbedPaneWrapper {
    val descriptor = PrevNextActionsDescriptor(IdeActions.ACTION_NEXT_EDITOR_TAB, IdeActions.ACTION_PREVIOUS_EDITOR_TAB)
    val tabbedPane = TabbedPaneWrapper.createJbTabs(project, SwingConstants.BOTTOM, descriptor, this)
    var firstEditor = true
    for (editorWithProvider in fileEditorWithProviders) {
      val editor = editorWithProvider.fileEditor
      tabbedPane.addTab(
        getDisplayName(editor),
        if (firstEditor && component != null) component.getComponent(0) as JComponent else createEditorComponent(editor),
      )
      firstEditor = false
    }

    // handles changes of selected editor
    tabbedPane.addChangeListener {
      val selectedIndex = tabbedPaneWrapper!!.selectedIndex
      require(selectedIndex != -1)
      _selectedEditorWithProvider.value = fileEditorWithProviders.get(selectedIndex)
    }
    return tabbedPane
  }

  private fun createEditorComponent(editor: FileEditor): JComponent {
    val component = JPanel(BorderLayout())
    var editorComponent = editor.component
    if (!isDumbAware(editor)) {
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
   * @return whether editor composite is pinned
   */
  var isPinned: Boolean = false
    set(pinned) {
      field = pinned
      ClientProperty.put(compositePanel, JBTabsImpl.PINNED, if (field) true else null)
    }


  private val _isPreviewFlow = MutableStateFlow(false)
  internal val isPreviewFlow: StateFlow<Boolean> = _isPreviewFlow.asStateFlow()

  /**
   * Whether the composite is opened as a preview tab or not
   */
  override var isPreview: Boolean
    get() = _isPreviewFlow.value
    set(value) {
      _isPreviewFlow.value = value
    }

  fun addListener(listener: EditorCompositeListener, disposable: Disposable?) {
    dispatcher.addListener(listener, disposable!!)
  }

  /**
   * @return preferred focused component inside myEditor composite. Composite uses FocusWatcher to track focus movement inside the editor.
   */
  open val preferredFocusedComponent: JComponent?
    get() {
      val editorWithProvider = selectedEditorWithProvider.value ?: return null
      val component = focusWatcher.focusedComponent
      if (component !is JComponent || !component.isShowing() || !component.isEnabled() || !component.isFocusable()) {
        return editorWithProvider.fileEditor.preferredFocusedComponent
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
    get() = fileEditorWithProviderList.map { it.fileEditor }

  val allEditorsWithProviders: List<FileEditorWithProvider>
    get() = java.util.List.copyOf(fileEditorWithProviderList)

  fun getTopComponents(editor: FileEditor): List<JComponent> {
    return topComponents.get(editor)!!.components.mapNotNull { (it as? NonOpaquePanel)?.targetComponent }
  }

  internal fun containsFileEditor(editor: FileEditor): Boolean = fileEditorWithProviderList.any { it.fileEditor === editor }

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

  @RequiresEdt
  private fun manageTopOrBottomComponent(editor: FileEditor, component: JComponent, top: Boolean, remove: Boolean) {
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
      if (top) {
        dispatcher.multicaster.topComponentAdded(editor, index, component, container)
      }
      else {
        dispatcher.multicaster.bottomComponentAdded(editor, index, component, container)
      }
    }
    container.revalidate()
  }

  fun setDisplayName(editor: FileEditor, name: @NlsContexts.TabTitle String) {
    val index = fileEditorWithProviderList.indexOfFirst { it.fileEditor == editor }
    assert(index != -1)
    displayNames.put(editor, name)
    tabbedPaneWrapper?.setTitleAt(index, name)
    dispatcher.multicaster.displayNameChanged(editor, name)
  }

  @Suppress("HardCodedStringLiteral")
  protected fun getDisplayName(editor: FileEditor): @NlsContexts.TabTitle String = displayNames.get(editor) ?: editor.name

  val selectedEditor: FileEditor?
    get() = selectedWithProvider?.fileEditor

  val selectedWithProvider: FileEditorWithProvider?
    get() = selectedEditorWithProvider.value

  fun setSelectedEditor(providerId: String) {
    val fileEditorWithProvider = fileEditorWithProviderList.firstOrNull { it.provider.editorTypeId == providerId } ?: return
    setSelectedEditor(fileEditorWithProvider)
  }

  internal fun setSelectedEditor(provider: FileEditorProvider) {
    val fileEditorWithProvider = fileEditorWithProviderList.firstOrNull { it.provider == provider } ?: return
    setSelectedEditor(fileEditorWithProvider)
  }

  fun setSelectedEditor(editor: FileEditor) {
    val newSelection = fileEditorWithProviderList.firstOrNull { it.fileEditor == editor }
    LOG.assertTrue(newSelection != null, "Unable to find editor=$editor")
    setSelectedEditor(newSelection!!)
  }

  fun setSelectedEditor(editorWithProvider: FileEditorWithProvider) {
    if (fileEditorWithProviderList.size == 1) {
      LOG.assertTrue(tabbedPaneWrapper == null)
      LOG.assertTrue(editorWithProvider == fileEditorWithProviderList[0])
      return
    }

    val index = fileEditorWithProviderList.indexOf(editorWithProvider)
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
     * @return `true` if the composite contains at least one modified editor
     */
    get() = allEditors.any { it.isModified }

  override fun dispose() {
    try {
      coroutineScope.cancel("disposed")
    }
    finally {
      _selectedEditorWithProvider.value = null
      for (editor in fileEditorWithProviderList) {
        @Suppress("DEPRECATION")
        if (!Disposer.isDisposed(editor.fileEditor)) {
          Disposer.dispose(editor.fileEditor)
        }
      }
      focusWatcher.deinstall(focusWatcher.topComponent)
    }
  }

  @RequiresEdt
  fun addEditor(editor: FileEditor, provider: FileEditorProvider) {
    val editorWithProvider = FileEditorWithProvider(editor, provider)
    fileEditorWithProviderList.add(editorWithProvider)
    FileEditor.FILE_KEY.set(editor, file)
    if (!clientId.isLocal) {
      assignClientId(editor, clientId)
    }

    when {
      fileEditorWithProviderList.size == 1 -> {
        val fileEditorWithProvider = fileEditorWithProviderList.get(0)
        setEditorComponent(fileEditorWithProvider.fileEditor)
        _selectedEditorWithProvider.value = fileEditorWithProvider
      }
      tabbedPaneWrapper == null -> {
        setTabbedPaneComponent(createTabbedPaneWrapper(compositePanel, fileEditorWithProviderList))
      }
      else -> {
        val component = createEditorComponent(editor)
        tabbedPaneWrapper!!.addTab(getDisplayName(editor), component)
      }
    }

    focusWatcher.deinstall(focusWatcher.topComponent)
    focusWatcher.install(compositePanel)
    if (fileEditorWithProviderList.size == 1) {
      preferredFocusedComponent?.requestFocusInWindow()
    }
    dispatcher.multicaster.editorAdded(editorWithProvider)
  }

  @RequiresEdt
  internal fun removeEditor(editorTypeId: String) {
    val fileEditorWithProviderList = fileEditorWithProviderList
    for (i in fileEditorWithProviderList.indices.reversed()) {
      val (fileEditor, provider) = fileEditorWithProviderList.get(i)
      if (provider.editorTypeId != editorTypeId) {
        continue
      }

      tabbedPaneWrapper?.removeTabAt(i)
      fileEditorWithProviderList.removeAt(i)
      topComponents.remove(fileEditor)
      bottomComponents.remove(fileEditor)
      displayNames.remove(fileEditor)
      Disposer.dispose(fileEditor)
    }

    if (fileEditorWithProviderList.isEmpty()) {
      compositePanel.removeAll()
    }
    else if (fileEditorWithProviderList.size == 1 && tabbedPaneWrapper != null) {
      tabbedPaneWrapper = null
      setEditorComponent(fileEditorWithProviderList.single().fileEditor)
    }

    if (selectedEditorWithProvider.value?.provider?.editorTypeId == editorTypeId) {
      _selectedEditorWithProvider.value = fileEditorWithProviderList.firstOrNull()
    }
    focusWatcher.deinstall(focusWatcher.topComponent)
    focusWatcher.install(compositePanel)
    dispatcher.multicaster.editorRemoved(editorTypeId)
  }

  internal fun currentStateAsHistoryEntry(): HistoryEntry? {
    if (fileEditorWithProviderList.isEmpty()) {
      // not initialized
      return null
    }

    val states = fileEditorWithProviderList.map { it.fileEditor.getState(FileEditorStateLevel.FULL) }
    val providers = allProviders
    return HistoryEntry.createLight(
      file = file,
      providers = providers,
      states = states,
      selectedProvider = (selectedWithProvider ?: fileEditorWithProviderList.first()).provider,
      isPreview = isPreview,
    )
  }

  internal fun writeCurrentStateAsHistoryEntry(project: Project): Element {
    val selectedEditorWithProvider = selectedEditorWithProvider.value
    val element = Element(TAG)
    element.setAttribute(FILE_ATTRIBUTE, file.url)
    for (fileEditorWithProvider in fileEditorWithProviderList) {
      val providerElement = Element(PROVIDER_ELEMENT)
      val provider = fileEditorWithProvider.provider

      providerElement.setAttribute(EDITOR_TYPE_ID_ATTRIBUTE, provider.editorTypeId)

      if (fileEditorWithProvider === selectedEditorWithProvider) {
        providerElement.setAttribute(SELECTED_ATTRIBUTE_VALUE, "true")
      }

      val state = fileEditorWithProvider.fileEditor.getState(FileEditorStateLevel.FULL)
      if (state !== FileEditorState.INSTANCE) {
        val stateElement = Element(STATE_ELEMENT)
        provider.writeState(state, project, stateElement)
        if (!stateElement.isEmpty) {
          providerElement.addContent(stateElement)
        }
      }

      element.addContent(providerElement)
    }
    if (isPreview) {
      element.setAttribute(PREVIEW_ATTRIBUTE, "true")
    }
    return element
  }

  override fun toString() = "EditorComposite(identityHashCode=${System.identityHashCode(this)}, file=$file)"
}

internal class EditorCompositePanel(@JvmField val composite: EditorComposite) : JPanel(BorderLayout()), EdtDataProvider {
  var focusComponent: () -> JComponent? = { null }
    private set

  init {
    addFocusListener(object : FocusAdapter() {
      override fun focusGained(e: FocusEvent) {
        composite.coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          if (!hasFocus()) {
            return@launch
          }
          val focus = composite.selectedWithProvider?.fileEditor?.preferredFocusedComponent
          if (focus != null && !focus.hasFocus()) {
            IdeFocusManager.getGlobalInstance().requestFocus(focus, true)
          }
        }
      }
    })
    focusTraversalPolicy = object : FocusTraversalPolicy() {
      override fun getComponentAfter(aContainer: Container, aComponent: Component) = composite.focusComponent

      override fun getComponentBefore(aContainer: Container, aComponent: Component) = composite.focusComponent

      override fun getFirstComponent(aContainer: Container) = composite.focusComponent

      override fun getLastComponent(aContainer: Container) = composite.focusComponent

      override fun getDefaultComponent(aContainer: Container) = composite.focusComponent
    }
    isFocusCycleRoot = true
  }

  fun setComponent(newComponent: JComponent, focusComponent: () -> JComponent?) {
    removeAll()

    val scrollPanes = UIUtil.uiTraverser(newComponent)
      .expand { o -> o === newComponent || o is JPanel || o is JLayeredPane }
      .filter(JScrollPane::class.java)
    for (scrollPane in scrollPanes) {
      scrollPane.border = SideBorder(JBColor.border(), SideBorder.NONE)
    }

    add(newComponent, BorderLayout.CENTER)
    this.focusComponent = focusComponent
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

  override fun uiDataSnapshot(sink: DataSink) {
    sink[CommonDataKeys.PROJECT] = composite.project
    sink[PlatformCoreDataKeys.FILE_EDITOR] = composite.selectedEditor
    sink[CommonDataKeys.VIRTUAL_FILE] = composite.file
    sink[CommonDataKeys.VIRTUAL_FILE_ARRAY] = arrayOf(composite.file)
    val component = composite.preferredFocusedComponent
    if (component !== this) {
      DataSink.uiDataSnapshot(sink, component)
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

internal fun isEditorComposite(component: Component): Boolean = component is EditorCompositePanel

/**
 * A mapper for old API with arrays and pairs
 */
@Internal
fun retrofitEditorComposite(composite: FileEditorComposite?): com.intellij.openapi.util.Pair<Array<FileEditor>, Array<FileEditorProvider>> {
  if (composite == null) {
    return com.intellij.openapi.util.Pair(FileEditor.EMPTY_ARRAY, FileEditorProvider.EMPTY_ARRAY)
  }
  else {
    return com.intellij.openapi.util.Pair(composite.allEditors.toTypedArray(), composite.allProviders.toTypedArray())
  }
}

internal fun restoreEditorState(
  file: VirtualFile,
  fileEditorWithProvider: FileEditorWithProvider,
  isNewEditor: Boolean,
  storedState: FileEditorState?,
  exactState: Boolean,
  project: Project,
) {
  var state = storedState
  if (state == null) {
    if (isNewEditor) {
      // We have to try to get state from the history only in case of the editor is not opened.
      // Otherwise, history entry might have a state out of sync with the current editor state.
      state = EditorHistoryManager.getInstance(project).getState(file, fileEditorWithProvider.provider)
    }
    if (state == null) {
      return
    }
  }

  val editor = fileEditorWithProvider.fileEditor
  if (isDumbAware(editor)) {
    editor.setState(state, exactState)
  }
  else {
    DumbService.getInstance(project).runWhenSmart { editor.setState(state, exactState) }
  }
}

private fun isDumbAware(editor: FileEditor): Boolean {
  return editor.getUserData(DUMB_AWARE) == true && (editor !is PossiblyDumbAware || (editor as PossiblyDumbAware).isDumbAware)
}

internal suspend fun focusEditorOnCompositeOpenComplete(
  composite: EditorComposite,
  splitters: EditorsSplitters,
  toFront: Boolean = true,
): Boolean {
  // wait for the file editor
  composite.selectedEditorWithProvider.filterNotNull().first()
  return withContext(Dispatchers.EDT) {
    val currentSelectedComposite = splitters.currentCompositeFlow.value
    // while the editor was loading, the user switched to another editor - don't steal focus
    if (currentSelectedComposite === composite) {
      val preferredFocusedComponent = composite.preferredFocusedComponent
      if (preferredFocusedComponent == null) {
        LOG.warn("Cannot focus editor (splitters=$splitters, composite=$composite, reason=preferredFocusedComponent is null)")
        false
      }
      else {
        preferredFocusedComponent.requestFocusInWindow()
        if (toFront) {
          IdeFocusManager.getGlobalInstance().toFront(splitters)
        }
        true
      }
    }
    else {
      LOG.warn("Cannot focus editor (splitters=$splitters, " +
               "composite=$composite, currentComposite=$currentSelectedComposite, " +
               "reason=selection changed)")
      false
    }
  }
}

internal suspend fun doOnCompositeOpenComplete(
  composite: EditorComposite,
  action: suspend (selectedEditor: FileEditor) -> Unit,
) {
  action(composite.selectedEditorWithProvider.filterNotNull().first().fileEditor)
}