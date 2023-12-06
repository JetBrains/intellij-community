// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.configurable

import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.progress.util.ProgressIndicatorWithDelayedPresentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.impl.DefaultVcsRootPolicy
import com.intellij.openapi.vcs.roots.VcsRootErrorsFinder
import com.intellij.openapi.vcs.update.AbstractCommonUpdateAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.*
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.ui.table.TableView
import com.intellij.util.UriUtil
import com.intellij.util.ui.*
import com.intellij.vcsUtil.VcsUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.io.File
import java.util.*
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

class VcsDirectoryConfigurationPanel(private val project: Project) : JPanel(), Disposable {
  private val POSTPONE_MAPPINGS_LOADING_PANEL = ProgressIndicatorWithDelayedPresentation.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS

  private val isEditingDisabled = project.isDefault

  private val vcsManager: ProjectLevelVcsManager = ProjectLevelVcsManager.getInstance(project)
  private val vcsConfiguration: VcsConfiguration = VcsConfiguration.getInstance(project)

  private val allSupportedVcss: List<AbstractVcs> = vcsManager.allSupportedVcss.asList()
  private val vcsRootCheckers: Map<String, VcsRootChecker> =
    VcsRootChecker.EXTENSION_POINT_NAME.extensionList.associateBy { it.supportedVcs.name }

  private val tableLoadingPanel: JBLoadingPanel
  private val mappingTable: TableView<MapInfo>
  private val mappingTableModel: ListTableModel<MapInfo>
  private val directoryRenderer: MyDirectoryRenderer

  private val vcsComboBox: ComboBox<AbstractVcs?>

  private val scopeFilterConfigurable: VcsUpdateInfoScopeFilterConfigurable

  private var rootDetectionIndicator: ProgressIndicator? = null

  private class MapInfo(var mapping: VcsDirectoryMapping, val type: Type) {
    companion object {
      val SEPARATOR: MapInfo = MapInfo(VcsDirectoryMapping("SEPARATOR", "SEP"), Type.SEPARATOR) //NON-NLS
      val COMPARATOR: Comparator<MapInfo> = Comparator { o1: MapInfo, o2: MapInfo ->
        if (o1.type.isRegistered() && o2.type.isRegistered() ||
            o1.type == Type.UNREGISTERED && o2.type == Type.UNREGISTERED) {
          return@Comparator Comparing.compare(o1.mapping.directory, o2.mapping.directory)
        }
        o1.type.compareTo(o2.type)
      }

      fun unregistered(mapping: VcsDirectoryMapping): MapInfo {
        return MapInfo(mapping, Type.UNREGISTERED)
      }

      fun registered(mapping: VcsDirectoryMapping, valid: Boolean): MapInfo {
        return MapInfo(mapping, if (valid) Type.NORMAL else Type.INVALID)
      }
    }

    enum class Type {
      NORMAL,
      INVALID,
      SEPARATOR,
      UNREGISTERED;

      fun isRegistered(): Boolean = this == NORMAL || this == INVALID
    }

    override fun toString(): String {
      if (type == Type.SEPARATOR) return ""
      return mapping.toString()
    }
  }

  private class MyDirectoryRenderer(private val project: Project) : ColoredTableCellRenderer() {
    override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
      if (value is MapInfo) {
        val textAttributes = getAttributes(value)

        if (!selected && (value == MapInfo.SEPARATOR || value.type == MapInfo.Type.UNREGISTERED)) {
          background = UIUtil.getDecoratedRowColor()
        }

        if (value == MapInfo.SEPARATOR) {
          append(VcsBundle.message("unregistered.roots.label"), textAttributes)
          return
        }

        val presentablePath = getPresentablePath(project, value.mapping)
        SpeedSearchUtil.appendFragmentsForSpeedSearch(table, presentablePath, textAttributes, selected, this)
      }
    }

