// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.configurable

import com.intellij.icons.AllIcons
import com.intellij.ide.impl.isTrusted
import com.intellij.ide.util.treeView.FileNameComparator
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.progress.util.ProgressIndicatorWithDelayedPresentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.changes.HierarchicalFilePathComparator
import com.intellij.openapi.vcs.impl.DefaultVcsRootPolicy
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl
import com.intellij.openapi.vcs.roots.VcsRootErrorsFinder
import com.intellij.openapi.vcs.update.AbstractCommonUpdateAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.*
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.ui.table.TableView
import com.intellij.util.FontUtil
import com.intellij.util.UriUtil
import com.intellij.util.ui.*
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Component
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.io.File
import javax.swing.*
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

class VcsDirectoryConfigurationPanel(private val project: Project) : JPanel(), Disposable {
  private val POSTPONE_MAPPINGS_LOADING_PANEL = ProgressIndicatorWithDelayedPresentation.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS

  private val isEditingDisabled = project.isDefault

  private val vcsManager: ProjectLevelVcsManager = ProjectLevelVcsManager.getInstance(project)
  private val vcsConfiguration: VcsConfiguration = VcsConfiguration.getInstance(project)
  private val sharedProjectSettings: VcsSharedProjectSettings = VcsSharedProjectSettings.getInstance(project)

  private val allSupportedVcss: List<AbstractVcs> = vcsManager.allSupportedVcss.asList()
  private val vcsRootCheckers: Map<String, VcsRootChecker> =
    VcsRootChecker.EXTENSION_POINT_NAME.extensionList.associateBy { it.supportedVcs.name }

  private val tableLoadingPanel: JBLoadingPanel
  private val mappingTable: TableView<RecordInfo>
  private val mappingTableModel: ListTableModel<RecordInfo>
  private val directoryRenderer: MyDirectoryRenderer

  private val vcsComboBox: ComboBox<AbstractVcs?>

  private val detectVcsMappingsCheckBox: JCheckBox

  private val scopeFilterConfigurable: VcsUpdateInfoScopeFilterConfigurable

  private var rootDetectionIndicator: ProgressIndicator? = null

  private class MyDirectoryRenderer(private val project: Project) : ColoredTableCellRenderer() {
    override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
      value as RecordInfo
      val textAttributes = getAttributes(value)
      toolTipText = null

      if (!selected && value.isUnregistered()) {
        background = UIUtil.getDecoratedRowColor()
      }

      when (value) {
        is RecordInfo.MappingInfo -> {
          val presentablePath = getPresentablePath(project, value.mapping)
          SpeedSearchUtil.appendFragmentsForSpeedSearch(table, presentablePath, textAttributes, selected, this)
        }
        is RecordInfo.Header -> {
          append(value.label, textAttributes)
        }
      }

      if (value is RecordInfo.RegisteredMappingInfo && value.mapping.isDefaultMapping) {
        val roots = collectDefaultMappedRoots(project, value.mapping.vcs)
        if (roots.isNotEmpty()) {
          append(FontUtil.spaceAndThinSpace(), textAttributes)
          append(VcsBundle.message("project.detected.n.roots.presentation", roots.size), SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
          toolTipText = roots
            .map { VcsUtil.getFilePath(it) }
            .sortedWith(HierarchicalFilePathComparator.NATURAL)
            .map { StringUtil.escapeXmlEntities(it.presentableUrl) }
            .joinToString(UIUtil.BR)
        }
      }
    }
  }

  private class MyVcsRenderer(private val info: RecordInfo, private val allSupportedVcss: List<AbstractVcs>) : ColoredTableCellRenderer() {
    override fun customizeCellRenderer(table: JTable,
                                       value: Any?,
                                       selected: Boolean,
                                       hasFocus: Boolean,
                                       row: Int,
                                       column: Int) {
      val textAttributes = getAttributes(info)

      if (!selected && info.isUnregistered()) {
        background = UIUtil.getDecoratedRowColor()
      }

      if (info is RecordInfo.MappingInfo) {
        val vcsName = info.mapping.vcs
        if (vcsName.isEmpty()) {
          append(VcsBundle.message("none.vcs.presentation"), textAttributes)
        }
        else {
          val vcs = allSupportedVcss.find { vcsName == it.name }
          append(vcs?.displayName ?: VcsBundle.message("unknown.vcs.presentation", vcsName), textAttributes)
        }
      }
    }
  }

