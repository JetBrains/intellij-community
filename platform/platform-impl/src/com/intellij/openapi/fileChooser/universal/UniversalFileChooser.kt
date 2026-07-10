// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.universal

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.ProductIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDialog
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.PathChooserDialog
import com.intellij.openapi.fileChooser.impl.FileChooserUtil
import com.intellij.openapi.fileChooser.universal.UniversalFileChooser.Panel
import com.intellij.openapi.fileChooser.universal.UniversalFileChooserContributor.MountStatus
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.getUserData
import com.intellij.openapi.ui.putUserData
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.util.progress.RawProgressReporter
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.UIBundle
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.Consumer
import com.intellij.util.SystemProperties
import com.intellij.util.containers.toArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BooleanSupplier
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeWillExpandListener
import javax.swing.tree.ExpandVetoException
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name
import kotlin.time.Duration.Companion.seconds

private const val leftPanel: Boolean = false

@ApiStatus.Internal
object UniversalFileChooser {
  @JvmStatic
  fun canUseIn(project: Project?): Boolean {
    return Registry.`is`("universal.file.chooser.is.enabled")
           && SystemProperties.getBooleanProperty("universal.file.chooser.is.enabled", true) != false
  }

  @JvmStatic
  fun create(project: Project?, parent: Component?, descriptor: FileChooserDescriptor): Dialog {
    val currProject = project ?: ProjectManager.getInstance().defaultProject
    return Dialog(currProject, parent, descriptor)
  }

  /**
   * Capable of choosing files in a local file system and in Docker/WSL containers.
   */
  class Dialog(
    val project: Project,
    parent: Component? = null,
    private val descriptor: FileChooserDescriptor,
    private val contributors: Collection<UniversalFileChooserContributor> = UniversalFileChooserContributor.EP_NAME.extensionList,
  ) : DialogWrapper(project, parent, true, IdeModalityType.IDE), FileChooserDialog, PathChooserDialog {
    private lateinit var mainPanel: Panel

    init {
      init()
      title = descriptor.title ?: UIBundle.message("file.chooser.default.title")
    }

    override fun getDimensionServiceKey(): String = "UniversalFileChooserDialog"

    override fun choose(project: Project?, vararg toSelect: VirtualFile?): Array<out VirtualFile?> {
      val explicit = toSelect.firstOrNull()?.let { runCatching { it.toNioPath() }.getOrNull() }
      mainPanel.preselect(explicit)
      if (this.showAndGet()) {
        return toVirtualFiles(mainPanel.getSelectedFiles()).toArray(VirtualFile.EMPTY_ARRAY)
      }
      return emptyArray()
    }

    override fun choose(toSelect: VirtualFile?, callback: Consumer<in MutableList<VirtualFile>>) {
      val explicit = toSelect?.let { runCatching { it.toNioPath() }.getOrNull() }
      mainPanel.preselect(explicit)
      if (showAndGet()) {
        val mutableList = mutableListOf<VirtualFile>()
        mutableList.addAll(toVirtualFiles(mainPanel.getSelectedFiles()).filterNotNull())
        callback.consume(mutableList)
      }
    }

    override fun createCenterPanel(): JComponent {
      mainPanel = Panel(this.disposable, descriptor, project, ::doOKAction, ::setOKActionEnabled, contributors)
      return mainPanel
    }

    fun getSelectedFiles(): List<Path> = mainPanel.getSelectedFiles()

    override fun doOKAction() {
      getSelectedFiles().firstOrNull()?.let { lastSelected ->
        FileChooserUtil.setLastOpenedFile(project, lastSelected)
      }
      super.doOKAction()
    }
  }

  private fun toVirtualFiles(paths: List<Path>): List<VirtualFile?> {
    return paths.map { path ->
      VfsUtil.findFile(path, true)
    }
  }

