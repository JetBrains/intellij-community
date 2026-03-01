// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.frontend

import com.intellij.CommonBundle
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.DefaultTreeExpander
import com.intellij.ide.IdeBundle
import com.intellij.ide.TreeExpander
import com.intellij.ide.dnd.aware.DnDAwareTree
import com.intellij.ide.structureView.newStructureView.StructurePopup
import com.intellij.ide.structureView.newStructureView.StructurePopupTestExt
import com.intellij.ide.structureView.newStructureView.TreeActionsOwner
import com.intellij.ide.ui.UISettingsListener
import com.intellij.ide.util.FileStructurePopupListener
import com.intellij.ide.util.FileStructurePopupLoadingStateUpdater
import com.intellij.ide.util.FileStructurePopupTimeTracker
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.lang.LangBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.actionSystem.UiDataProvider.Companion.wrapComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.structureView.frontend.uiModel.CheckboxTreeAction
import com.intellij.platform.structureView.frontend.uiModel.StructureTreeAction
import com.intellij.platform.structureView.frontend.uiModel.StructureUiModel
import com.intellij.platform.structureView.frontend.uiModel.StructureUiModelListener
import com.intellij.platform.structureView.impl.StructureViewScopeHolder
import com.intellij.platform.structureView.impl.uiModel.StructureUiTreeElement
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.ClickListener
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.PlaceProvider
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.SpeedSearchComparator
import com.intellij.ui.SpeedSearchObjectWithWeight
import com.intellij.ui.SpinningProgressIcon
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.popup.PopupUpdateProcessor
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.ui.speedSearch.ElementFilter
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.tree.TreeVisitor
import com.intellij.ui.tree.TreeVisitor.VisitThread
import com.intellij.ui.treeStructure.Tree
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure.FilteringNode
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SpeedSearchAdvertiser
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.asDeferred
import java.awt.BorderLayout
import java.awt.GridLayout
import java.awt.Point
import java.awt.Rectangle
import java.awt.datatransfer.DataFlavor
import java.awt.event.ActionEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.awt.event.MouseEvent
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.TransferHandler
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.nanoseconds

/**
 * @author Konstantin Bulenkov
 */