  init {
    mappingTable = TableView()
    mappingTable.setShowGrid(false)
    mappingTable.intercellSpacing = JBUI.emptySize()
    TableSpeedSearch.installOn(mappingTable) { info: Any? ->
      if (info is RecordInfo.MappingInfo) getPresentablePath(project, info.mapping) else ""
    }

    scopeFilterConfigurable = VcsUpdateInfoScopeFilterConfigurable(project, vcsConfiguration)

    // don't start loading automatically
    tableLoadingPanel = JBLoadingPanel(BorderLayout(), this, POSTPONE_MAPPINGS_LOADING_PANEL * 2)

    detectVcsMappingsCheckBox = JCheckBox(VcsBundle.message("directory.mapping.checkbox.detect.vcs.mappings.automatically"))

    layout = BorderLayout()
    add(createMainComponent())

    directoryRenderer = MyDirectoryRenderer(project)

    mappingTableModel = ListTableModel(MyDirectoryColumnInfo(), MyVcsColumnInfo())
    mappingTable.setModelAndUpdateColumns(mappingTableModel)

    initializeModel()

    vcsComboBox = buildVcsesComboBox(project, allSupportedVcss)
    vcsComboBox.addItemListener {
      if (mappingTable.isEditing) {
        mappingTable.stopEditing()
      }
    }

    mappingTable.rowHeight = vcsComboBox.preferredSize.height
    if (isEditingDisabled) {
      mappingTable.isEnabled = false
    }
  }

  override fun dispose() {
    rootDetectionIndicator?.cancel()
    scopeFilterConfigurable.disposeUIResources()
  }

  private fun initializeModel() {
    scopeFilterConfigurable.reset()

    val items = mutableListOf<RecordInfo>()
    items.addAll(vcsManager.directoryMappings.map { createRegisteredInfo(it) })
    setDisplayedMappings(items)

    detectVcsMappingsCheckBox.isSelected = sharedProjectSettings.isDetectVcsMappingsAutomatically

    scheduleUnregisteredRootsLoading()
  }

  private fun scheduleUnregisteredRootsLoading() {
    if (project.isDefault || !project.isTrusted()) return
    rootDetectionIndicator?.cancel()
    if (!VcsUtil.shouldDetectVcsMappingsFor(project)) return

    rootDetectionIndicator = BackgroundTaskUtil.executeAndTryWait(
      { indicator: ProgressIndicator ->
        val unregisteredRoots = VcsRootErrorsFinder.getInstance(project).getOrFind()
          .filter { error -> error.type == VcsRootError.Type.UNREGISTERED_ROOT }
          .map { error -> error.mapping }
          .toList()
        return@executeAndTryWait Runnable {
          if (indicator.isCanceled) return@Runnable
          tableLoadingPanel.stopLoading()
          if (!unregisteredRoots.isEmpty()) {
            val items = mappingTableModel.items.toMutableList()
            items.removeIf { it.isUnregistered() }
            items.addAll(unregisteredRoots.map { RecordInfo.UnregisteredMapping(it) })
            setDisplayedMappings(items)
          }
        }
      }, { tableLoadingPanel.startLoading() }, POSTPONE_MAPPINGS_LOADING_PANEL.toLong(), false)
  }

  private fun createRegisteredInfo(mapping: VcsDirectoryMapping): RecordInfo {
    if (isMappingValid(mapping)) {
      return RecordInfo.ValidMapping(mapping)
    }
    else {
      return RecordInfo.InvalidMapping(mapping)
    }
  }

  private fun isMappingValid(mapping: VcsDirectoryMapping): Boolean {
    if (mapping.isDefaultMapping) return true
    val checker = vcsRootCheckers[mapping.vcs] ?: return true
    val directory = LocalFileSystem.getInstance().findFileByPath(mapping.directory)
    return directory != null && checker.validateRoot(directory)
  }

  private fun addMapping() {
    val dlg = VcsMappingConfigurationDialog(project, VcsBundle.message("directory.mapping.add.title"))
    if (dlg.showAndGet()) {
      val items = mappingTableModel.items.toMutableList()
      items.add(createRegisteredInfo(dlg.mapping))
      setDisplayedMappings(items)
    }
  }

  private fun addSelectedUnregisteredMappings(infos: List<RecordInfo.UnregisteredMapping>) {
    val items = mappingTableModel.items.toMutableList()
    for (info in infos) {
      items.remove(info)
      items.add(createRegisteredInfo(info.mapping))
    }
    setDisplayedMappings(items)
  }