  class Panel @JvmOverloads constructor(
    disposable: Disposable,
    private val descriptor: FileChooserDescriptor,
    private val project: Project,
    okAction: Runnable,
    private val okEnabledUpdater: (Boolean) -> Unit = {},
    contributors: Collection<UniversalFileChooserContributor> = UniversalFileChooserContributor.EP_NAME.extensionList,
    private val extraToolbarActions: ActionGroup = DefaultActionGroup(),
    private val extraPopupActions: ActionGroup = DefaultActionGroup()
  ) : JPanel(), FileBrowserPanel {

    companion object {
      private val FILE_VIEW_KEY: Key<FileView?> = Key.create<FileView>("universalFileChooser.fileView")
      private const val LOCATIONS_PROPORTION_KEY = "universalFileChooser.locationsProportion"
      private const val LOCATIONS_DEFAULT_PROPORTION = 0.2f
      private const val SHOW_HIDDEN_FILES_KEY = "universalFileChooser.showHiddenFiles"
    }

    private val tabbedPane: JBTabbedPane
    private val fileViews: MutableList<FileView> = mutableListOf()

    @Suppress("OPT_IN_USAGE")
    private val scope = GlobalScope.childScope("UniversalFileChooser")

    private val topToolbar: ActionToolbar
    private val toolbarActionGroup: DefaultActionGroup
    private val popupActionGroup: DefaultActionGroup
    private val effectiveContributors: Collection<UniversalFileChooserContributor>

    init {
      layout = BorderLayout()
      val properties = PropertiesComponent.getInstance()
      if (properties.isValueSet(SHOW_HIDDEN_FILES_KEY)) {
        descriptor.withShowHiddenFiles(properties.getBoolean(SHOW_HIDDEN_FILES_KEY, descriptor.isShowHiddenFiles))
      }
      val (toolbar, group) = createTopToolbar()
      topToolbar = toolbar
      toolbarActionGroup = group
      popupActionGroup = DefaultActionGroup(toolbarActionGroup, Separator.getInstance(), extraPopupActions)
      val screenSize = Toolkit.getDefaultToolkit().screenSize
      preferredSize = Dimension(screenSize.width / 2, screenSize.height / 2)
      tabbedPane = JBTabbedPane()
      effectiveContributors = if (descriptor.isEnvironmentRestricted) {
        projectContributor(project)?.let { listOf(it) } ?: contributors
      }
      else {
        contributors
      }
      for (contributor in effectiveContributors) {
        val fileView = FileView(contributor, descriptor, disposable, project, okAction, scope, topToolbar, popupActionGroup, ::updateOkEnabled)
        fileViews.add(fileView)
      }
      // If there is a single tab available, don't show the tab itself, only its content panel.
      val contentComponent: JComponent = if (fileViews.size == 1) {
        fileViews[0].topComponent
      }
      else {
        for (fileView in fileViews) {
          tabbedPane.addTab(fileView.contributor.tabTitle, fileView.topComponent)
        }
        tabbedPane.addChangeListener { updateOkEnabled() }
        tabbedPane
      }

      preselect(null)
      updateOkEnabled()

      if (leftPanel) {
        val splitter = OnePixelSplitter(false, LOCATIONS_PROPORTION_KEY, LOCATIONS_DEFAULT_PROPORTION)
        splitter.firstComponent = createLocationsPanel(project)
        splitter.secondComponent = contentComponent
        add(splitter, BorderLayout.CENTER)
      }
      else {
        val topPanel = panel {
          row {
            cell(topToolbar.component).align(AlignX.LEFT)
          }
        }
        add(topPanel, BorderLayout.NORTH)
        topToolbar.targetComponent = this
        add(contentComponent, BorderLayout.CENTER)
      }

      disposable.whenDisposed {
        scope.cancel()
      }

      registerFocusPathAction(disposable)
    }

    private fun registerFocusPathAction(disposable: Disposable) {
      val action = ActionManager.getInstance().getAction("UniversalFileChooser.FocusPath") ?: return
      val shortcutSet = action.shortcutSet
      if (shortcutSet.shortcuts.isEmpty()) return
      object : DumbAwareAction() {
        override fun actionPerformed(e: AnActionEvent) {
          getActiveFileView()?.focusPathField()
        }
      }.registerCustomShortcutSet(shortcutSet, this, disposable)
    }

    private fun createTopToolbar(): Pair<ActionToolbar, DefaultActionGroup> {
      val homeAction = object : AnAction(
        IdeBundle.message("universal.file.chooser.action.home.text"),
        IdeBundle.message("universal.file.chooser.action.home.description"),
        AllIcons.Nodes.HomeFolder
      ) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun actionPerformed(e: AnActionEvent) {
          navigateToHome()
        }
      }

      val desktopAction = object : AnAction(
        IdeBundle.message("universal.file.chooser.action.desktop.text"),
        IdeBundle.message("universal.file.chooser.action.desktop.description"),
        AllIcons.Nodes.Desktop
      ) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
          e.presentation.isVisible = fileViews.any { it.contributor.getDesktopPath() != null }
          e.presentation.isEnabled = true
        }

