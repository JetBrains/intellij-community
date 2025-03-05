// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.openapi.fileEditor

import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettings.Companion.getInstance
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil.getAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.observable.util.addKeyListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.pom.Navigatable
import com.intellij.ui.ExperimentalUI.Companion.isNewUI
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLayeredPane
import com.intellij.util.Alarm
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.ui.EDT
import com.intellij.util.ui.StartupUiUtil.addAwtListener
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.AWTEvent
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.event.AWTEventListener
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.function.Supplier
import javax.swing.*

private val PARENT_SPLIT_EDITOR_KEY = Key.create<TextEditorWithPreview>("parentSplit")

/**
 * Two-panel editor with three states: Editor, Preview and Editor with Preview.
 */
@Suppress("LeakingThis")
open class TextEditorWithPreview @JvmOverloads constructor(
  @JvmField protected val myEditor: TextEditor,
  @JvmField protected val myPreview: FileEditor,
  private val name: @Nls String = "TextEditorWithPreview",
  defaultLayout: Layout = Layout.SHOW_EDITOR_AND_PREVIEW,
  private var isVerticalSplit: Boolean = false,
  private var layout: Layout? = null,
) : UserDataHolderBase(), TextEditor {
  @Suppress("LeakingThis")
  private val listenerGenerator = MyListenersMultimap(this)
  private val defaultLayout: Layout = myEditor.file?.getUserData(DEFAULT_LAYOUT_FOR_FILE) ?: defaultLayout

  private val ui = SynchronizedClearableLazy { TextEditorWithPreviewUi(this) }

  init {
    myEditor.putUserData(PARENT_SPLIT_EDITOR_KEY, this)
    myPreview.putUserData(PARENT_SPLIT_EDITOR_KEY, this)
    EventQueue.invokeLater {
      ui.value
    }
  }

  // we cannot initialize UI it in the constructor, because we call some methods that inheritors override (`createToolbar`)
  private class TextEditorWithPreviewUi(host: TextEditorWithPreview) {
    @JvmField val component: JComponent
    @JvmField var splitter: JBSplitter = host.createSplitter()
    @JvmField val toolbarWrapper: SplitEditorToolbar

    init {
      splitter.splitterProportionKey = host.splitterProportionKey
      splitter.firstComponent = host.myEditor.component
      splitter.secondComponent = host.myPreview.component
      // we're using OnePixelSplitter, but it actually supports wider dividers
      splitter.dividerWidth = if (isNewUI()) 1 else 2
      splitter.divider.background = JBColor.lazy(Supplier {
        EditorColorsManager.getInstance().globalScheme.getColor(EditorColors.PREVIEW_BORDER_COLOR) ?: UIUtil.getPanelBackground()
      })

      val toolbarWrapper = host.createSplitEditorToolbar(splitter)
      this.toolbarWrapper = toolbarWrapper

      var layout = host.layout
      if (layout == null) {
        val lastUsed = PropertiesComponent.getInstance().getValue(host.layoutPropertyName)
        layout = Layout.fromId(id = lastUsed, defaultValue = host.defaultLayout)
        host.layout = layout
      }
      host.adjustEditorsVisibility(layout)

      if (host.isShowFloatingToolbar && toolbarWrapper.isLeftToolbarEmpty) {
        toolbarWrapper.isVisible = false
        val layeredPane = MyEditorLayeredComponentWrapper(splitter)
        component = layeredPane
        val toolbarGroup = toolbarWrapper.rightToolbar.actionGroup
        val toolbar = LayoutActionsFloatingToolbar(parentComponent = layeredPane, actionGroup = toolbarGroup, parentDisposable = host)
        layeredPane.add(splitter, JLayeredPane.DEFAULT_LAYER as Any)
        layeredPane.add(toolbar, JLayeredPane.POPUP_LAYER as Any)
        host.registerToolbarListeners(splitter, toolbar, host)
      }
      else {
        val panel = JPanel(BorderLayout())
        panel.add(splitter, BorderLayout.CENTER)
        panel.add(toolbarWrapper, BorderLayout.NORTH)
        component = panel
      }
    }

    fun handleLayoutChange(isVerticalSplit: Boolean) {
      toolbarWrapper.refresh()
      splitter.orientation = isVerticalSplit
      component.repaint()
    }
  }

  companion object {
    @JvmField
    internal val DEFAULT_LAYOUT_FOR_FILE: Key<Layout> = Key.create("TextEditorWithPreview.DefaultLayout")

    fun getEditorWithPreviewIcon(isVerticalSplit: Boolean): Icon {
      return if (isNewUI()) {
        if (isVerticalSplit) AllIcons.General.EditorPreviewVertical else AllIcons.General.LayoutEditorPreview
      }
      else {
        if (isVerticalSplit) AllIcons.Actions.PreviewDetailsVertically else AllIcons.Actions.PreviewDetails
      }
    }

    fun openPreviewForFile(project: Project, file: VirtualFile): Array<FileEditor> {
      file.putUserData(DEFAULT_LAYOUT_FOR_FILE, Layout.SHOW_PREVIEW)
      return FileEditorManager.getInstance(project).openFile(file, true)
    }

    fun getParentSplitEditor(fileEditor: FileEditor?): TextEditorWithPreview? {
      return if (fileEditor is TextEditorWithPreview) fileEditor else PARENT_SPLIT_EDITOR_KEY.get(fileEditor)
    }
  }

  open val textEditor: TextEditor
    get() = myEditor

  open val previewEditor: FileEditor
    get() = myPreview

  override fun getBackgroundHighlighter(): BackgroundEditorHighlighter? = myEditor.backgroundHighlighter

  override fun getCurrentLocation(): FileEditorLocation? = myEditor.currentLocation

  override fun getStructureViewBuilder(): StructureViewBuilder? = myEditor.structureViewBuilder

  override fun dispose() {
    Disposer.dispose(myEditor)
    Disposer.dispose(myPreview)
  }

  override fun selectNotify() {
    myEditor.selectNotify()
    myPreview.selectNotify()
  }

  override fun deselectNotify() {
    myEditor.deselectNotify()
    myPreview.deselectNotify()
  }

  protected open fun createSplitter(): JBSplitter = OnePixelSplitter()

  override fun getComponent(): JComponent = ui.value.component

  protected open val isShowFloatingToolbar: Boolean
    get() = Registry.`is`("ide.text.editor.with.preview.show.floating.toolbar")

  protected open val isShowActionsInTabs: Boolean
    get() = isNewUI() && getInstance().editorTabPlacement != UISettings.TABS_NONE

  private fun registerToolbarListeners(actualComponent: JComponent, toolbar: LayoutActionsFloatingToolbar, parentDisposable: Disposable) {
    addAwtListener(AWTEvent.MOUSE_MOTION_EVENT_MASK, parentDisposable, MyMouseListener(toolbar, parentDisposable))
    val actualEditor = UIUtil.findComponentOfType(actualComponent, EditorComponentImpl::class.java) ?: return
    val editorKeyListener = object : KeyAdapter() {
      override fun keyPressed(event: KeyEvent) {
        toolbar.scheduleHide()
      }
    }
    actualEditor.editor.contentComponent.addKeyListener(parentDisposable, editorKeyListener)
  }

  open fun isVerticalSplit(): Boolean = isVerticalSplit

  open fun setVerticalSplit(verticalSplit: Boolean) {
    isVerticalSplit = verticalSplit
    ui.value.splitter.orientation = verticalSplit
  }

  private fun createSplitEditorToolbar(targetComponentForActions: JComponent): SplitEditorToolbar {
    val leftToolbar = createToolbar()
    if (leftToolbar != null) {
      leftToolbar.targetComponent = targetComponentForActions
      leftToolbar.isReservePlaceAutoPopupIcon = false
    }

    val rightToolbar = createRightToolbar()
    rightToolbar.targetComponent = targetComponentForActions
    rightToolbar.isReservePlaceAutoPopupIcon = false

    return SplitEditorToolbar(leftToolbar, rightToolbar)
  }

  override fun setState(state: FileEditorState) {
    if (state is MyFileEditorState) {
      if (state.firstState != null) {
        myEditor.setState(state.firstState)
      }
      if (state.secondState != null) {
        myPreview.setState(state.secondState)
      }
      state.splitLayout?.let { splitLayout ->
        layout = state.splitLayout
        adjustEditorsVisibility(splitLayout)
        val ui = ui.valueIfInitialized
        ui?.toolbarWrapper?.refresh()
        if (ui != null && EDT.isCurrentThreadEdt()) {
          ui.component.repaint()

          val focusComponent = preferredFocusedComponent
          val focusOwner = IdeFocusManager.findInstance().focusOwner
          if (focusComponent != null && focusOwner != null && SwingUtilities.isDescendingFrom(focusOwner, getComponent())) {
            IdeFocusManager.findInstanceByComponent(focusComponent).requestFocus(focusComponent, true)
          }
        }
      }
      setVerticalSplit(state.isVerticalSplit)
    }
  }

  protected open fun onLayoutChange(oldValue: Layout?, newValue: Layout?) {}

  private fun adjustEditorsVisibility(layout: Layout) {
    myEditor.component.isVisible = layout == Layout.SHOW_EDITOR || layout == Layout.SHOW_EDITOR_AND_PREVIEW
    myPreview.component.isVisible = layout == Layout.SHOW_PREVIEW || layout == Layout.SHOW_EDITOR_AND_PREVIEW
  }

  fun getLayout(): Layout? = layout

  open fun setLayout(layout: Layout) {
    val oldLayout = this.layout
    this.layout = layout
    PropertiesComponent.getInstance().setValue(layoutPropertyName, layout.id, defaultLayout.id)
    adjustEditorsVisibility(layout)
    onLayoutChange(oldLayout, layout)
  }

  /**
   * To persist the proportion of the splitter for an individual editor,
   * override this method to generate a unique key.
   * From all the text editors that don't override this method, only a single proportion is stored.
   */
  protected open val splitterProportionKey: String
    get() = "TextEditorWithPreview.SplitterProportionKey"

  override fun getPreferredFocusedComponent(): JComponent? {
    return when (layout) {
      Layout.SHOW_EDITOR_AND_PREVIEW, Layout.SHOW_EDITOR -> myEditor.preferredFocusedComponent
      Layout.SHOW_PREVIEW -> myPreview.preferredFocusedComponent
      null -> null
    }
  }

  override fun getName(): String = name

  override fun getState(level: FileEditorStateLevel): FileEditorState {
    return MyFileEditorState(
      splitLayout = layout,
      firstState = myEditor.getState(level),
      secondState = myPreview.getState(level),
      isVerticalSplit = isVerticalSplit(),
    )
  }

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {
    myEditor.addPropertyChangeListener(listener)
    myPreview.addPropertyChangeListener(listener)

    val delegate = listenerGenerator.addListenerAndGetDelegate(listener)
    myEditor.addPropertyChangeListener(delegate)
    myPreview.addPropertyChangeListener(delegate)
  }

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {
    myEditor.removePropertyChangeListener(listener)
    myPreview.removePropertyChangeListener(listener)

    val delegate = listenerGenerator.removeListenerAndGetDelegate(listener)
    if (delegate != null) {
      myEditor.removePropertyChangeListener(delegate)
      myPreview.removePropertyChangeListener(delegate)
    }
  }

  class MyFileEditorState(
    val splitLayout: Layout?,
    val firstState: FileEditorState?,
    val secondState: FileEditorState?,
    val isVerticalSplit: Boolean,
  ) : FileEditorState {
    @Deprecated("Use {@link #MyFileEditorState(Layout, FileEditorState, FileEditorState, boolean)}")
    constructor(layout: Layout?, firstState: FileEditorState?, secondState: FileEditorState?) : this(layout, firstState, secondState, false)

    override fun canBeMergedWith(otherState: FileEditorState, level: FileEditorStateLevel): Boolean {
      return otherState is MyFileEditorState
             && (firstState == null || firstState.canBeMergedWith(otherState.firstState!!, level))
             && (secondState == null || secondState.canBeMergedWith(otherState.secondState!!, level))
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other == null || javaClass != other.javaClass) return false
      val state = other as MyFileEditorState
      return splitLayout == state.splitLayout &&
             firstState == state.firstState &&
             secondState == state.secondState
    }

    override fun hashCode(): Int {
      var result = splitLayout?.hashCode() ?: 0
      result = 31 * result + (firstState?.hashCode() ?: 0)
      result = 31 * result + (secondState?.hashCode() ?: 0)
      result = 31 * result + isVerticalSplit.hashCode()
      return result
    }
  }

  override fun isModified(): Boolean = myEditor.isModified || myPreview.isModified

  override fun isValid(): Boolean = myEditor.isValid && myPreview.isValid

  protected open fun createToolbar(): ActionToolbar? {
    val actionGroup = createLeftToolbarActionGroup() ?: return null
    return ActionManager.getInstance().createActionToolbar(ActionPlaces.TEXT_EDITOR_WITH_PREVIEW, actionGroup, true)
  }

  protected open fun createLeftToolbarActionGroup(): ActionGroup? = null

  protected open fun createRightToolbar(): ActionToolbar {
    val viewActions = createViewActionGroup().run {
      (this as? DefaultActionGroup)?.getChildren(ActionManager.getInstance()) ?: getChildren(null)
    }
    val viewActionsGroup = ConditionalActionGroup(viewActions) { !isShowActionsInTabs }
    val group = createRightToolbarActionGroup()
    val rightToolbarActions = if (group == null) {
      viewActionsGroup
    }
    else {
      DefaultActionGroup(group, Separator.create(), viewActionsGroup)
    }
    return ActionManager.getInstance().createActionToolbar(ActionPlaces.TEXT_EDITOR_WITH_PREVIEW, rightToolbarActions, true)
  }

  protected open fun createViewActionGroup(): ActionGroup {
    return DefaultActionGroup(listOf(
      showEditorAction,
      showEditorAndPreviewAction,
      showPreviewAction,
    ))
  }

  protected open fun createRightToolbarActionGroup(): ActionGroup? = null

  override fun getTabActions(): ActionGroup = ConditionalActionGroup(actions = createTabActions()) { isShowActionsInTabs }

  protected open fun createTabActions(): Array<AnAction> {
    return createViewActionGroup().run {
      (this as? DefaultActionGroup)?.getChildren(ActionManager.getInstance()) ?: getChildren(null)
    }
  }

  protected open val showEditorAction: ToggleAction
    get() = getAction("TextEditorWithPreview.Layout.EditorOnly")!! as ToggleAction

  protected open val showEditorAndPreviewAction: ToggleAction
    get() = getAction("TextEditorWithPreview.Layout.EditorAndPreview")!! as ToggleAction

  protected open val showPreviewAction: ToggleAction
    get() = getAction("TextEditorWithPreview.Layout.PreviewOnly")!! as ToggleAction

  enum class Layout(val id: String, private val myName: Supplier<@Nls String>) {
    SHOW_EDITOR("Editor only", IdeBundle.messagePointer("tab.title.editor.only")),
    SHOW_PREVIEW("Preview only", IdeBundle.messagePointer("tab.title.preview.only")),
    SHOW_EDITOR_AND_PREVIEW("Editor and Preview", IdeBundle.messagePointer("tab.title.editor.and.preview"));

    fun getName(): @Nls String = myName.get()

    fun getIcon(editor: TextEditorWithPreview?): Icon {
      return when {
        this == SHOW_EDITOR -> AllIcons.General.LayoutEditorOnly
        this == SHOW_PREVIEW -> AllIcons.General.LayoutPreviewOnly
        else -> getEditorWithPreviewIcon(isVerticalSplit = editor != null && editor.isVerticalSplit)
      }
    }

    companion object {
      @JvmStatic
      fun fromId(id: String?, defaultValue: Layout): Layout = entries.firstOrNull { it.id == id } ?: defaultValue
    }
  }

  private val layoutPropertyName: String
    get() = "${name}Layout"

  override fun getFile(): VirtualFile? = myEditor.file

  override fun getEditor(): Editor = myEditor.editor

  override fun canNavigateTo(navigatable: Navigatable): Boolean = myEditor.canNavigateTo(navigatable)

  override fun navigateTo(navigatable: Navigatable) {
    myEditor.navigateTo(navigatable)
  }

  protected fun handleLayoutChange(isVerticalSplit: Boolean) {
    if (this.isVerticalSplit == isVerticalSplit) {
      return
    }

    this.isVerticalSplit = isVerticalSplit
    ui.valueIfInitialized?.handleLayoutChange(isVerticalSplit)
  }

  private inner class MyMouseListener(
    private val toolbar: LayoutActionsFloatingToolbar,
    parentDisposable: Disposable,
  ) : AWTEventListener {
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, parentDisposable)

    override fun eventDispatched(event: AWTEvent) {
      val isMouseInsideComponent = component.mousePosition != null
      val isMouseOutsideToolbar = toolbar.mousePosition == null
      if (isMouseInsideComponent) {
        alarm.cancelAllRequests()
        toolbar.scheduleShow()
        if (isMouseOutsideToolbar) {
          alarm.addRequest({ toolbar.scheduleHide() }, 1400)
        }
      }
      else if (isMouseOutsideToolbar) {
        toolbar.scheduleHide()
      }
    }
  }
}