  private fun setDisplayedMappings(mappings: List<RecordInfo>) {
    val items = mappings.toMutableList()

    // update group headers
    items.removeIf { it is RecordInfo.Header }
    if (items.any { it is RecordInfo.UnregisteredMapping }) {
      items.add(RecordInfo.UnregisteredHeader)
    }
    items.sortWith(RECORD_INFO_COMPARATOR)

    mappingTableModel.items = items
  }

  private fun editMapping() {
    val row = mappingTable.selectedRow
    val info = mappingTable.getRow(row) as? RecordInfo.RegisteredMappingInfo ?: return

    val dlg = VcsMappingConfigurationDialog(project, VcsBundle.message("directory.mapping.remove.title"))
    dlg.mapping = info.mapping
    if (dlg.showAndGet()) {
      val items = mappingTableModel.items.toMutableList()
      items[row] = createRegisteredInfo(dlg.mapping)
      setDisplayedMappings(items)
    }
  }

  private fun removeMapping() {
    val index = mappingTable.selectionModel.minSelectionIndex
    val selection = mappingTable.selection.filterIsInstance<RecordInfo.RegisteredMappingInfo>()

    val items = mappingTableModel.items.toMutableList()
    items.removeAll(selection.toSet())
    val removedValidRoots = selection
      .filter { info -> info is RecordInfo.ValidMapping && vcsRootCheckers[info.mapping.vcs] != null }
    items.addAll(removedValidRoots.map { RecordInfo.UnregisteredMapping(it.mapping) })
    setDisplayedMappings(items)

    selectIndex(index)
  }

  private fun selectIndex(index: Int) {
    val newItems = mappingTableModel.items
    if (newItems.isEmpty()) return

    val toSelect = if (index >= newItems.size) newItems.size - 1 else index
    mappingTable.selectionModel.setSelectionInterval(toSelect, toSelect)
  }

  private fun createMainComponent(): JComponent {
    val panel = JPanel(GridBagLayout())
    val gb = GridBag()
      .setDefaultInsets(JBUI.insets(0, 0, UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP))
      .setDefaultWeightX(1.0)
      .setDefaultFill(GridBagConstraints.HORIZONTAL)

    if (!project.isTrusted()) {
      val notificationPanel = EditorNotificationPanel(LightColors.RED, EditorNotificationPanel.Status.Error)
      notificationPanel.text = VcsBundle.message("configuration.project.not.trusted.label")
      panel.add(notificationPanel, gb.nextLine().next())
    }

    val mappingsTable = createMappingsTable()
    tableLoadingPanel.add(mappingsTable)
    panel.add(tableLoadingPanel, gb.nextLine().next().fillCell().weighty(1.0))

    panel.add(createProjectMappingDescription(), gb.nextLine().next())

    val detectVcsMappingsHintLabel = JLabel(AllIcons.General.ContextHelp).apply {
      border = JBUI.Borders.emptyLeft(4)
      toolTipText = VcsBundle.message("directory.mapping.checkbox.detect.vcs.mappings.automatically.hint")
    }
    panel.add(JBUI.Panels.simplePanel(detectVcsMappingsCheckBox).addToRight(detectVcsMappingsHintLabel),
              gb.nextLine().next().fillCellNone().anchor(GridBagConstraints.WEST))

    if (!AbstractCommonUpdateAction.showsCustomNotification(vcsManager.allActiveVcss.asList())) {
      panel.add(scopeFilterConfigurable.createComponent(), gb.nextLine().next())
    }
    return panel
  }

  private fun createMappingsTable(): JComponent {
    val panelForTable = ToolbarDecorator.createDecorator(mappingTable, null)
      .setAddActionUpdater { !isEditingDisabled && rootsOfOneKindInSelection() }
      .setAddAction {
        val unregisteredRoots = getSelectedUnregisteredRoots()
        if (unregisteredRoots.isEmpty()) {
          addMapping()
        }
        else {
          addSelectedUnregisteredMappings(unregisteredRoots)
        }
      }
      .setEditActionUpdater { !isEditingDisabled && onlyRegisteredRootsInSelection() }
      .setEditAction {
        editMapping()
      }
      .setRemoveActionUpdater { !isEditingDisabled && onlyRegisteredRootsInSelection() }
      .setRemoveAction {
        removeMapping()
      }
      .disableUpDownActions()
      .createPanel()
    panelForTable.preferredSize = JBDimension(-1, 200)
    return panelForTable
  }

  private fun getSelectedUnregisteredRoots(): List<RecordInfo.UnregisteredMapping> {
    return mappingTable.selection.filterIsInstance<RecordInfo.UnregisteredMapping>()
  }