        override fun actionPerformed(e: AnActionEvent) {
          navigateToDesktop()
        }
      }

      val projectAction = if (!project.isDefault) object : AnAction(
        IdeBundle.message("universal.file.chooser.action.project.text"),
        IdeBundle.message("universal.file.chooser.action.project.description"),
        ProductIcons.getInstance().getProjectIcon()
      ) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun actionPerformed(e: AnActionEvent) {
          navigateToProject()
        }
      } else null

      val showHiddenAction = object : ToggleAction(
        IdeBundle.message("universal.file.chooser.action.show.hidden.text"),
        IdeBundle.message("universal.file.chooser.action.show.hidden.description"),
        AllIcons.Actions.ToggleVisibility
      ) {
        override fun isSelected(e: AnActionEvent): Boolean = getActiveFileView()?.fileTree?.areHiddensShown() == true

        override fun setSelected(e: AnActionEvent, state: Boolean) {
          getActiveFileView()?.fileTree?.showHiddens(state)
          PropertiesComponent.getInstance().setValue(SHOW_HIDDEN_FILES_KEY, state)
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
      }

      val createDirectoryAction = object : AnAction(
        IdeBundle.message("universal.file.chooser.action.create.directory.text"),
        IdeBundle.message("universal.file.chooser.action.create.directory.description"),
        AllIcons.Actions.NewFolder
      ) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

        override fun update(e: AnActionEvent) {
          val fileView = getActiveFileView()
          if (fileView == null) { e.presentation.isEnabled = false; return }
          val parent = fileView.fileTree.getNewFileParent()
          e.presentation.isEnabled = parent != null && parent.parent != null && Files.isDirectory(parent) && Files.isWritable(parent)
        }

        override fun actionPerformed(e: AnActionEvent) {
          val fileView = getActiveFileView() ?: return
          val parent = fileView.fileTree.getNewFileParent() ?: return
          val newFolderName = Messages.showInputDialog(
            UIBundle.message("create.new.folder.enter.new.folder.name.prompt.text"),
            UIBundle.message("new.folder.dialog.title"),
            Messages.getQuestionIcon(),
            "",
            null
          ) ?: return
          val failReason = fileView.fileTree.createNewFolder(parent, newFolderName)
          if (failReason != null) {
            Messages.showMessageDialog(
              UIBundle.message("create.new.folder.could.not.create.folder.error.message", newFolderName),
              UIBundle.message("error.dialog.title"),
              Messages.getErrorIcon()
            )
          }
        }
      }

      val deleteAction = object : AnAction(
        IdeBundle.message("universal.file.chooser.action.delete.text"),
        IdeBundle.message("universal.file.chooser.action.delete.description"),
        AllIcons.General.Delete
      ) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

        override fun update(e: AnActionEvent) {
          e.presentation.isEnabled = getActiveFileView()?.canDeleteSelectedFile() == true
        }

        override fun actionPerformed(e: AnActionEvent) {
          getActiveFileView()?.deleteSelectedFile()
        }
      }

      val refreshAction = object : AnAction(
        IdeBundle.message("universal.file.chooser.action.refresh.text"),
        IdeBundle.message("universal.file.chooser.action.refresh.description"),
        AllIcons.Actions.Refresh
      ) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun actionPerformed(e: AnActionEvent) {
          getActiveFileView()?.fileTree?.updateTree()
        }
      }

      val actionGroup = DefaultActionGroup().apply {
        add(homeAction)
        add(desktopAction)
        if (projectAction != null) add(projectAction)
        addSeparator()
        add(createDirectoryAction)
        add(deleteAction)
        addSeparator()
        add(refreshAction)
        add(showHiddenAction)
      }

      val toolbarGroup = DefaultActionGroup(actionGroup, Separator.getInstance(), extraToolbarActions)
      val toolbar = ActionManager.getInstance().createActionToolbar("UniversalFileChooserTopToolbar", toolbarGroup, true)
      return toolbar to actionGroup
    }

    private fun projectContributor(project: Project): UniversalFileChooserContributor? {
      if (project.isDefault) return null
      val basePath = project.basePath ?: project.projectFilePath ?: return null
      return UniversalFileChooserContributor.findOwner(Path.of(basePath))
    }

    private fun preselectProjectTab(project: Project) {
      if (fileViews.size <= 1) return
      val projectContributor = projectContributor(project)
      projectContributor?.let { contributor ->
        tabbedPane.indexOfTab(contributor.tabTitle)
          .takeIf { it >= 0 }?.let { tabbedPane.selectedIndex = it }
      }
    }

    fun preselect(toSelect: Path?) {
      scope.launch {
        withContext(Dispatchers.IO) {
          val target = pathToSelect(toSelect)
          val effective = if (descriptor is FileSaverDescriptor && Files.exists(target) && !Files.isDirectory(target)) {
            target.parent ?: target
          }
          else {
            target
          }
          runOnEdt {
            navigateToFile(effective)
            if (toSelect == null) {
              preselectProjectTab(project)
            }
          }
        }
      }
    }

    private suspend fun pathToSelect(toSelect: Path?): Path {
      val last = NioFileChooserUtil.getLastOpenedPath(project)
      if (last != null && (toSelect == null || descriptor.getUserData(PathChooserDialog.PREFER_LAST_OVER_EXPLICIT) == true)) {
        return last
      }
      if (toSelect != null) {
        return toSelect
      }
      if (!project.isDefault) {
        val eelDescriptor = project.getEelDescriptor()
        val basePath = project.basePath ?: project.projectFilePath
        if (basePath != null) {
          return runCatching { Path.of(basePath) }.getOrNull() ?: eelDescriptor.toEelApi().userInfo.home.asNioPath()
        }
      }
      return Path.of(SystemProperties.getUserHome())
    }


    override fun getSelectedFiles(): List<Path> {
      val fileView = getActiveFileView()
      return fileView?.getSelectedFiles() ?: emptyList()
    }

    fun navigateToFile(file: Path) {
      val index = fileViews.indexOfFirst { it.contributor.ownsPath(file) }
      if (index < 0) return
      if (fileViews.size > 1) {
        tabbedPane.selectedIndex = index
      }
      val targetView = fileViews[index]
      targetView.fileToSelect = file
      targetView.fileTree.select(file) { targetView.fileTree.expand(file, null) }
    }

    private fun getActiveFileView(): FileView? {
      if (fileViews.size == 1) return fileViews[0]
      val component = tabbedPane.selectedComponent as? JComponent ?: return null
      return component.getUserData(FILE_VIEW_KEY)
    }

    private fun updateOkEnabled() {
      val activeView = getActiveFileView()
      okEnabledUpdater(activeView?.isOkEnabled() ?: false)
    }

    private data class LocationData(
      val icon: Icon,
      val text: @Nls String,
      val action: Runnable,
    )

    private fun createLocationsPanel(project: Project): JComponent {
      val locations = buildList {
        add(LocationData(
          icon = AllIcons.Nodes.HomeFolder,
          text = IdeBundle.message("universal.file.chooser.action.home.text"),
          action = { navigateToHome() }
        ))
        add(LocationData(
          icon = AllIcons.Nodes.Desktop,
          text = IdeBundle.message("universal.file.chooser.action.desktop.text"),
          action = { navigateToDesktop() }
        ))
        if (!project.isDefault) {
          add(LocationData(
            icon = AllIcons.Nodes.Project,
            text = IdeBundle.message("universal.file.chooser.location.project"),
            action = { navigateToProject() }
          ))
        }
      }
      val locationList = JBList(locations)
      locationList.selectionMode = ListSelectionModel.SINGLE_SELECTION
      locationList.cellRenderer = object : ColoredListCellRenderer<LocationData>() {
        override fun customizeCellRenderer(
          list: JList<out LocationData>,
          value: LocationData,
          index: Int,
          selected: Boolean,
          hasFocus: Boolean,
        ) {
          icon = value.icon
          append(value.text)
        }
      }
      locationList.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          val index = locationList.locationToIndex(e.point)
          if (index >= 0) {
            locationList.model.getElementAt(index).action.run()
            locationList.clearSelection()
          }
        }
      })
      return panel {
        row {
          cell(locationList).align(AlignX.FILL)
        }
      }
    }

    private fun navigateToHome() {
      val activeView = getActiveFileView() ?: return
      activeView.topComponent.cursor = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)
      scope.launch {
        withContext(Dispatchers.IO) {
          val basePath = project.basePath?.let { Path.of(it) }
                         ?: findNonProjectBasePath()
                         ?: return@withContext
          val homePath = basePath.asEelPath().descriptor.toEelApi().userInfo.home.asNioPath()
          runOnEdt {
            activeView.topComponent.cursor = Cursor.getDefaultCursor()
            navigateToFile(homePath)
          }
        }
      }
    }

    private fun findNonProjectBasePath(): Path? {
      val localHome = Path.of(SystemProperties.getUserHome())
      if (effectiveContributors.find { c -> c.ownsPath(localHome) } != null) return localHome
      val activeView = getActiveFileView() ?: return null
      return activeView.roots.asSequence()
        .mapNotNull { runCatching { Path.of(it) }.getOrNull() }
        .firstOrNull()
    }

    private fun navigateToProject() {
      val basePath = project.basePath ?: return
      scope.launch {
        withContext(Dispatchers.IO) {
          runOnEdt {
            navigateToFile(Path.of(basePath))
          }
        }
      }
    }

    private fun navigateToDesktop() {
      val targetView = getActiveFileView()?.takeIf { it.contributor.getDesktopPath() != null }
                       ?: fileViews.firstOrNull { it.contributor.getDesktopPath() != null }
                       ?: return
      targetView.topComponent.cursor = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)
      scope.launch {
        withContext(Dispatchers.IO) {
          targetView.contributor.getDesktopPath()?.let { desktopPath ->
            runOnEdt {
              targetView.topComponent.cursor = Cursor.getDefaultCursor()
              navigateToFile(desktopPath)
            }
          }
        }
      }
    }


    class FileView(
      val contributor: UniversalFileChooserContributor,
      descriptor: FileChooserDescriptor,
      disposable: Disposable,
      private val project: Project,
      okAction: Runnable,
      val scope: CoroutineScope,
      private val topToolbar: ActionToolbar,
      popupActionGroup: ActionGroup,
      private val okEnabledUpdater: () -> Unit = {},
    ) {
      val topComponent: JComponent
      val fileTree: NioFileSystemTree
      val roots: MutableList<String> = mutableListOf()
      private val environmentRestricted: Boolean = descriptor.isEnvironmentRestricted

      var fileToSelect: Path? = null
      private val pathTextField: NioPathTextField = NioPathTextField(scope, descriptor.isChooseFiles)

      @Volatile
      private var pathTextFieldInvalid: Boolean = false

      companion object {
        private const val LOADING_CARD = "loading"
        private const val TREE_CARD = "tree"
      }

      private val cardLayout = CardLayout()
      private val contentPanel = JPanel(cardLayout)
      private val tree = Tree()
      val mountStatusCache: MutableMap<String, MountStatus> = ConcurrentHashMap()
      private val presentationCache: MutableMap<String, UniversalFileChooserContributor.Presentation> = ConcurrentHashMap()

      @Volatile
      var cacheUpdateJob: Job? = null

      @Volatile
      var isMountActionInProgress: Boolean = false

      init {
        val descriptorCopy = FileChooserDescriptor(descriptor)

        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.selectionModel.selectionMode = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
        contributor.getNoEntriesText()?.let { tree.emptyText.text = it }
        tree.addTreeWillExpandListener(object : TreeWillExpandListener {
          override fun treeWillExpand(event: TreeExpansionEvent) {
            val virtualRoot = fileTree.getVirtualRoot(event.path)
            if (virtualRoot != null) {
              mountVirtualRootAndReload(virtualRoot)
              throw ExpandVetoException(event)
            }
            val isUnmounted = isUnderUnmountedRoot(event.path)
            if (isUnmounted ?: true) {
              val nioPath = NioFileSystemTree.getNioPath(event.path)
              if (nioPath != null && isUnmounted != null) {
                mountUnmountedRootAndReload(nioPath.root)
              }
              throw ExpandVetoException(event)
            }
          }

          override fun treeWillCollapse(event: TreeExpansionEvent) {}
        })
        fileTree = NioFileSystemTree(project, descriptorCopy, tree, contributor, scope)
        Disposer.register(disposable, fileTree)
        fileTree.addOkAction(okAction)
        fileTree.addListener(object : NioFileSystemTree.Listener {
          override fun selectionChanged(selection: List<Path?>) {
            updatePathField(selection)
            okEnabledUpdater()
          }
        }, disposable)
        val scrollPane = ScrollPaneFactory.createScrollPane(fileTree.getTree())

        pathTextField.showHiddenSupplier = BooleanSupplier { fileTree.areHiddensShown() }
        ComponentValidator(disposable)
          .withValidator(Supplier<ValidationInfo?> {
            if (pathTextFieldInvalid)
              ValidationInfo(IdeBundle.message("universal.file.chooser.invalid.path"), pathTextField)
            else null
          })
          .installOn(pathTextField)
        pathTextField.document.addDocumentListener(object : DocumentListener {
          override fun insertUpdate(e: DocumentEvent) { setPathTextFieldError(false) }
          override fun removeUpdate(e: DocumentEvent) { setPathTextFieldError(false) }
          override fun changedUpdate(e: DocumentEvent) {}
        })
        pathTextField.addKeyListener(object : KeyAdapter() {
          override fun keyPressed(e: KeyEvent) {
            if (e.isConsumed) return
            when (e.keyCode) {
              KeyEvent.VK_ENTER -> {
                navigateToTextFieldPath(); e.consume()
              }
              KeyEvent.VK_ESCAPE -> {
                setPathTextFieldError(false)
                updatePathField(fileTree.getSelectedFile()?.let { listOf(it) } ?: emptyList())
                focusTree()
                e.consume()
              }
            }
          }
        })

        tree.addTreeSelectionListener {
          topToolbar.updateActionsAsync()
        }

        PopupHandler.installPopupMenu(tree, popupActionGroup, "UniversalFileChooserTreePopup")

        tree.addKeyListener(object : KeyAdapter() {
          override fun keyPressed(e: KeyEvent) {
            if (e.isConsumed) return
            if (e.keyCode == KeyEvent.VK_DELETE && e.modifiersEx == 0) {
              if (canDeleteSelectedFile()) {
                deleteSelectedFile()
                e.consume()
              }
            }
          }
        })

        val loadingLabel = JBLabel(
          contributor.getCustomLoadingText() ?: IdeBundle.message("universal.file.chooser.label.loading"),
          SwingConstants.CENTER)
        contentPanel.add(loadingLabel, LOADING_CARD)
        contentPanel.add(scrollPane, TREE_CARD)

        val mainPanel = panel {
          row {
            cell(pathTextField)
              .align(AlignX.FILL)
              .resizableColumn()
          }
          row {
            cell(contentPanel)
              .align(AlignX.FILL)
              .align(AlignY.FILL)
              .resizableColumn()
          }.resizableRow()
        }

        topComponent = mainPanel
        topComponent.putUserData(FILE_VIEW_KEY, this)

        loadRoots()

      }

      fun loadRoots() {
        cardLayout.show(contentPanel, LOADING_CARD)
        scope.launch {
          withContext(Dispatchers.IO) {
            val allRoots = if (environmentRestricted && !project.isDefault) {
              val basePath = project.basePath?.let { Path.of(it) }
              if (basePath != null) contributor.getFilteredRoots(basePath) else contributor.getRoots()
            }
            else {
              contributor.getRoots()
            }
            val realRoots = allRoots.filter { it.path != null }
            val presentations = mutableMapOf<String, UniversalFileChooserContributor.Presentation>()
            val mountStatuses = mutableMapOf<String, MountStatus>()
            for (root in realRoots) {
              val rootKey = root.path!!.invariantSeparatorsPathString
              val presentation = contributor.getPresentation(root.path!!)
              if (presentation != null) {
                presentations[rootKey] = presentation
              }
              mountStatuses[rootKey] = contributor.getMountStatus(root.path!!)
            }
            runOnEdt {
              roots.clear()
              roots.addAll(realRoots.map { it.path!!.invariantSeparatorsPathString })
              presentationCache.clear()
              presentationCache.putAll(presentations)
              mountStatusCache.clear()
              mountStatusCache.putAll(mountStatuses)
              fileTree.setRoots(allRoots)
              fileTree.updateTree()
              cardLayout.show(contentPanel, TREE_CARD)
              fileToSelect?.let {
                val selection = if (it.root == it) {
                  fileTree.matchRoot(it)
                } else it
                if (selection != null) {
                  fileTree.select(selection) { fileTree.expand(selection, null) }
                }
              }
              fileToSelect = null
              okEnabledUpdater()
              startCacheUpdates()
            }
          }
        }
      }

      fun startCacheUpdates() {
        cacheUpdateJob?.cancel()
        val changed = mutableSetOf<String>()
        cacheUpdateJob = scope.launch {
          while (true) {
            changed.clear()
            withContext(Dispatchers.IO) {
              for (root in roots) {
                val oldStatus = mountStatusCache[root]
                val newStatus = contributor.getMountStatus(Path.of(root))
                if (oldStatus != newStatus) {
                  mountStatusCache[root] = newStatus
                  if (oldStatus != null) {
                    changed.add(root)
                  }
                }
              }
            }
            changed.forEach { handleMountStatusChange(Path.of(it)) }
            delay(3.seconds)
          }
        }
      }

      private fun handleMountStatusChange(root: Path) {
        when (mountStatusCache[root.invariantSeparatorsPathString]) {
          MountStatus.Unmounted -> {
            runOnEdt {
              collapseUnmountedRoot(root)
              topToolbar.updateActionsAsync()
            }
            loadRoots()
          }
          MountStatus.Mounted -> {
            loadRoots()
            runOnEdt {
              topToolbar.updateActionsAsync()
            }
          }
          else -> {}
        }
      }

      fun getSelectedFiles(): List<Path> {
        return fileTree.getSelectedFiles().filterNotNull().filter { file ->
          isUnmountedRoot(file) == false
        }
      }

      fun isOkEnabled(): Boolean {
        val selected = getSelectedFiles()
        return selected.isNotEmpty() && selected.all { file ->
          file.parent != null
        }
      }

      fun canDeleteSelectedFile(): Boolean {
        val selected = fileTree.getSelectedFile() ?: return false
        if (roots.contains(selected.invariantSeparatorsPathString)) return false
        if (!Files.isWritable(selected)) return false
        return true
      }

      fun deleteSelectedFile() {
        val selected = fileTree.getSelectedFile() ?: return
        val confirmMessage = if (Files.isDirectory(selected) && !runCatching { Files.list(selected).use { it.findAny().isPresent } }.getOrElse { false }) {
          IdeBundle.message("universal.file.chooser.action.delete.confirm.directory", selected.name)
        }
        else {
          IdeBundle.message("universal.file.chooser.action.delete.confirm", selected.name)
        }
        if (Messages.showYesNoDialog(
            confirmMessage,
            IdeBundle.message("universal.file.chooser.action.delete.text"),
            Messages.getWarningIcon()
          ) != Messages.YES) return

        val nextSelection = fileTree.computeSelectionAfterDeletion()

        scope.launch {
          var failure: Exception? = null
          try {
            withBackgroundProgress(project, IdeBundle.message("universal.file.chooser.action.delete.progress.title", selected.name)) {
              withContext(Dispatchers.IO) {
                val deletionContext = currentCoroutineContext()
                reportRawProgress { reporter ->
                  deleteRecursively(selected, reporter, deletionContext)
                }
              }
            }
          }
          catch (e: CancellationException) {
            // The user cancelled the progress (or the dialog was disposed): reflect the partial deletion, then propagate.
            runOnEdt { fileTree.updateTree() }
            throw e
          }
          catch (e: Exception) {
            failure = e
          }
          runOnEdt {
            fileTree.updateTree()
            when {
              failure != null -> Messages.showErrorDialog(failure.message ?: "", IdeBundle.message("universal.file.chooser.action.delete.text"))
              nextSelection != null -> fileTree.select(nextSelection, null)
            }
          }
        }
      }

      private fun deleteRecursively(path: Path, reporter: RawProgressReporter, context: CoroutineContext) {
        context.ensureActive()
        if (Files.isDirectory(path) && !Files.isSymbolicLink(path)) {
          Files.newDirectoryStream(path).use { children ->
            for (child in children) {
              deleteRecursively(child, reporter, context)
            }
          }
        }
        reporter.text(IdeBundle.message("universal.file.chooser.action.delete.progress.deleting", path.fileName?.toString() ?: path.toString()))
        Files.delete(path)
      }

      private fun findRootPath(nioPath: Path): String? {
        return roots.firstOrNull { root -> nioPath.startsWith(root) }
      }

      private fun isUnmountedRoot(nioPath: Path): Boolean? {
        val rootPath = findRootPath(nioPath) ?: return false
        return mountStatusCache[rootPath]?.let{ it == MountStatus.Unmounted }
      }

      private fun isUnderUnmountedRoot(treePath: TreePath): Boolean? {
        val nioPath = NioFileSystemTree.getNioPath(treePath) ?: return false
        return isUnmountedRoot(nioPath)
      }

      private fun collapseUnmountedRoot(path: Path) {
        val rootPath = findRootPath(path) ?: return
        val rowCount = tree.rowCount
        for (i in 0 until rowCount) {
          val treePath = tree.getPathForRow(i) ?: continue
          val nioPath = NioFileSystemTree.getNioPath(treePath) ?: continue
          if (nioPath.invariantSeparatorsPathString == rootPath) {
            tree.collapsePath(treePath)
            return
          }
        }
      }

      private fun focusTree() {
        fileTree.getTree().requestFocusInWindow()
      }

      fun focusPathField() {
        if (!pathTextField.isShowing) return
        pathTextField.requestFocusInWindow()
        pathTextField.selectAll()
      }

      private fun navigateToTextFieldPath() {
        val text = pathTextField.text.trim()
        if (text.isEmpty()) {
          setPathTextFieldError(false)
          updatePathField(fileTree.getSelectedFile()?.let { listOf(it) } ?: emptyList())
          focusTree()
          return
        }
        scope.launch {
          withContext(Dispatchers.IO) {
            val path = runCatching { Path.of(text) }.getOrNull()
            val exists = path != null && runCatching { Files.exists(path) }.getOrDefault(false)
            if (path == null || !exists) {
              runOnEdt {
                setPathTextFieldError(true)
                if (pathTextField.isShowing) {
                  pathTextField.requestFocusInWindow()
                }
              }
              return@withContext
            }
            val forceShowHidden = !fileTree.areHiddensShown() && hasHiddenSegment(path)
            runOnEdt {
              setPathTextFieldError(false)
              focusTree()
              if (forceShowHidden) {
                fileTree.showHiddens(true)
                PropertiesComponent.getInstance().setValue(SHOW_HIDDEN_FILES_KEY, true)
                topToolbar.updateActionsAsync()
              }
              fileTree.select(path) { fileTree.expand(path, null) }
            }
          }
        }
      }

      private fun setPathTextFieldError(isError: Boolean) {
        if (pathTextFieldInvalid == isError) return
        pathTextFieldInvalid = isError
        ComponentValidator.getInstance(pathTextField).ifPresent { it.revalidate() }
      }

      private fun hasHiddenSegment(path: Path): Boolean {
        var current: Path? = path
        while (current != null && current.parent != null) {
          if (runCatching { NioFileChooserUtil.isHidden(current) }.getOrDefault(false)) {
            return true
          }
          current = current.parent
        }
        return false
      }

      private fun updatePathField(selection: List<Path?>) {
        val file = selection.firstOrNull()
        pathTextField.text = file?.toString() ?: ""
        pathTextField.caretPosition = pathTextField.text.length
      }

      fun mountVirtualRootAndReload(virtualRoot: UniversalFileChooserContributor.Root) {
        if (isMountActionInProgress) return
        isMountActionInProgress = true
        cacheUpdateJob?.cancel()
        topComponent.cursor = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)
        scope.launch {
          try {
            val mountedPath = withContext(Dispatchers.IO) {
              contributor.mountVirtualRoot(virtualRoot)
            }
            if (mountedPath != null) {
              fileToSelect = mountedPath
            }
          }
          finally {
            topComponent.cursor = Cursor.getDefaultCursor()
            isMountActionInProgress = false
            loadRoots()
          }
        }
      }

      fun mountUnmountedRootAndReload(root: Path) {
        if (isMountActionInProgress) return
        isMountActionInProgress = true
        cacheUpdateJob?.cancel()
        topComponent.cursor = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)
        scope.launch {
          try {
            withContext(Dispatchers.IO) {
              val status = contributor.getMountStatus(root)
              if (status == MountStatus.Unmounted) {
                contributor.mount(root)
              }
            }
            fileToSelect = root
            isMountActionInProgress = false
            startCacheUpdates()
            loadRoots()
            runOnEdt {
              fileTree.updateTree()
            }
          }
          catch (e: Exception) {
            isMountActionInProgress = false
            fileTree.setRootError(root, e.localizedMessage ?: e.message ?: e.javaClass.simpleName)
          }
          finally {
            topComponent.cursor = Cursor.getDefaultCursor()
          }
        }
      }

    }
  }

  @Suppress("ForbiddenInSuspectContextMethod") // ModalityState.any() is required.
  private fun runOnEdt(runnable: Runnable) {
    ApplicationManager.getApplication().invokeLater(runnable, ModalityState.any())
  }

}