    companion object {
      fun getPresentablePath(project: Project, mapping: VcsDirectoryMapping): @NlsSafe String {
        if (mapping.isDefaultMapping) {
          return VcsDirectoryMapping.PROJECT_CONSTANT.get()
        }

        val directory = mapping.directory

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

      internal fun getAttributes(info: MapInfo): SimpleTextAttributes {
        if (info == MapInfo.SEPARATOR) {
          return SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD or SimpleTextAttributes.STYLE_SMALLER, null)
        }
        else if (info.type == MapInfo.Type.INVALID) {
          return SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.RED)
        }
        else if (info.type == MapInfo.Type.UNREGISTERED) {
          return SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, JBColor.GRAY)
        }
        else {
          return SimpleTextAttributes.REGULAR_ATTRIBUTES
        }
      }
    }
  }

  private class MyVcsRenderer(private val info: MapInfo, private val allSupportedVcss: List<AbstractVcs>) : ColoredTableCellRenderer() {
    override fun customizeCellRenderer(table: JTable,
                                       value: Any?,
                                       selected: Boolean,
                                       hasFocus: Boolean,
                                       row: Int,
                                       column: Int) {
      if (info == MapInfo.SEPARATOR) {
        if (!selected) {
          background = UIUtil.getDecoratedRowColor()
        }
        return
      }

      if (info.type == MapInfo.Type.UNREGISTERED && !selected) {
        background = UIUtil.getDecoratedRowColor()
      }

      val vcsName = info.mapping.vcs
      val text: String
      if (vcsName.isEmpty()) {
        text = VcsBundle.message("none.vcs.presentation")
      }
      else {
        val vcs = allSupportedVcss.find { vcsName == it.name }
        text = vcs?.displayName ?: VcsBundle.message("unknown.vcs.presentation", vcsName)
      }
      append(text, MyDirectoryRenderer.getAttributes(info))
    }
  }

  init {
    mappingTable = TableView()
    mappingTable.setShowGrid(false)
    mappingTable.intercellSpacing = JBUI.emptySize()
    TableSpeedSearch.installOn(mappingTable) { info: Any? ->
      if (info is MapInfo) MyDirectoryRenderer.getPresentablePath(project, info.mapping) else ""
    }

    scopeFilterConfigurable = VcsUpdateInfoScopeFilterConfigurable(project, vcsConfiguration)

    // don't start loading automatically
    tableLoadingPanel = JBLoadingPanel(BorderLayout(), this, POSTPONE_MAPPINGS_LOADING_PANEL * 2)

    layout = BorderLayout()
    add(createMainComponent())

    directoryRenderer = MyDirectoryRenderer(project)

    mappingTableModel = ListTableModel(MyDirectoryColumnInfo(), MyVcsColumnInfo())
    mappingTable.setModelAndUpdateColumns(mappingTableModel)

    initializeModel()

    vcsComboBox = buildVcsesComboBox(allSupportedVcss)
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

    val mappings: MutableList<MapInfo> = mutableListOf()
    for (mapping in ProjectLevelVcsManager.getInstance(project).directoryMappings) {
      mappings.add(MapInfo.registered(VcsDirectoryMapping(mapping.directory, mapping.vcs, mapping.rootSettings),
                                      isMappingValid(mapping)))
    }
    mappingTableModel.items = mappings

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
            mappingTableModel.addRow(MapInfo.SEPARATOR)
            for (mapping in unregisteredRoots) {
              mappingTableModel.addRow(MapInfo.unregistered(mapping))
            }
          }
        }
      }, { tableLoadingPanel.startLoading() }, POSTPONE_MAPPINGS_LOADING_PANEL.toLong(), false)
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
      addMapping(dlg.mapping)
    }
  }

  private fun addMapping(mapping: VcsDirectoryMapping) {
    val items: MutableList<MapInfo> = mappingTableModel.items.toMutableList()
    items.add(MapInfo.registered(VcsDirectoryMapping(mapping.directory, mapping.vcs, mapping.rootSettings),
                                 isMappingValid(mapping)))
    items.sortWith(MapInfo.COMPARATOR)
    mappingTableModel.setItems(items)
  }


  private fun addSelectedUnregisteredMappings(infos: List<MapInfo>) {
    val items: MutableList<MapInfo> = mappingTableModel.items.toMutableList()
    for (info in infos) {
      items.remove(info)
      items.add(MapInfo.registered(info.mapping, isMappingValid(info.mapping)))
    }
    sortAndAddSeparatorIfNeeded(items)
    mappingTableModel.items = items
  }

  private fun sortAndAddSeparatorIfNeeded(items: MutableList<MapInfo>) {
    var hasUnregistered = false
    var hasSeparator = false
    for (item in items) {
      if (item.type == MapInfo.Type.UNREGISTERED) {
        hasUnregistered = true
      }
      else if (item.type == MapInfo.Type.SEPARATOR) {
        hasSeparator = true
      }
    }
    if (!hasUnregistered && hasSeparator) {
      items.remove(MapInfo.SEPARATOR)
    }
    else if (hasUnregistered && !hasSeparator) {
      items.add(MapInfo.SEPARATOR)
    }
    items.sortWith(MapInfo.COMPARATOR)
  }

  private fun editMapping() {
    val dlg = VcsMappingConfigurationDialog(project, VcsBundle.message("directory.mapping.remove.title"))
    val row = mappingTable.selectedRow
    val mapping = mappingTable.getRow(row).mapping
    dlg.mapping = mapping
    if (dlg.showAndGet()) {
      setMapping(row, dlg.mapping)
    }
  }

  private fun setMapping(row: Int, mapping: VcsDirectoryMapping) {
    val items: MutableList<MapInfo> = mappingTableModel.items.toMutableList()
    items[row] = MapInfo.registered(mapping, isMappingValid(mapping))
    items.sortWith(MapInfo.COMPARATOR)
    mappingTableModel.setItems(items)
  }

  private fun removeMapping() {
    val mappings = mappingTableModel.items.toMutableList()
    var index = mappingTable.selectionModel.minSelectionIndex
    val selection = mappingTable.selection
    mappings.removeAll(selection.toSet())

    val removedValidRoots: Collection<MapInfo> = selection.mapNotNull { info ->
      if (info.type == MapInfo.Type.NORMAL && vcsRootCheckers[info.mapping.vcs] != null) MapInfo.unregistered(info.mapping) else null
    }
    mappings.addAll(removedValidRoots)
    sortAndAddSeparatorIfNeeded(mappings)

    mappingTableModel.items = mappings
    if (mappings.size > 0) {
      if (index >= mappings.size) {
        index = mappings.size - 1
      }
      mappingTable.selectionModel.setSelectionInterval(index, index)
    }
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

  private fun getSelectedUnregisteredRoots(): List<MapInfo> {
    return mappingTable.selection.filter { info -> info.type == MapInfo.Type.UNREGISTERED }
  }

  private fun rootsOfOneKindInSelection(): Boolean {
    val selection = mappingTable.selection
    if (selection.isEmpty()) {
      return true
    }
    if (selection.size == 1 && selection.iterator().next().type == MapInfo.Type.SEPARATOR) {
      return true
    }
    val selectedRegisteredRoots = getSelectedRegisteredRoots()
    return selectedRegisteredRoots.size == selection.size || selectedRegisteredRoots.isEmpty()
  }

  private fun getSelectedRegisteredRoots(): List<MapInfo> {
    val selection = mappingTable.selection
    return selection.filter { info -> info.type == MapInfo.Type.NORMAL || info.type == MapInfo.Type.INVALID }
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
    return getModelMappings() != vcsManager.directoryMappings
  }

  private fun getModelMappings(): List<VcsDirectoryMapping> {
    return mappingTableModel.items.mapNotNull { info ->
      if (info == MapInfo.SEPARATOR || info.type == MapInfo.Type.UNREGISTERED) null else info.mapping
    }
  }

  private inner class MyDirectoryColumnInfo : ColumnInfo<MapInfo, MapInfo>(VcsBundle.message("column.info.configure.vcses.directory")) {
    override fun valueOf(mapping: MapInfo): MapInfo {
      return mapping
    }

    override fun getRenderer(vcsDirectoryMapping: MapInfo): TableCellRenderer {
      return directoryRenderer
    }
  }

  private inner class MyVcsColumnInfo : ColumnInfo<MapInfo, String>(VcsBundle.message("column.name.configure.vcses.vcs")) {
    override fun valueOf(`object`: MapInfo): String {
      return `object`.mapping.vcs
    }

    override fun isCellEditable(info: MapInfo): Boolean {
      return info != MapInfo.SEPARATOR && info.type != MapInfo.Type.UNREGISTERED
    }

    override fun setValue(o: MapInfo, aValue: String) {
      o.mapping = VcsDirectoryMapping(o.mapping.directory, aValue, o.mapping.rootSettings)
    }

    override fun getRenderer(info: MapInfo): TableCellRenderer {
      return MyVcsRenderer(info, allSupportedVcss)
    }

    override fun getEditor(o: MapInfo): TableCellEditor {
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
      return buildVcsesComboBox(allVcses.asList())
    }

    private fun buildVcsesComboBox(allVcses: List<AbstractVcs>): ComboBox<AbstractVcs?> {
      val comboBox = ComboBox((allVcses + null).toTypedArray())
      comboBox.renderer = SimpleListCellRenderer.create(VcsBundle.message("none.vcs.presentation")) { obj: AbstractVcs -> obj.displayName }
      return comboBox
    }
  }
}