  private fun rootsOfOneKindInSelection(): Boolean {
    val selection = mappingTable.selection
    if (selection.isEmpty()) {
      return true
    }
    if (selection.size == 1 && selection.iterator().next() is RecordInfo.Header) {
      return true
    }
    val selectedRegisteredRoots = getSelectedRegisteredRoots()
    return selectedRegisteredRoots.size == selection.size || selectedRegisteredRoots.isEmpty()
  }

  private fun getSelectedRegisteredRoots(): List<RecordInfo.RegisteredMappingInfo> {
    val selection = mappingTable.selection
    return selection.filterIsInstance<RecordInfo.RegisteredMappingInfo>()
  }

  private fun onlyRegisteredRootsInSelection(): Boolean {
    return getSelectedRegisteredRoots().size == mappingTable.selection.size
  }

  private fun createProjectMappingDescription(): JComponent {
    val projectMessage = HtmlBuilder()
      .append(VcsDirectoryMapping.PROJECT_CONSTANT.get())
      .append(" - ")
      .append(DefaultVcsRootPolicy.getInstance(project).projectConfigurationMessage.replace('\n', ' '))
      .wrapWithHtmlBody().toString()

    val label = JBLabel(projectMessage)
    label.componentStyle = UIUtil.ComponentStyle.SMALL
    label.fontColor = UIUtil.FontColor.BRIGHTER
    label.border = JBUI.Borders.empty(2, 5, 2, 0)
    return label
  }

  fun reset() {
    initializeModel()
  }

  @Throws(ConfigurationException::class)
  fun apply() {
    adjustIgnoredRootsSettings()
    vcsManager.directoryMappings = getModelMappings()
    scopeFilterConfigurable.apply()
    sharedProjectSettings.isDetectVcsMappingsAutomatically = detectVcsMappingsCheckBox.isSelected
    initializeModel()
  }

  private fun adjustIgnoredRootsSettings() {
    val newMappings = getModelMappings()
    val previousMappings = vcsManager.directoryMappings
    vcsConfiguration.addIgnoredUnregisteredRoots(previousMappings
                                                   .filter { mapping -> !newMappings.contains(mapping) && !mapping.isDefaultMapping }
                                                   .map { mapping -> mapping.directory })
    vcsConfiguration.removeFromIgnoredUnregisteredRoots(newMappings.map { obj: VcsDirectoryMapping -> obj.directory })
  }

  fun isModified(): Boolean {
    if (scopeFilterConfigurable.isModified) return true
    return getModelMappings() != vcsManager.directoryMappings ||
           detectVcsMappingsCheckBox.isSelected != sharedProjectSettings.isDetectVcsMappingsAutomatically
  }

  private fun getModelMappings(): List<VcsDirectoryMapping> {
    return mappingTableModel.items.mapNotNull { info ->
      if (info is RecordInfo.RegisteredMappingInfo) info.mapping else null
    }
  }

  private inner class MyDirectoryColumnInfo : ColumnInfo<RecordInfo, RecordInfo>(
    VcsBundle.message("column.info.configure.vcses.directory")) {
    override fun valueOf(mapping: RecordInfo): RecordInfo {
      return mapping
    }

    override fun getRenderer(vcsDirectoryMapping: RecordInfo): TableCellRenderer {
      return directoryRenderer
    }
  }

  private inner class MyVcsColumnInfo : ColumnInfo<RecordInfo, String>(VcsBundle.message("column.name.configure.vcses.vcs")) {
    override fun valueOf(info: RecordInfo): String {
      return (info as? RecordInfo.MappingInfo)?.mapping?.vcs.orEmpty()
    }

    override fun isCellEditable(info: RecordInfo): Boolean {
      return info is RecordInfo.RegisteredMappingInfo
    }

    override fun setValue(info: RecordInfo, newVcs: String) {
      if (info is RecordInfo.RegisteredMappingInfo) {
        val oldMapping = info.mapping
        info.mapping = VcsDirectoryMapping(oldMapping.directory, newVcs, oldMapping.rootSettings)
      }
    }

    override fun getRenderer(info: RecordInfo): TableCellRenderer {
      return MyVcsRenderer(info, allSupportedVcss)
    }

    override fun getEditor(o: RecordInfo): TableCellEditor {
      return object : AbstractTableCellEditor() {
        override fun getCellEditorValue(): Any {
          val selectedVcs = vcsComboBox.item
          return if (selectedVcs == null) "" else selectedVcs.name //NON-NLS handled by custom renderer
        }

        override fun getTableCellEditorComponent(table: JTable, value: Any, isSelected: Boolean, row: Int, column: Int): Component {
          vcsComboBox.selectedItem = value
          return vcsComboBox
        }
      }
    }

    override fun getMaxStringValue(): String? {
      var maxString: String? = null
      for (vcs in allSupportedVcss) {
        val name = vcs.displayName
        if (maxString == null || maxString.length < name.length) {
          maxString = name
        }
      }
      return maxString
    }

    override fun getAdditionalWidth(): Int {
      return UIUtil.DEFAULT_HGAP
    }
  }