class FileStructurePopup(
  private val myProject: Project,
  private val myFileEditor: FileEditor,
  private val myModel: StructureUiModel,
) : Disposable, TreeActionsOwner, StructurePopup, StructurePopupTestExt {
  private var myPopup: JBPopup? = null
  private var myTitle: @NlsContexts.PopupTitle String? = null

  private val tree: Tree
  private val myTreeStructure: StructureViewTreeStructure
  private val myFilteringStructure: FilteringTreeStructure

  private val myAsyncTreeModel: AsyncTreeModel
  private val myStructureTreeModel: StructureTreeModel<*>
  private val mySpeedSearch: MyTreeSpeedSearch

  private val myCheckBoxes = hashMapOf<String, JBCheckBox>()
  private val myCheckBoxesPanel = Wrapper()
  private val myAutoClicked = mutableListOf<JBCheckBox>()
  private var myTestSearchFilter: String? = null
  private val myTriggeredCheckboxes = mutableListOf<Pair<String, JBCheckBox>>()
  private val myTreeExpander: TreeExpander
  private val mySorters = mutableListOf<AnAction>()

  private val mutex = Mutex()

  private val cs = StructureViewScopeHolder.getInstance(myProject).cs.childScope("$this scope")

  private val constructorCallTime = System.nanoTime()
  private var showTime: Long = 0

  private var myCanClose = true

  init {
    //Stop code analyzer to speed up the EDT
    DaemonCodeAnalyzer.getInstance(myProject).disableUpdateByTimer(this)
    myTreeStructure = object : StructureViewTreeStructure(myProject, myModel) {
      override fun rebuildTree() {
        ProgressManager.getInstance().computePrioritized(ThrowableComputable {
          super.rebuildTree()
          myFilteringStructure.rebuild()
        })
      }

      override fun isToBuildChildrenInBackground(element: Any): Boolean {
        return rootElement === element
      }

      @NonNls
      override fun toString(): @NonNls String {
        return "structure view tree structure(model=$myModel)"
      }
    }

    val filter = FileStructurePopupFilter()
    myFilteringStructure = FilteringTreeStructure(filter, myTreeStructure, false)

    myStructureTreeModel = StructureTreeModel<FilteringTreeStructure>(myFilteringStructure, this)
    myAsyncTreeModel = AsyncTreeModel(myStructureTreeModel, this)
    tree = MyTree(myAsyncTreeModel)
    tree.model.addTreeModelListener(StructureViewExpandListener(tree, myModel))
    PopupUtil.applyNewUIBackground(tree)
    tree.getAccessibleContext().setAccessibleName(LangBundle.message("file.structure.tree.accessible.name"))
    Disposer.register(this, myModel)

    val updaterInstalled = AtomicBoolean(false)
    myModel.addListener(object : StructureUiModelListener {
      override fun onTreeChanged() {
        if (myModel.dto == null) {
          tree.emptyText.text = LangBundle.message("panel.empty.text.no.structure")
          return
        }

        cs.launch(Dispatchers.UI) {
          rebuild(false)

          if (updaterInstalled.compareAndSet(false, true)) {
            myProject.service<FileStructurePopupLoadingStateUpdater>()
              .installUpdater({ delayMillis -> installUpdater(delayMillis) }, myProject)
          }

          @Suppress("TestOnlyProblems")
          for (listener in myTestListeners) {
            listener.rebuildAfterTreeChangeFinished()
          }
        }
      }

      override fun onActionsChanged() {
        updateActions()
      }
    })
    tree.setCellRenderer(NodeRenderer())
    myProject.getMessageBus()
      .connect(this)
      .subscribe<UISettingsListener>(UISettingsListener.TOPIC, UISettingsListener { cs.launch(Dispatchers.UI) { rebuild(false) } })

    tree.setTransferHandler(object : TransferHandler() {
      override fun importData(support: TransferSupport): Boolean {
        val s = CopyPasteManager.getInstance().getContents<String?>(DataFlavor.stringFlavor)
        if (s != null && !mySpeedSearch.isPopupActive) {
          mySpeedSearch.showPopup(s)
          return true
        }
        return false
      }
    })

    mySpeedSearch = MyTreeSpeedSearch()
    mySpeedSearch.setupListeners()
    mySpeedSearch.comparator = SpeedSearchComparator(false, true, " ()")

    myTreeExpander = DefaultTreeExpander(tree)

    TreeUtil.installActions(tree)
  }

  override fun show() {
    val panel = createCenterPanel()
    tree.addTreeSelectionListener {
      if (myPopup!!.isVisible()) {
        val updateProcessor = myPopup!!.getUserData(PopupUpdateProcessor::class.java)
        if (updateProcessor != null) {
          val node = selectedNode
          updateProcessor.updatePopup(node)
        }
      }
    }

    myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, tree)
      .setTitle(myTitle)
      .setResizable(true)
      .setModalContext(false)
      .setFocusable(true)
      .setRequestFocus(true)
      .setMovable(true)
      .setBelongsToGlobalPopupStack(true) //.setCancelOnClickOutside(false) //for debug and snapshots
      .setCancelOnOtherWindowOpen(true)
      .setCancelKeyEnabled(false)
      .setDimensionServiceKey(myProject,
                              dimensionServiceKey, true)
      .setCancelCallback {
        val listener = myProject.getMessageBus().syncPublisher<FileStructurePopupListener>(FileStructurePopupListener.TOPIC)
        listener.stateChanged(false)
        myCanClose
      }
      .setAdvertiser(SpeedSearchAdvertiser().addSpeedSearchAdvertisement())
      .createPopup()

    Disposer.register(myPopup!!, this)
    tree.emptyText.text = CommonBundle.getLoadingTreeNodeText()

    myPopup!!.showCenteredInCurrentWindow(myProject)
    IdeFocusManager.getInstance(myProject).requestFocus(tree, true)

    showTime = System.nanoTime()
    LOG.debug {
      val time = (showTime - constructorCallTime).nanoseconds
      "show time for the popup: $time ns, ${time.inWholeMilliseconds} ms"
    }
  }

  private fun installUpdater(delayMillis: Int) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return
    }

    cs.launch(Dispatchers.UI) {
      var previousFilter = ""

      flow {
        while (true) {
          val currentPrefix = mySpeedSearch.enteredPrefix ?: ""
          emit(currentPrefix)
          delay(delayMillis.toLong())
        }
      }
        .distinctUntilChanged()
        .collectLatest { prefix ->
          withContext(Dispatchers.UI) {
            tree.emptyText.text = if (StringUtil.isEmpty(prefix)) {
              LangBundle.message("status.text.structure.empty")
            }
            else {
              "'$prefix' ${LangBundle.message("status.text.structure.empty.not.found")}"
            }

            if (previousFilter != prefix) {
              val isBackspace = prefix.length < previousFilter.length
              previousFilter = prefix

              rebuild(true)

              TreeUtil.promiseExpandAll(tree)
              if (isBackspace && handleBackspace(prefix)) {
                return@withContext
              }
              if (myFilteringStructure.rootElement.getChildren().size == 0) {
                for (box in myCheckBoxes.values) {
                  if (!box.isSelected) {
                    myAutoClicked.add(box)
                    myTriggeredCheckboxes.addFirst(prefix to box)
                    box.doClick()
                    previousFilter = ""
                    break
                  }
                }
              }
            }
          }
        }
    }
  }

  private fun handleBackspace(filter: String): Boolean {
    var clicked = false
    val iterator = myTriggeredCheckboxes.iterator()
    while (iterator.hasNext()) {
      val next = iterator.next()
      if (next.first.length < filter.length) break

      iterator.remove()
      next.second.doClick()
      clicked = true
    }
    return clicked
  }

  suspend fun select(element: StructureUiTreeElement): TreePath? {
    val visitor: TreeVisitor = object : TreeVisitor {
      override fun visitThread() = VisitThread.BGT

      override fun visit(path: TreePath): TreeVisitor.Action {
        val last = path.lastPathComponent
        val value = unwrapTreeElement(last)?.value

        return if (value == element) {
          TreeVisitor.Action.INTERRUPT
        }
        else {
          TreeVisitor.Action.CONTINUE
        }
      }
    }

    return selectInner(visitor) ?: element.parent?.let { select(it) }
  }

  suspend fun select(element: StructureViewTreeElement): TreePath? {
    val visitor: TreeVisitor = object : TreeVisitor {
      override fun visitThread() = VisitThread.BGT

      override fun visit(path: TreePath): TreeVisitor.Action {
        val last = path.lastPathComponent
        val treeElement = unwrapTreeElement(last)

        return if (treeElement == element) {
          TreeVisitor.Action.INTERRUPT
        }
        else if (treeElement != null && !treeElement.isAncestorOf(element)) {
          TreeVisitor.Action.SKIP_CHILDREN
        }
        else {
          TreeVisitor.Action.CONTINUE
        }
      }

      private fun StructureViewTreeElement.isAncestorOf(child: StructureViewTreeElement): Boolean {
        var element = child.parent
        while (element != null) {
          if (element == this) return true
          element = element.parent
        }
        return false
      }
    }

    return selectInner(visitor)
  }

  private suspend fun selectInner(visitor: TreeVisitor): TreePath? {
    val result = myAsyncTreeModel.accept(visitor).asDeferred().await()
    if (result == null) return null

    withContext(Dispatchers.UI) {
      tree.expandPath(result)
      TreeUtil.selectPath(tree, result)
      TreeUtil.ensureSelection(tree)
    }
    return result
  }

  override fun dispose() {
    cs.cancel()
    if (showTime != 0L) {
      FileStructurePopupTimeTracker.logShowTime(System.nanoTime() - showTime)
    }
    FileStructurePopupTimeTracker.logPopupLifeTime(System.nanoTime() - constructorCallTime)
  }

  fun createCenterPanel(): JComponent {
    val panel = JPanel(BorderLayout())
    panel.preferredSize = JBUI.size(540, 500)

    val f4 = ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE).shortcutSet.getShortcuts()
    val enter = CustomShortcutSet.fromString("ENTER").shortcuts
    val shortcutSet = CustomShortcutSet(*(f4 + enter))
    NavigateSelectedElementAction().registerCustomShortcutSet(shortcutSet, panel)

    DumbAwareAction.create {
      if (mySpeedSearch.isPopupActive) {
        mySpeedSearch.hidePopup()
      }
      else {
        myPopup!!.cancel()
      }
    }.registerCustomShortcutSet(CustomShortcutSet.fromString("ESCAPE"), tree)
    object : ClickListener() {
      override fun onClick(e: MouseEvent, clickCount: Int): Boolean {
        val path = tree.getClosestPathForLocation(e.getX(), e.getY())
        val bounds = if (path == null) null else tree.getPathBounds(path)
        if (bounds == null || bounds.x > e.getX() || bounds.y > e.getY() || bounds.y + bounds.height < e.getY()) return false
        navigateSelectedElement()
        return true
      }
    }.installOn(tree)


    val topPanel = JPanel(BorderLayout())

    val rightToolbarPanel = JPanel(BorderLayout())
    rightToolbarPanel.setBackground(JBUI.CurrentTheme.Popup.toolbarPanelColor())
    rightToolbarPanel.setBorder(JBUI.Borders.empty())

    rightToolbarPanel.add(createUpdatePendingIndicator(myModel), BorderLayout.WEST)

    rightToolbarPanel.add(createSettingsButton(), BorderLayout.EAST)

    topPanel.add(rightToolbarPanel, BorderLayout.EAST)
    topPanel.add(myCheckBoxesPanel, BorderLayout.WEST)

    updateActions()

    topPanel.setBackground(JBUI.CurrentTheme.Popup.toolbarPanelColor())
    topPanel.setBorder(JBUI.Borders.emptyLeft(UIUtil.DEFAULT_HGAP))

    panel.add(topPanel, BorderLayout.NORTH)
    val scrollPane = ScrollPaneFactory.createScrollPane(tree)
    scrollPane.setBorder(IdeBorderFactory.createBorder(JBUI.CurrentTheme.Popup.toolbarBorderColor(), SideBorder.TOP or SideBorder.BOTTOM))
    panel.add(scrollPane, BorderLayout.CENTER)
    panel.addFocusListener(object : FocusAdapter() {
      override fun focusLost(e: FocusEvent?) {
        myPopup!!.cancel()
      }
    })

    return wrapComponent(panel, UiDataProvider { uiDataSnapshot(it) })
  }

  private fun createUpdatePendingIndicator(treeModel: StructureUiModel): JComponent {
    val component = JLabel(SpinningProgressIcon())
    component.isVisible = false

    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      treeModel.getUpdatePendingFlow().collect {
        withContext(Dispatchers.UI) {
          component.isVisible = it
        }
      }
    }

    return component
  }

  private fun updateActions() {
    val actions = myModel.getActions()
    val sorters = actions.filter { it.actionType == StructureTreeAction.Type.SORTER }
    val checkboxActions = actions.filterIsInstance<CheckboxTreeAction>()

    mySorters.clear()
    mySorters.addAll(sorters.map { MyStructureTreeAction(it, myModel) })

    myAutoClicked.clear()
    myTriggeredCheckboxes.clear()


    val checkBoxCount = checkboxActions.size

    val cols = if (checkBoxCount > 0 && checkBoxCount % 4 == 0) checkBoxCount / 2 else 3
    val singleRow = checkBoxCount <= cols

    val chkPanel = JPanel(GridLayout(0, cols, scale(UIUtil.DEFAULT_HGAP), 0))
    chkPanel.setOpaque(false)

    for (filter in checkboxActions) {
      addCheckbox(chkPanel, filter)
    }

    chkPanel.addHierarchyListener(
      HierarchyListener { event ->
        if ((event.getChangeFlags() and HierarchyEvent.PARENT_CHANGED.toLong()) != 0L && event.getChanged() === chkPanel) {
          val topPanel = myCheckBoxesPanel.getParent()
          val prefSize = topPanel.preferredSize
          if (singleRow) {
            prefSize.height = JBUI.CurrentTheme.Popup.toolbarHeight()
          }
          topPanel.setPreferredSize(prefSize)
        }
      })
    myCheckBoxesPanel.setContent(chkPanel)
  }

  internal class MyStructureTreeAction(action: StructureTreeAction, model: StructureUiModel) : StructureTreeActionWrapper(action, model)

  private fun uiDataSnapshot(sink: DataSink) {
    sink[CommonDataKeys.PROJECT] = myProject
    sink[PlatformCoreDataKeys.FILE_EDITOR] = myFileEditor
    if (myFileEditor is TextEditor) {
      sink[OpenFileDescriptor.NAVIGATE_IN_EDITOR] = myFileEditor.getEditor()
    }
    sink[LangDataKeys.POSITION_ADJUSTER_POPUP] = myPopup
    sink[PlatformDataKeys.TREE_EXPANDER] = myTreeExpander
  }

  private fun createSettingsButton(): JComponent {
    val label = JLabel(AllIcons.General.GearPlain)
    label.setBorder(JBUI.Borders.empty(0, 4))
    label.setHorizontalAlignment(SwingConstants.RIGHT)
    label.setVerticalAlignment(SwingConstants.CENTER)
    label.getAccessibleContext().setAccessibleName(LangBundle.message("file.structure.settings.accessible.name"))

    object : ClickListener() {
      override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
        val group = DefaultActionGroup()
        if (!mySorters.isEmpty()) {
          group.addAll(mySorters)
          group.addSeparator()
        }

        //addGroupers(group);
        //addFilters(group);
        group.add(ToggleNarrowDownAction())

        val dataManager = DataManager.getInstance()
        val popup = JBPopupFactory.getInstance().createActionGroupPopup(
          null, group, dataManager.getDataContext(label), JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false)
        popup.addListener(object : JBPopupListener {
          override fun onClosed(event: LightweightWindowEvent) {
            myCanClose = true
          }
        })
        myCanClose = false
        popup.showUnderneathOf(label)
        return true
      }
    }.installOn(label)
    return label
  }

  private val selectedNode: StructureViewTreeElement?
    get() {
      val path = tree.selectionPath
      return unwrapTreeElement(if (path == null) null else path.lastPathComponent)
    }

  private fun navigateSelectedElement() {
    val selectedNode = selectedNode
    if (ApplicationManager.getApplication().isInternal()) {
      val enteredPrefix = mySpeedSearch.enteredPrefix
      val itemText = if (selectedNode != null) getSpeedSearchText(selectedNode) else null
      if (StringUtil.isNotEmpty(enteredPrefix) && StringUtil.isNotEmpty(itemText)) {
        LOG.info("Chosen in file structure popup by prefix '$enteredPrefix': '$itemText'")
      }
    }

    myModel.navigateTo(selectedNode?.value).thenAccept {
      if (it) {
        cs.launch(Dispatchers.UI) {
          myPopup!!.cancel()
        }
      }
    }
  }

  private fun addCheckbox(panel: JPanel, action: CheckboxTreeAction) {
    var text = action.checkboxText

    val shortcuts = extractShortcutFor(action)


    val checkBox = JBCheckBox()
    checkBox.setOpaque(false)
    UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, checkBox)

    val selected = myModel.isActionEnabled(action)
    checkBox.setSelected(selected)
    val isRevertedStructureFilter = action.isReverted
    checkBox.addActionListener {
      val state = checkBox.isSelected
      myModel.setActionEnabled(action, isRevertedStructureFilter != state, myAutoClicked.contains(checkBox))
      cs.launch(Dispatchers.UI) {
        rebuild(false)
        if (mySpeedSearch.isPopupActive) {
          mySpeedSearch.refreshSelection()
        }
      }
    }
    checkBox.setFocusable(false)

    if (shortcuts.isNotEmpty()) {
      text += " (" + KeymapUtil.getShortcutText(shortcuts[0]) + ")"
      DumbAwareAction.create {
        checkBox.doClick()
      }.registerCustomShortcutSet(CustomShortcutSet(*shortcuts), tree)
    }
    checkBox.setText(text)
    panel.add(checkBox)

    myCheckBoxes[action.name] = checkBox
  }

  private suspend fun rebuild(refilterOnly: Boolean) {
    withContext(Dispatchers.UI) {
      val selection = tree.getSelectionPaths()?.firstNotNullOf { unwrapTreeElement(it.lastPathComponent) } ?: myModel.editorSelection.value
      withContext(Dispatchers.Default) {
        mutex.withLock {
          rebuildAndSelect(refilterOnly, selection, null)
        }
      }
    }
  }

  @RequiresBackgroundThread
  private suspend fun rebuildAndSelect(refilterOnly: Boolean, selection: Any?, rebuildStartTime: Long?): TreePath? {
    check(mutex.isLocked)

    var rebuildStartTime = rebuildStartTime
    if (rebuildStartTime == null) {
      rebuildStartTime = System.nanoTime()
    }

    val finalLastRebuildStartTime = rebuildStartTime

    val treePath = coroutineScope {
      suspendCancellableCoroutine { continuation ->
        myStructureTreeModel.invoker.invoke {
          if (refilterOnly) {
            myFilteringStructure.rebuild()
            myFilteringStructure.refilter()
            myStructureTreeModel.invalidateAsync().thenRun {
              launch {
                try {
                  val result = when (selection) {
                    is StructureViewTreeElement -> select(selection)
                    is StructureUiTreeElement -> select(selection)
                    else -> {
                      myAsyncTreeModel.accept { TreeVisitor.Action.CONTINUE }.asDeferred().await()
                      null
                    }
                  }
                  withContext(Dispatchers.UI) {
                    TreeUtil.expand(this@FileStructurePopup.tree, myModel.minimumAutoExpandDepth)
                    TreeUtil.ensureSelection(this@FileStructurePopup.tree)
                    mySpeedSearch.refreshSelection()
                    LOG.debug {
                      val time = (System.nanoTime() - showTime).nanoseconds
                      "rebuild time: $time ns, ${time.inWholeMilliseconds} ms"
                    }
                    FileStructurePopupTimeTracker.logRebuildTime(System.nanoTime() - finalLastRebuildStartTime)
                  }
                  continuation.resume(result)
                }
                catch (e: Exception) {
                  mySpeedSearch.refreshSelection()
                  continuation.resumeWithException(e)
                }
              }
            }
          }
          else {
            myTreeStructure.rebuildTree()
            myStructureTreeModel.invalidateAsync().thenRun {
              launch {
                try {
                  val result = rebuildAndSelect(true, selection ?: myModel.editorSelection.value, finalLastRebuildStartTime)
                  continuation.resume(result)
                }
                catch (e: Exception) {
                  continuation.resumeWithException(e)
                }
              }
            }
          }
        }.onError {
          continuation.resumeWithException(it)
        }
      }
    }

    return treePath
  }

  override fun setTitle(title: @NlsContexts.PopupTitle String) {
    myTitle = title
  }

  @TestOnly
  @ApiStatus.Internal
  fun isUpdatePending(): Boolean {
    return myModel.getUpdatePendingFlow().value
  }

  @TestOnly
  @ApiStatus.Internal
  override fun getSpeedSearch(): TreeSpeedSearch {
    return mySpeedSearch
  }

  @TestOnly
  @ApiStatus.Internal
  override fun setSearchFilterForTests(filter: String?) {
    myTestSearchFilter = filter
  }

  @TestOnly
  @ApiStatus.Internal
  override fun setTreeActionState(actionName: String, state: Boolean) {
    val checkBox = myCheckBoxes[actionName]
    if (checkBox != null) {
      checkBox.setSelected(state)
      for (listener in checkBox.actionListeners) {
        listener.actionPerformed(ActionEvent(this, 1, ""))
      }
    }
    else {
      LOG.error("Action '$actionName' not found in FileStructurePopup")
    }
  }

  @TestOnly
  @ApiStatus.Internal
  override fun initUi() {
    createCenterPanel()
  }

  @TestOnly
  @ApiStatus.Internal
  override fun getTree(): Tree {
    return tree
  }

  @TestOnly
  private val myTestListeners = CopyOnWriteArrayList<StructurePopupListener>()

  @ApiStatus.Internal
  @TestOnly
  suspend fun waitUpdateFinished() {
    val listener = object : StructurePopupListener {
      var flow = MutableStateFlow(false)
      override fun rebuildAfterTreeChangeFinished() {
        flow.value = true
      }
    }
    addTestListener(listener)

    if (!isUpdatePending()) {
      removeTestListener(listener)
      return
    }

    listener.flow.firstOrNull { it }

    removeTestListener(listener)
  }

  @ApiStatus.Internal
  @TestOnly
  fun addTestListener(listener: StructurePopupListener) {
    myTestListeners.add(listener)
  }

  @ApiStatus.Internal
  @TestOnly
  fun removeTestListener(listener: StructurePopupListener) {
    myTestListeners.remove(listener)
  }

  @ApiStatus.Internal
  @TestOnly
  suspend fun rebuildAndUpdate() {
    rebuild(false)
    val visitor = TreeVisitor {
      TreeUtil.getLastUserObject(AbstractTreeNode::class.java, it)?.update()
      TreeVisitor.Action.CONTINUE
    }
    myAsyncTreeModel.accept(visitor).asDeferred().await()
  }

  @ApiStatus.Internal
  @TestOnly
  suspend fun selectCurrent() {
    val id = myModel.getNewSelection()
    val visitor: TreeVisitor = object : TreeVisitor {
      override fun visitThread() = VisitThread.BGT

      override fun visit(path: TreePath): TreeVisitor.Action {
        val last = path.lastPathComponent
        val value = unwrapTreeElement(last)?.value

        return if (value?.id == id) {
          TreeVisitor.Action.INTERRUPT
        }
        else {
          TreeVisitor.Action.CONTINUE
        }
      }
    }
    selectInner(visitor)
  }

  override fun setActionActive(name: String?, state: Boolean) {
  }

  override fun isActionActive(name: String?): Boolean {
    return false
  }

  private inner class FileStructurePopupFilter : ElementFilter<Any?> {
    private var myLastFilter: String? = null
    private val isUnitTest = ApplicationManager.getApplication().isUnitTestMode()

    override fun shouldBeShowing(value: Any?): Boolean {
      if (!isShouldNarrowDown) return true

      val filter: String? = searchPrefix
      if (!StringUtil.equals(myLastFilter, filter)) {
        myLastFilter = filter
      }
      if (filter != null) {
        val text: String? = if (value is StructureViewTreeElement) getSpeedSearchText(value) else null
        if (text == null) return false

        return matches(filter, text)
      }
      return true
    }

    fun matches(filter: String, text: String): Boolean {
      return (isUnitTest || mySpeedSearch.isPopupActive) &&
             StringUtil.isNotEmpty(filter) && mySpeedSearch.comparator.matchingFragments(filter, text) != null
    }
  }

  private val searchPrefix: String?
    get() {
      if (ApplicationManager.getApplication().isUnitTestMode()) return myTestSearchFilter

      return if (!StringUtil.isEmpty(mySpeedSearch.enteredPrefix)) mySpeedSearch.enteredPrefix else null
    }

  private inner class MyTreeSpeedSearch : TreeSpeedSearch(tree, true, null, {
    val element = unwrapTreeElement(it)
    if (element != null) getSpeedSearchText(element) else null
  }) {
    @Volatile
    private var myPopupVisible = false

    override fun showPopup(searchText: String?) {
      super.showPopup(searchText)
      myPopupVisible = true
    }

    override fun hidePopup() {
      super.hidePopup()
      myPopupVisible = false
    }

    override fun isPopupActive(): Boolean {
      return myPopupVisible
    }

    override fun getComponentLocationOnScreen(): Point? {
      return myPopup!!.getContent().locationOnScreen
    }

    override fun getComponentVisibleRect(): Rectangle? {
      return myPopup!!.getContent().getVisibleRect()
    }

    override fun findElement(s: String): Any? {
      val elements = SpeedSearchObjectWithWeight.findElement(s, this)
      val best = elements.firstOrNull()
      if (best == null) return null
      val initial = myModel.editorSelection.value
      if (initial != null) {
        // find children of the initial element
        val bestForParent: SpeedSearchObjectWithWeight? = find(initial, elements) { parent: StructureUiTreeElement, path: TreePath? ->
          isParent(parent, path)
        }
        if (bestForParent != null) return bestForParent.node
        // find siblings of the initial element
        val parent = initial.parent
        if (parent != null) {
          val bestSibling: SpeedSearchObjectWithWeight? = find(parent, elements) { parent: StructureUiTreeElement, path: TreePath? ->
            isParent(parent, path)
          }
          if (bestSibling != null) return bestSibling.node
        }
        // find grand children of the initial element
        val bestForAncestor: SpeedSearchObjectWithWeight? = find(initial, elements) { ancestor: StructureUiTreeElement, path: TreePath? ->
          isAncestor(ancestor, path)
        }
        if (bestForAncestor != null) return bestForAncestor.node
      }
      return best.node
    }
  }

  internal class MyTree(treeModel: TreeModel?) : DnDAwareTree(treeModel), PlaceProvider {
    init {
      setRootVisible(false)
      setShowsRootHandles(true)
    }

    override fun getPlace(): String {
      return ActionPlaces.STRUCTURE_VIEW_POPUP
    }
  }

  private inner class NavigateSelectedElementAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
      navigateSelectedElement()
    }
  }

  private fun unwrapTreeElement(o: Any?): StructureViewTreeElement? {
    val p = TreeUtil.getUserObject(o)
    val node = if (p is FilteringNode) p.delegate else p
    return node as? StructureViewTreeElement
  }

  private fun unwrapTreeElement(o: TreePath?): StructureViewTreeElement? {
    val p = TreeUtil.getLastUserObject(o)
    val node = if (p is FilteringNode) p.delegate else p
    return node as? StructureViewTreeElement
  }

  private inner class ToggleNarrowDownAction :
    ToggleAction(@Suppress("DialogTitleCapitalization") IdeBundle.message("checkbox.narrow.down.on.typing")) {
    override fun isSelected(e: AnActionEvent): Boolean {
      return isShouldNarrowDown
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.BGT
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      PropertiesComponent.getInstance().setValue(NARROW_DOWN_PROPERTY_KEY, state.toString())
      if (mySpeedSearch.isPopupActive && !StringUtil.isEmpty(mySpeedSearch.enteredPrefix)) {
        cs.launch(Dispatchers.UI) {
          rebuild(true)
        }
      }
    }
  }

  @TestOnly
  interface StructurePopupListener {
    fun rebuildAfterTreeChangeFinished()
  }

  companion object {
    private val LOG = Logger.getInstance(FileStructurePopup::class.java)

    @NonNls
    private const val NARROW_DOWN_PROPERTY_KEY: @NonNls String = "FileStructurePopup.narrowDown"

    private val isShouldNarrowDown: Boolean
      get() = PropertiesComponent.getInstance().getBoolean(
        NARROW_DOWN_PROPERTY_KEY, true)

    @get:NonNls
    private val dimensionServiceKey: @NonNls String
      get() = "StructurePopup"

    fun extractShortcutFor(action: CheckboxTreeAction): Array<Shortcut> {
      if (action.actionIdForShortcut != null) {
        return KeymapUtil.getActiveKeymapShortcuts(action.actionIdForShortcut).getShortcuts()
      }
      return action.shortcuts ?: arrayOf()
    }

    fun getSpeedSearchText(element: StructureViewTreeElement): String? {
      return element.getValue().speedSearchText
    }

    private fun find(
      element: StructureUiTreeElement,
      objects: MutableList<out SpeedSearchObjectWithWeight>,
      predicate: (StructureUiTreeElement, TreePath?) -> Boolean,
    ): SpeedSearchObjectWithWeight? {
      return objects.find { predicate(element, (it.node as? TreePath)) }
    }

    private fun isElement(element: StructureUiTreeElement, path: TreePath?): Boolean {
      val treeElement = TreeUtil.getLastUserObject(FilteringNode::class.java, path)?.delegate as? StructureViewTreeElement
      return element == treeElement?.getValue()
    }

    private fun isParent(parent: StructureUiTreeElement, path: TreePath?): Boolean {
      return path != null && isElement(parent, path.parentPath)
    }

    private fun isAncestor(ancestor: StructureUiTreeElement, path: TreePath?): Boolean {
      var path = path
      while (path != null) {
        if (isElement(ancestor, path)) return true
        path = path.parentPath
      }
      return false
    }
  }
}