private class MyEditorLayeredComponentWrapper(private val editorComponent: JComponent) : JBLayeredPane() {
  @Suppress("ConstPropertyName")
  companion object {
    const val toolbarTopPadding: Int = 25
    const val toolbarRightPadding: Int = 20
  }

  override fun doLayout() {
    val components = components
    val bounds = bounds
    for (component in components) {
      if (component === editorComponent) {
        component.setBounds(0, 0, bounds.width, bounds.height)
      }
      else {
        val preferredComponentSize = component.preferredSize
        var x = 0
        var y = 0
        if (component is LayoutActionsFloatingToolbar) {
          x = bounds.width - preferredComponentSize.width - toolbarRightPadding
          y = toolbarTopPadding
        }
        component.setBounds(x, y, preferredComponentSize.width, preferredComponentSize.height)
      }
    }
  }

  override fun getPreferredSize(): Dimension = editorComponent.preferredSize
}

private class ConditionalActionGroup(private val actions: Array<AnAction>, private val condition: () -> Boolean) : ActionGroup() {
  override fun getChildren(e: AnActionEvent?): Array<AnAction> = if (condition()) actions else EMPTY_ARRAY
}

private class MyListenersMultimap(
  private val textEditorWithPreview: TextEditorWithPreview,
) {
  private val map = HashMap<PropertyChangeListener, Pair<Int, DoublingEventListenerDelegate>>()

  fun addListenerAndGetDelegate(listener: PropertyChangeListener): DoublingEventListenerDelegate {
    val oldPair = map.get(listener)
    if (oldPair == null) {
      val v = DoublingEventListenerDelegate(listener, textEditorWithPreview)
      map.put(listener, 1 to v)
      return v
    }
    else {
      val v = oldPair.second
      map.put(listener, oldPair.first + 1 to v)
      return v
    }
  }

  fun removeListenerAndGetDelegate(listener: PropertyChangeListener): DoublingEventListenerDelegate? {
    val oldPair = map.get(listener) ?: return null
    if (oldPair.first == 1) {
      map.remove(listener)
    }
    else {
      map.put(listener, oldPair.first - 1 to oldPair.second)
    }
    return oldPair.second
  }
}

private class DoublingEventListenerDelegate(
  private val delegate: PropertyChangeListener,
  private val textEditorWithPreview: TextEditorWithPreview,
) : PropertyChangeListener {
  override fun propertyChange(event: PropertyChangeEvent) {
    delegate.propertyChange(PropertyChangeEvent(textEditorWithPreview, event.propertyName, event.oldValue, event.newValue))
  }
}