@ApiStatus.Experimental
interface FileBrowserPanel {
  /**
   * Returns the files and/or directories currently selected in the active tab of the panel.
   */
  fun getSelectedFiles(): List<Path>
}

object FileBrowser {
  /**
   * Entry point for building an embeddable [FileBrowserPanel].
   *
   * The [parentDisposable] owns the panel's lifecycle: background loaders, listeners, and the
   * internal coroutine scope are released when it is disposed. Callers should not rely on any
   * dialog-close events.
   *
   * Typical usage:
   * ```
   * val panel = FileBrowser.builder(descriptor, parentDisposable)
   *   .forProject(myProject)
   *   .contributors(listOf(myContributor))
   *   .onDefaultAction { openSelected() }
   *   .toolbarActions(myToolbarGroup)
   *   .popupActions(myPopupGroup)
   *   .build()
   * ```
   *
   * @param descriptor        file chooser descriptor
   * @param parentDisposable  disposable owning the returned panel
   */
  @ApiStatus.Experimental
  @JvmStatic
  fun builder(descriptor: FileChooserDescriptor, parentDisposable: Disposable): Builder =
    Builder(descriptor, parentDisposable)

  /**
   * Fluent builder for an embeddable [FileBrowserPanel].
   *
   * Only [project], [descriptor], and [parentDisposable] are required; all other parameters have
   * reasonable defaults. Use [contributors] or [root] to restrict which tabs are shown, and
   * [toolbarActions] / [popupActions] to inject additional actions.
   *
   * @see FileBrowser.builder
   */
  @ApiStatus.Experimental
  class Builder internal constructor(
    private val descriptor: FileChooserDescriptor,
    private val parentDisposable: Disposable,
  ) {
    private var contributors: Collection<UniversalFileChooserContributor> = UniversalFileChooserContributor.EP_NAME.extensionList
    private var onDefaultAction: Runnable = Runnable {}
    private var toolbarActions: ActionGroup = DefaultActionGroup()
    private var popupActions: ActionGroup = DefaultActionGroup()
    private var project: Project = ProjectManager.getInstance().defaultProject

    /**
     * Sets the project for the builder.
     *
     * @param project the project to be associated with the builder, defaults to `ProjectManager.getInstance().defaultProject`
     */
    fun forProject(project: Project) {
      this.project = project
    }

    /**
     * Restricts the tabs shown in the panel to the given [contributors]. By default all registered
     * [UniversalFileChooserContributor] extensions are used.
     */
    fun contributors(contributors: Collection<UniversalFileChooserContributor>): Builder = apply {
      this.contributors = contributors
    }

    /**
     * Shortcut for a single contributor rooted at [root]: the panel will show only that contributor's
     * subtree starting at [root]. Returns `false` if no [UniversalFileChooserContributor] owns [root];
     * in that case the builder is left unchanged so callers can decide how to react.
     */
    fun root(root: Path): Boolean {
      val contributor = UniversalFileChooserContributor.findOwner(root) ?: return false
      this.contributors = listOf(SingleRootContributor(contributor, root))
      return true
    }

    /**
     * Callback invoked when the user triggers the default action on the current selection
     * (Enter / double-click).
     */
    fun onDefaultAction(action: Runnable): Builder = apply {
      this.onDefaultAction = action
    }

    /**
     * Additional actions appended to the panel's top toolbar (after the built-in navigation actions).
     */
    fun toolbarActions(actions: ActionGroup): Builder = apply {
      this.toolbarActions = actions
    }

    /**
     * Additional actions appended to the tree's context popup (after the toolbar actions).
     */
    fun popupActions(actions: ActionGroup): Builder = apply {
      this.popupActions = actions
    }

    fun build(): FileBrowserPanel =
      Panel(parentDisposable, descriptor, project, onDefaultAction, {}, contributors, toolbarActions, popupActions)
  }
}