  companion object {
    @JvmStatic
    fun buildVcsesComboBox(project: Project): ComboBox<AbstractVcs?> {
      val allVcses = ProjectLevelVcsManager.getInstance(project).allSupportedVcss
      return buildVcsesComboBox(project, allVcses.asList())
    }

    private fun buildVcsesComboBox(project: Project, allVcses: List<AbstractVcs>): ComboBox<AbstractVcs?> {
      val comboBox = ComboBox((allVcses + null).sortedWith(SuggestedVcsComparator.create(project)).toTypedArray())
      comboBox.renderer = textListCellRenderer(VcsBundle.message("none.vcs.presentation")) { obj: AbstractVcs -> obj.displayName }
      return comboBox
    }
  }
}

private fun collectDefaultMappedRoots(project: Project, vcsName: String): List<VirtualFile> {
  return ProjectLevelVcsManagerImpl.getInstanceImpl(project).vcsRootObjectsForDefaultMapping
    .filter { it.vcs?.name == vcsName }
    .map { it.path }
}

private fun getPresentablePath(project: Project, mapping: VcsDirectoryMapping): @NlsSafe String {
  if (mapping.isDefaultMapping) {
    return VcsDirectoryMapping.PROJECT_CONSTANT.get()
  }
  return getPresentablePath(project, mapping.directory)
}

private fun getPresentablePath(project: Project, directory: String): @NlsSafe String {
  val baseDir = project.baseDir
  if (baseDir == null) {
    return File(directory).path
  }

  val directoryFile = File(UriUtil.trimTrailingSlashes(directory).removeSuffix("\\") + "/")
  val ioBase = File(baseDir.path)
  if (directoryFile.isAbsolute && !FileUtil.isAncestor(ioBase, directoryFile, false)) {
    return File(directory).path
  }

  val relativePath = FileUtil.getRelativePath(ioBase, directoryFile)
  if ("." == relativePath || relativePath == null) {
    return ioBase.path
  }
  else {
    return "$relativePath ($ioBase)"
  }
}

private fun getAttributes(info: RecordInfo): SimpleTextAttributes {
  when (info) {
    is RecordInfo.InvalidMapping -> {
      return SimpleTextAttributes.ERROR_ATTRIBUTES
    }
    is RecordInfo.UnregisteredMapping -> {
      return SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES
    }
    is RecordInfo.Header -> {
      return SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD or SimpleTextAttributes.STYLE_SMALLER, null)
    }
    is RecordInfo.ValidMapping -> {
      return SimpleTextAttributes.REGULAR_ATTRIBUTES
    }
  }
}

private val RECORD_INFO_COMPARATOR: Comparator<RecordInfo> = compareBy<RecordInfo> { info ->
  when (info) {
    is RecordInfo.ValidMapping -> 0
    is RecordInfo.InvalidMapping -> 1
    is RecordInfo.UnregisteredHeader -> 2
    is RecordInfo.UnregisteredMapping -> 3
  }
}.thenBy(FileNameComparator.getInstance()) { info -> (info as? RecordInfo.MappingInfo)?.mapping?.directory }

private sealed interface RecordInfo {
  sealed class MappingInfo : RecordInfo {
    abstract val mapping: VcsDirectoryMapping
    override fun toString(): String = mapping.toString()
  }

  sealed class RegisteredMappingInfo(override var mapping: VcsDirectoryMapping) : MappingInfo()

  sealed class Header(val label: @Nls String) : RecordInfo {
    override fun toString(): String = ""
  }

  class ValidMapping(mapping: VcsDirectoryMapping) : RegisteredMappingInfo(mapping)
  class InvalidMapping(mapping: VcsDirectoryMapping) : RegisteredMappingInfo(mapping)
  class UnregisteredMapping(override val mapping: VcsDirectoryMapping) : MappingInfo()
  object UnregisteredHeader : Header(VcsBundle.message("unregistered.roots.label"))

  fun isUnregistered() = this is UnregisteredHeader || this is UnregisteredMapping
}
