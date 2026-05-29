// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.universal

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.ProductIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDialog
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.PathChooserDialog
import com.intellij.openapi.fileChooser.impl.FileChooserUtil
import com.intellij.openapi.fileChooser.universal.UniversalFileChooserContributor.MountStatus
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.getUserData
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.putUserData
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.UIBundle
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.breadcrumbs.Breadcrumbs
import com.intellij.ui.components.breadcrumbs.Crumb
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.Consumer
import com.intellij.util.SystemProperties
import com.intellij.util.containers.isEmpty
import com.intellij.util.containers.toArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name
import kotlin.io.path.pathString
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
  ) : JPanel() {

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

    init {
      layout = BorderLayout()
      val properties = PropertiesComponent.getInstance()
      if (properties.isValueSet(SHOW_HIDDEN_FILES_KEY)) {
        descriptor.withShowHiddenFiles(properties.getBoolean(SHOW_HIDDEN_FILES_KEY, descriptor.isShowHiddenFiles))
      }
      val (toolbar, group) = createTopToolbar()
      topToolbar = toolbar
      toolbarActionGroup = group
      val screenSize = Toolkit.getDefaultToolkit().screenSize
      preferredSize = Dimension(screenSize.width / 2, screenSize.height / 2)
      tabbedPane = JBTabbedPane()
      val effectiveContributors = if (descriptor.isEnvironmentRestricted) {
        projectContributor(project)?.let { listOf(it) } ?: contributors
      }
      else {
        contributors
      }
      for (contributor in effectiveContributors) {
        val fileView = FileView(contributor, descriptor, disposable, project, okAction, scope, topToolbar, toolbarActionGroup, ::updateOkEnabled)
        fileViews.add(fileView)
        tabbedPane.addTab(contributor.tabTitle, fileView.topComponent)
      }
      tabbedPane.addChangeListener { updateOkEnabled() }

      preselect(null)
      updateOkEnabled()

      if (leftPanel) {
        val splitter = OnePixelSplitter(false, LOCATIONS_PROPORTION_KEY, LOCATIONS_DEFAULT_PROPORTION)
        splitter.firstComponent = createLocationsPanel(project)
        splitter.secondComponent = tabbedPane
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
        add(tabbedPane, BorderLayout.CENTER)
      }

      disposable.whenDisposed {
        scope.cancel()
      }
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
          e.presentation.isVisible = getActiveFileView()?.contributor?.getDesktopPath() != null
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

      val toolbar = ActionManager.getInstance().createActionToolbar("UniversalFileChooserTopToolbar", actionGroup, true)
      return toolbar to actionGroup
    }

    private fun projectContributor(project: Project): UniversalFileChooserContributor? {
      if (project.isDefault) return null
      val basePath = project.basePath ?: project.projectFilePath ?: return null
      return UniversalFileChooserContributor.findOwner(Path.of(basePath))
    }

    private fun preselectProjectTab(project: Project) {
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


    fun getSelectedFiles(): List<Path> {
      val fileView = (tabbedPane.selectedComponent as JComponent).getUserData(FILE_VIEW_KEY)
      return fileView?.getSelectedFiles() ?: emptyList()
    }

    fun navigateToFile(file: Path) {
      val index = fileViews.indexOfFirst { it.contributor.ownsPath(file) }
      if (index < 0) return
      tabbedPane.selectedIndex = index
      val targetView = fileViews[index]
      targetView.fileToSelect = file
      targetView.fileTree.select(file) { targetView.fileTree.expand(file, null) }
    }

    private fun getActiveFileView(): FileView? {
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
                         ?: Path.of(SystemProperties.getUserHome())
                         ?: return@withContext
          val homePath = basePath.asEelPath().descriptor.toEelApi().userInfo.home.asNioPath()
          runOnEdt {
            activeView.topComponent.cursor = Cursor.getDefaultCursor()
            navigateToFile(homePath)
          }
        }
      }
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
      val activeView = getActiveFileView() ?: return
      activeView.topComponent.cursor = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)
      scope.launch {
        withContext(Dispatchers.IO) {
          activeView.contributor.getDesktopPath()?.let { desktopPath ->
            runOnEdt {
              activeView.topComponent.cursor = Cursor.getDefaultCursor()
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
      toolbarActionGroup: DefaultActionGroup,
      private val okEnabledUpdater: () -> Unit = {},
    ) {
      val topComponent: JComponent
      val fileTree: NioFileSystemTree
      val roots: MutableList<String> = mutableListOf()
      private val environmentRestricted: Boolean = descriptor.isEnvironmentRestricted

      var fileToSelect: Path? = null
      private val breadcrumbs = Breadcrumbs()
      private var currentCrumbs: List<FileCrumb> = emptyList()
      private val barCardLayout = CardLayout()
      private val barPanel = JPanel(barCardLayout)
      private val pathTextField: NioPathTextField = NioPathTextField(scope)

      @Volatile
      private var pathTextFieldInvalid: Boolean = false

      companion object {
        private const val LOADING_CARD = "loading"
        private const val TREE_CARD = "tree"
        private const val BREADCRUMBS_CARD = "breadcrumbs"
        private const val PATH_CARD = "path"
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
            updateBreadcrumbs(selection)
            okEnabledUpdater()
          }
        }, disposable)
        val scrollPane = ScrollPaneFactory.createScrollPane(fileTree.getTree())

        barPanel.add(breadcrumbs, BREADCRUMBS_CARD)
        barPanel.add(pathTextField, PATH_CARD)
        breadcrumbs.onSelect { crumb, event ->
          val fileCrumb = crumb as? FileCrumb ?: return@onSelect
          if (fileCrumb == currentCrumbs.lastOrNull() && Files.isDirectory(fileCrumb.file)) {
            showDirectoryPopup(fileCrumb.file, event as? MouseEvent ?: return@onSelect)
          }
          else {
            fileTree.select(fileCrumb.file, null)
          }
        }
        breadcrumbs.addMouseListener(object : MouseAdapter() {
          override fun mouseClicked(e: MouseEvent) {
            if (breadcrumbs.getCrumbAt(e.x, e.y) == null) {
              switchToEditMode()
            }
          }
        })
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
                setPathTextFieldError(false); switchToBreadcrumbs(); e.consume()
              }
            }
          }
        })

        tree.addTreeSelectionListener {
          topToolbar.updateActionsAsync()
        }

        PopupHandler.installPopupMenu(tree, toolbarActionGroup, "UniversalFileChooserTreePopup")

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
            cell(barPanel)
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
        if (Files.isDirectory(selected) && !runCatching { Files.list(selected).isEmpty() }.getOrElse { true }) return false
        return true
      }

      fun deleteSelectedFile() {
        val selected = fileTree.getSelectedFile() ?: return
        if (Messages.showYesNoDialog(
            IdeBundle.message("universal.file.chooser.action.delete.confirm", selected.name),
            IdeBundle.message("universal.file.chooser.action.delete.text"),
            Messages.getWarningIcon()
          ) != Messages.YES) return

        val nextSelection = fileTree.computeSelectionAfterDeletion()

        scope.launch {
          withContext(Dispatchers.IO) {
            val result = runCatching { Files.delete(selected) }
            runOnEdt {
              if (result.isSuccess) {
                fileTree.updateTree()
                if (nextSelection != null) {
                  fileTree.select(nextSelection, null)
                }
              }
              else {
                val message = result.exceptionOrNull()?.message ?: ""
                Messages.showErrorDialog(message, IdeBundle.message("universal.file.chooser.action.delete.text"))
              }
            }
          }
        }
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

      private fun switchToEditMode() {
        val selectedFile = fileTree.getSelectedFile()
        pathTextField.text = selectedFile?.toString() ?: ""
        barCardLayout.show(barPanel, PATH_CARD)
        pathTextField.requestFocusInWindow()
        pathTextField.caretPosition = pathTextField.text.length
      }

      private fun switchToBreadcrumbs() {
        barCardLayout.show(barPanel, BREADCRUMBS_CARD)
        fileTree.getTree().requestFocusInWindow()
      }

      private fun navigateToTextFieldPath() {
        val text = pathTextField.text.trim()
        if (text.isEmpty()) {
          setPathTextFieldError(false)
          switchToBreadcrumbs()
          return
        }
        scope.launch {
          withContext(Dispatchers.IO) {
            val path = runCatching { Path.of(text) }.getOrNull()
            val exists = path != null && runCatching { Files.exists(path) }.getOrDefault(false)
            if (path == null || !exists) {
              runOnEdt {
                setPathTextFieldError(true)
                if (barPanel.isShowing) {
                  barCardLayout.show(barPanel, PATH_CARD)
                  pathTextField.requestFocusInWindow()
                }
              }
              return@withContext
            }
            val forceShowHidden = !fileTree.areHiddensShown() && hasHiddenSegment(path)
            runOnEdt {
              setPathTextFieldError(false)
              switchToBreadcrumbs()
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

      private fun updateBreadcrumbs(selection: List<Path?>) {
        switchToBreadcrumbs()
        val file = selection.firstOrNull()
        if (file == null) {
          currentCrumbs = emptyList()
          breadcrumbs.setCrumbs(emptyList())
          return
        }
        val crumbs = mutableListOf<FileCrumb>()
        var current: Path? = file
        while (current != null) {
          crumbs.add(0, FileCrumb(current))
          current = current.parent
        }
        currentCrumbs = crumbs
        breadcrumbs.setCrumbs(crumbs)
      }

      private var currentDirectoryPopup: JBPopup? = null

      private fun showDirectoryPopup(directory: Path, event: MouseEvent) {
        if (currentDirectoryPopup?.isVisible == true) return
        val showHidden = fileTree.areHiddensShown()
        scope.launch {
          withContext(Dispatchers.IO) {
            val children = NioFileChooserUtil.safeGetChildren(directory, showHidden, false)
            if (!children.isEmpty()) {
              runOnEdt {
                if (currentDirectoryPopup?.isVisible == true) return@runOnEdt
                val popup = JBPopupFactory.getInstance()
                  .createPopupChooserBuilder(children)
                  .setRenderer(listCellRenderer("") {
                    icon(AllIcons.Nodes.Folder)
                    text(value.name)
                  })
                  .setItemChosenCallback { chosen -> fileTree.select(chosen) { fileTree.expand(chosen, null) } }
                  .createPopup()
                currentDirectoryPopup = popup
                popup.show(RelativePoint(event))
              }
            }
          }
        }
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

      private class FileCrumb(val file: Path) : Crumb {
        @NlsSafe
        override fun getText(): String {
          val contributor = UniversalFileChooserContributor.findOwner(file)
          return contributor?.getFileName(file) ?: file.name.ifEmpty { file.pathString }
        }

        @NlsSafe
        override fun getTooltip(): String = file.pathString
      }
    }
  }

  @Suppress("ForbiddenInSuspectContextMethod") // ModalityState.any() is required.
  private fun runOnEdt(runnable: Runnable) {
    ApplicationManager.getApplication().invokeLater(runnable, ModalityState.any())
  }

}
