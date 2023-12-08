// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.filter

import com.intellij.ide.HelpTooltip
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.impl.FieldInplaceActionButtonLook
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ClientProperty
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.SearchFieldWithExtension
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.*
import com.intellij.vcs.log.VcsLogRangeFilter.RefRange
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.VcsLogStorage
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogUiProperties.PropertiesChangeListener
import com.intellij.vcs.log.impl.VcsLogUiProperties.VcsLogUiProperty
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector
import com.intellij.vcs.log.ui.VcsLogActionIds
import com.intellij.vcs.log.ui.VcsLogColorManager
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys
import com.intellij.vcs.log.ui.filter.FilterModel.PairFilterModel
import com.intellij.vcs.log.ui.filter.FilterModel.SingleFilterModel
import com.intellij.vcs.log.ui.filter.VcsLogFilterUiEx.VcsLogFilterListener
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.visible.VisiblePack
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import com.intellij.vcsUtil.VcsUtil
import com.intellij.xml.util.XmlStringUtil
import org.jetbrains.annotations.NonNls
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.util.function.Consumer
import java.util.function.Supplier
import java.util.regex.Pattern
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.event.DocumentEvent

open class VcsLogClassicFilterUi(private val logData: VcsLogData,
                                 filterConsumer: Consumer<VcsLogFilterCollection>,
                                 private val uiProperties: MainVcsLogUiProperties,
                                 private val colorManager: VcsLogColorManager,
                                 filters: VcsLogFilterCollection?,
                                 parentDisposable: Disposable) : VcsLogFilterUiEx {
  private var dataPack: VcsLogDataPack

  protected val branchFilterModel: BranchFilterModel
  protected val userFilterModel: SingleFilterModel<VcsLogUserFilter>
  protected val dateFilterModel: DateFilterModel
  protected val structureFilterModel: FileFilterModel
  protected val textFilterModel: TextFilterModel

  private val textFilterField: VcsLogTextFilterField

  private val filterListenerDispatcher = EventDispatcher.create(VcsLogFilterListener::class.java)

  init {
    dataPack = VisiblePack.EMPTY

    val dataPackGetter = Supplier { dataPack }
    branchFilterModel = BranchFilterModel(dataPackGetter, logData.storage, logData.roots, uiProperties, filters)
    userFilterModel = UserFilterModel(uiProperties, filters)
    dateFilterModel = DateFilterModel(uiProperties, filters)
    structureFilterModel = FileFilterModel(logData.logProviders.keys, uiProperties, filters)
    textFilterModel = TextFilterModel(uiProperties, filters, parentDisposable)

    val field = TextFilterField(textFilterModel, parentDisposable)
    val toolbar = createTextActionsToolbar(field.textEditor)
    textFilterField = MyVcsLogTextFilterField(SearchFieldWithExtension(toolbar.component, field))

    val models = arrayOf(branchFilterModel, userFilterModel, dateFilterModel, structureFilterModel, textFilterModel)
    for (model in models) {
      model.addSetFilterListener {
        filterConsumer.accept(getFilters())
        filterListenerDispatcher.multicaster.onFiltersChanged()
        branchFilterModel.onStructureFilterChanged(structureFilterModel.rootFilter, structureFilterModel.structureFilter)
      }
    }
  }

  override fun updateDataPack(newDataPack: VcsLogDataPack) {
    dataPack = newDataPack
  }

  override fun getTextFilterComponent(): VcsLogTextFilterField = textFilterField

  private class MyActionButton(action: AnAction, presentation: Presentation) :
    ActionButton(action, presentation, "Vcs.Log.SearchTextField", ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {

    init {
      updateIcon()
    }

    override fun getPopState(): Int {
      return if (isSelected) SELECTED else super.getPopState()
    }

    override fun getIcon(): Icon {
      if (isEnabled && isSelected) {
        val selectedIcon = myPresentation.selectedIcon
        if (selectedIcon != null) return selectedIcon
      }
      return super.getIcon()
    }
  }

  private class MyVcsLogTextFilterField(private val searchField: SearchFieldWithExtension) : VcsLogTextFilterField {
    override val component: JComponent
      get() = searchField

    override val focusedComponent: JComponent
      get() = searchField.textField

    override var text: String
      get() = searchField.textField.text
      set(s) {
        searchField.textField.text = s
      }
  }

  override fun createActionGroup(): ActionGroup {
    val actions = listOfNotNull(createBranchComponent(), createUserComponent(), createDateComponent(), createStructureFilterComponent())
    return DefaultActionGroup(actions)
  }

  @RequiresEdt
  override fun getFilters(): VcsLogFilterCollection {
    return VcsLogFilterObject.collection(branchFilterModel.branchFilter, branchFilterModel.revisionFilter,
                                         branchFilterModel.rangeFilter,
                                         textFilterModel.filter1, textFilterModel.filter2,
                                         structureFilterModel.filter1, structureFilterModel.filter2,
                                         dateFilterModel.getFilter(), userFilterModel.getFilter())
  }

  override fun setFilters(collection: VcsLogFilterCollection) {
    ThreadingAssertions.assertEventDispatchThread()
    branchFilterModel.setFilter(BranchFilters(collection.get(VcsLogFilterCollection.BRANCH_FILTER),
                                              collection.get(VcsLogFilterCollection.REVISION_FILTER),
                                              collection.get(VcsLogFilterCollection.RANGE_FILTER)))
    structureFilterModel.setFilter(FilterPair(collection.get(VcsLogFilterCollection.STRUCTURE_FILTER),
                                              collection.get(VcsLogFilterCollection.ROOT_FILTER)))
    dateFilterModel.setFilter(collection.get(VcsLogFilterCollection.DATE_FILTER))
    textFilterModel.setFilter(FilterPair(collection.get(VcsLogFilterCollection.TEXT_FILTER),
                                         collection.get(VcsLogFilterCollection.HASH_FILTER)))
    userFilterModel.setFilter(collection.get(VcsLogFilterCollection.USER_FILTER))
  }

  protected open fun createBranchComponent(): AnAction? {
    return FilterActionComponent(VcsLogBundle.messagePointer("vcs.log.branch.filter.action.text")) {
      BranchFilterPopupComponent(uiProperties, branchFilterModel).initUi()
    }
  }

  protected fun createUserComponent(): AnAction? {
    return FilterActionComponent(VcsLogBundle.messagePointer("vcs.log.user.filter.action.text")) {
      UserFilterPopupComponent(uiProperties, logData, userFilterModel).initUi()
    }
  }

  protected fun createDateComponent(): AnAction? {
    return FilterActionComponent(VcsLogBundle.messagePointer("vcs.log.date.filter.action.text")) {
      DateFilterPopupComponent(dateFilterModel).initUi()
    }
  }

  protected fun createStructureFilterComponent(): AnAction? {
    return FilterActionComponent(VcsLogBundle.messagePointer("vcs.log.path.filter.action.text")) {
      StructureFilterPopupComponent(uiProperties, structureFilterModel, colorManager).initUi()
    }
  }

  override fun addFilterListener(listener: VcsLogFilterListener) {
    filterListenerDispatcher.addListener(listener)
  }

  protected class FilterActionComponent(dynamicText: Supplier<String?>, private val componentCreator: Supplier<out JComponent>) :
    DumbAwareAction(dynamicText), CustomComponentAction {

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent = componentCreator.get()

    override fun actionPerformed(e: AnActionEvent) {
      val vcsLogUi = e.getData(VcsLogInternalDataKeys.MAIN_UI)
      if (vcsLogUi == null) return

      val actionComponent = UIUtil.uiTraverser(vcsLogUi.toolbar).traverse().find { component: Component? ->
        ClientProperty.get(component, CustomComponentAction.ACTION_KEY) === this
      }
      if (actionComponent is VcsLogPopupComponent) {
        actionComponent.showPopupMenu()
      }
    }
  }

  class BranchFilterModel internal constructor(private val dataPackProvider: Supplier<out VcsLogDataPack>,
                                               private val storage: VcsLogStorage,
                                               private val roots: Collection<VirtualFile>,
                                               properties: MainVcsLogUiProperties,
                                               filters: VcsLogFilterCollection?) : FilterModel<BranchFilters>(properties) {
    var visibleRoots: Collection<VirtualFile>? = null
      private set

    init {
      if (filters != null) {
        saveFilterToProperties(BranchFilters(filters.get(VcsLogFilterCollection.BRANCH_FILTER),
                                             filters.get(VcsLogFilterCollection.REVISION_FILTER),
                                             filters.get(VcsLogFilterCollection.RANGE_FILTER)))
      }
    }

    override fun setFilter(filter: BranchFilters?) {
      var newFilters = filter
      if (newFilters != null && newFilters.isEmpty()) newFilters = null

      var anyFilterDiffers = false

      if (newFilters?.branchFilter != _filter?.branchFilter) {
        if (newFilters?.branchFilter != null) VcsLogUsageTriggerCollector.triggerFilterSet(VcsLogFilterCollection.BRANCH_FILTER.name)
        anyFilterDiffers = true
      }
      if (newFilters?.revisionFilter != _filter?.revisionFilter) {
        if (newFilters?.revisionFilter != null) VcsLogUsageTriggerCollector.triggerFilterSet(VcsLogFilterCollection.REVISION_FILTER.name)
        anyFilterDiffers = true
      }
      if (newFilters?.rangeFilter != _filter?.rangeFilter) {
        if (newFilters?.rangeFilter != null) VcsLogUsageTriggerCollector.triggerFilterSet(VcsLogFilterCollection.RANGE_FILTER.name)
        anyFilterDiffers = true
      }
      if (anyFilterDiffers) {
        super.setFilter(newFilters)
      }
    }

    override fun saveFilterToProperties(filter: BranchFilters?) {
      uiProperties.saveFilterValues(VcsLogFilterCollection.BRANCH_FILTER.name, filter?.branchFilter?.let { getBranchFilterValues(it) })
      uiProperties.saveFilterValues(VcsLogFilterCollection.REVISION_FILTER.name,
                                    filter?.revisionFilter?.let { getRevisionFilterValues(it) })
      uiProperties.saveFilterValues(VcsLogFilterCollection.RANGE_FILTER.name, filter?.rangeFilter?.let { getRangeFilterValues(it) })
    }

    override fun getFilterFromProperties(): BranchFilters? {
      val branchFilterValues = uiProperties.getFilterValues(VcsLogFilterCollection.BRANCH_FILTER.name)
      val branchFilter = branchFilterValues?.let { createBranchFilter(it) }

      val revisionFilterValues = uiProperties.getFilterValues(VcsLogFilterCollection.REVISION_FILTER.name)
      val revisionFilter = revisionFilterValues?.let { createRevisionFilter(it) }

      val rangeFilterValues = uiProperties.getFilterValues(VcsLogFilterCollection.RANGE_FILTER.name)
      val rangeFilter = rangeFilterValues?.let { createRangeFilter(it) }

      if (branchFilter == null && revisionFilter == null && rangeFilter == null) return null
      return BranchFilters(branchFilter, revisionFilter, rangeFilter)
    }

    fun onStructureFilterChanged(rootFilter: VcsLogRootFilter?, structureFilter: VcsLogStructureFilter?) {
      if (rootFilter == null && structureFilter == null) {
        visibleRoots = null
      }
      else {
        visibleRoots = VcsLogUtil.getAllVisibleRoots(roots, rootFilter, structureFilter)
      }
    }

    val dataPack: VcsLogDataPack
      get() = dataPackProvider.get()

    private fun createBranchFilter(values: List<String>): VcsLogBranchFilter {
      return VcsLogFilterObject.fromBranchPatterns(values, ContainerUtil.map2Set(dataPack.refs.branches) { it.name })
    }

    private fun createRevisionFilter(values: List<String>): VcsLogRevisionFilter {
      val pattern = Pattern.compile("\\[(.*)\\](" + VcsLogUtil.HASH_REGEX.pattern() + ")")
      return VcsLogFilterObject.fromCommits(values.mapNotNull { s: String ->
        val matcher = pattern.matcher(s)
        if (!matcher.matches()) {
          if (VcsLogUtil.isFullHash(s)) {
            val commitId = findCommitId(HashImpl.build(s))
            if (commitId != null) return@mapNotNull commitId
          }
          LOG.warn("Could not parse '$s' while creating revision filter")
          return@mapNotNull null
        }
        val result = matcher.toMatchResult()
        val root = LocalFileSystem.getInstance().findFileByPath(result.group(1))
        if (root == null) {
          LOG.warn("Root '" + result.group(1) + "' does not exist")
          return@mapNotNull null
        }
        else if (!roots.contains(root)) {
          LOG.warn("Root '" + result.group(1) + "' is not registered")
          return@mapNotNull null
        }
        CommitId(HashImpl.build(result.group(2)), root)
      })
    }

    private fun findCommitId(hash: Hash): CommitId? {
      for (root in roots) {
        val commitId = CommitId(hash, root)
        if (storage.containsCommit(commitId)) {
          return commitId
        }
      }
      return null
    }

    fun createFilterFromPresentation(values: List<String>): BranchFilters? {
      val hashes = mutableListOf<String>()
      val branches = mutableListOf<String>()
      val ranges = mutableListOf<String>()
      for (s in values) {
        val twoDots = s.indexOf("..")
        if (twoDots > 0 && twoDots == s.lastIndexOf("..")) {
          ranges.add(s)
        }
        else if (VcsLogUtil.isFullHash(s)) {
          hashes.add(s)
        }
        else {
          branches.add(s)
        }
      }
      val branchFilter = if (branches.isEmpty()) null else createBranchFilter(branches)
      val hashFilter = if (hashes.isEmpty()) null else createRevisionFilter(hashes)
      val refDiffFilter = if (ranges.isEmpty()) null else createRangeFilter(ranges)
      return BranchFilters(branchFilter, hashFilter, refDiffFilter)
    }

    var branchFilter: VcsLogBranchFilter?
      get() = getFilter()?.branchFilter
      set(branchFilter) {
        setFilter(BranchFilters(branchFilter, null, null))
      }

    val revisionFilter: VcsLogRevisionFilter?
      get() = getFilter()?.revisionFilter

    var rangeFilter: VcsLogRangeFilter?
      get() = getFilter()?.rangeFilter
      set(rangeFilter) {
        setFilter(BranchFilters(null, null, rangeFilter))
      }

    companion object {
      private const val TWO_DOTS = ".."

      private fun createRangeFilter(values: List<String>): VcsLogRangeFilter? {
        val ranges = values.mapNotNull { value: String ->
          val twoDots = value.indexOf(TWO_DOTS)
          if (twoDots <= 0) {
            LOG.error("Incorrect range filter value: $values")
            return@mapNotNull null
          }
          RefRange(value.substring(0, twoDots), value.substring(twoDots + TWO_DOTS.length))
        }
        if (ranges.isEmpty()) return null
        return VcsLogFilterObject.fromRange(ranges)
      }

      private fun getBranchFilterValues(filter: VcsLogBranchFilter): List<String?> {
        return ArrayList(ContainerUtil.sorted<@NlsSafe String?>(filter.textPresentation))
      }

      private fun getRevisionFilterValues(filter: VcsLogRevisionFilter): List<String> {
        return filter.heads.map { id -> "[" + id.root.path + "]" + id.hash.asString() }
      }

      private fun getRangeFilterValues(rangeFilter: VcsLogRangeFilter): List<String> {
        return ArrayList(rangeFilter.getTextPresentation())
      }

      private fun getRevisionFilter2Presentation(filter: VcsLogRevisionFilter): List<String> {
        return filter.heads.map { id -> id.hash.asString() }
      }

      @JvmStatic
      fun getFilterPresentation(filters: BranchFilters): List<String> {
        val branchFilterValues = filters.branchFilter?.let { getBranchFilterValues(it) } ?: emptyList()
        val revisionFilterValues = filters.revisionFilter?.let { getRevisionFilter2Presentation(it) } ?: emptyList()
        val rangeFilterValues = filters.rangeFilter?.let { getRangeFilterValues(filters.rangeFilter) } ?: emptyList()
        return ContainerUtil.concat(branchFilterValues, revisionFilterValues, rangeFilterValues)
      }
    }
  }

  protected class TextFilterModel internal constructor(properties: MainVcsLogUiProperties,
                                                       filters: VcsLogFilterCollection?,
                                                       parentDisposable: Disposable) :
    PairFilterModel<VcsLogTextFilter, VcsLogHashFilter>(VcsLogFilterCollection.TEXT_FILTER, VcsLogFilterCollection.HASH_FILTER, properties,
                                                        filters) {
    init {
      if (filters != null) {
        val textFilter = filters.get(VcsLogFilterCollection.TEXT_FILTER)
        if (textFilter != null) {
          uiProperties.set(MainVcsLogUiProperties.TEXT_FILTER_MATCH_CASE, textFilter.matchesCase())
          uiProperties.set(MainVcsLogUiProperties.TEXT_FILTER_REGEX, textFilter.isRegex)
        }
      }
      val listener: PropertiesChangeListener = object : PropertiesChangeListener {
        override fun <T> onPropertyChanged(property: VcsLogUiProperty<T>) {
          if (MainVcsLogUiProperties.TEXT_FILTER_REGEX == property || MainVcsLogUiProperties.TEXT_FILTER_MATCH_CASE == property) {
            if (filter1 != null) {
              _filter = getFilterFromProperties()
              notifyFiltersChanged()
            }
          }
        }
      }
      properties.addChangeListener(listener, parentDisposable)
    }

    override fun getFilterFromProperties(): FilterPair<VcsLogTextFilter, VcsLogHashFilter>? {
      val filterPair: FilterPair<VcsLogTextFilter, VcsLogHashFilter>? = super.getFilterFromProperties()
      if (filterPair == null) return null

      var textFilter = filterPair.filter1
      var hashFilter = filterPair.filter2

      // check filters correctness
      if (textFilter != null && textFilter.text.isBlank()) {
        LOG.warn("Saved text filter is empty. Removing.")
        textFilter = null
      }

      if (textFilter != null) {
        val hashFilterFromText = VcsLogFilterObject.fromHash(textFilter.text)
        if (hashFilter != hashFilterFromText) {
          LOG.warn("Saved hash filter " + hashFilter + " is inconsistent with text filter." +
                   " Replacing with " + hashFilterFromText)
          hashFilter = hashFilterFromText
        }
      }
      else if (hashFilter != null && !hashFilter.hashes.isEmpty()) {
        textFilter = createTextFilter(StringUtil.join(hashFilter.hashes, " "))
        LOG.warn("Saved hash filter " + hashFilter +
                 " is inconsistent with empty text filter. Using text filter " + textFilter)
      }

      return FilterPair(textFilter, hashFilter)
    }

    val text: String get() = filter1?.text ?: ""

    override fun getFilter1Values(filter1: VcsLogTextFilter): List<String> = listOf(filter1.text)
    override fun getFilter2Values(filter2: VcsLogHashFilter): List<String> = ArrayList(filter2.hashes)

    override fun createFilter1(values: List<String>): VcsLogTextFilter = createTextFilter(values.first())
    override fun createFilter2(values: List<String>): VcsLogHashFilter = VcsLogFilterObject.fromHashes(values)

    private fun createTextFilter(text: String): VcsLogTextFilter {
      return VcsLogFilterObject.fromPattern(text,
                                            uiProperties.get(MainVcsLogUiProperties.TEXT_FILTER_REGEX),
                                            uiProperties.get(MainVcsLogUiProperties.TEXT_FILTER_MATCH_CASE))
    }

    fun setFilterText(text: String) {
      if (text.isBlank()) {
        setFilter(null)
      }
      else {
        val textFilter = createTextFilter(text)
        val hashFilter = VcsLogFilterObject.fromHash(text)
        setFilter(FilterPair(textFilter, hashFilter))
      }
    }
  }

  class FileFilterModel(val roots: Set<VirtualFile>, uiProperties: MainVcsLogUiProperties, filters: VcsLogFilterCollection?) :
    PairFilterModel<VcsLogStructureFilter, VcsLogRootFilter>(VcsLogFilterCollection.STRUCTURE_FILTER, VcsLogFilterCollection.ROOT_FILTER,
                                                             uiProperties, filters) {

    override fun getFilter1Values(filter1: VcsLogStructureFilter): List<String> = getFilterValues(filter1)
    override fun getFilter2Values(filter2: VcsLogRootFilter): List<String> = getRootFilterValues(filter2)

    override fun createFilter1(values: List<String>): VcsLogStructureFilter = createStructureFilter(values)
    override fun createFilter2(values: List<String>): VcsLogRootFilter? {
      val selectedRoots: MutableList<VirtualFile> = ArrayList()
      for (path in values) {
        val root = LocalFileSystem.getInstance().findFileByPath(path)
        if (root != null) {
          if (roots.contains(root)) {
            selectedRoots.add(root)
          }
          else {
            LOG.warn("Can not find VCS root for filtering $root")
          }
        }
        else {
          LOG.warn("Can not filter by root that does not exist $path")
        }
      }
      if (selectedRoots.isEmpty()) return null
      return VcsLogFilterObject.fromRoots(selectedRoots)
    }

    val rootFilter: VcsLogRootFilter?
      get() = filter2

    var structureFilter: VcsLogStructureFilter?
      get() = filter1
      private set(filter) {
        setFilter(FilterPair(filter, null))
      }

    companion object {
      private const val DIR: @NonNls String = "dir:"
      private const val FILE: @NonNls String = "file:"

      fun getRootFilterValues(filter: VcsLogRootFilter): List<String> {
        return filter.roots.map { it.path }
      }

      @JvmStatic
      fun getFilterValues(filter: VcsLogStructureFilter): List<String> {
        return filter.files.map { path -> (if (path.isDirectory) DIR else FILE) + path.path }
      }

      @JvmStatic
      fun createStructureFilter(values: List<String>): VcsLogStructureFilter {
        return VcsLogFilterObject.fromPaths(values.map { path -> extractPath(path) })
      }

      fun extractPath(path: String): FilePath {
        if (path.startsWith(DIR)) {
          return VcsUtil.getFilePath(path.substring(DIR.length), true)
        }
        else if (path.startsWith(FILE)) {
          return VcsUtil.getFilePath(path.substring(FILE.length), false)
        }
        return VcsUtil.getFilePath(path)
      }
    }
  }

  class DateFilterModel(uiProperties: MainVcsLogUiProperties, filters: VcsLogFilterCollection?) :
    SingleFilterModel<VcsLogDateFilter>(VcsLogFilterCollection.DATE_FILTER, uiProperties, filters) {

    override fun createFilter(values: List<String>): VcsLogDateFilter? {
      if (values.size != 2) {
        LOG.warn("Can not create date filter from $values before and after dates are required.")
        return null
      }
      val after = values[0]
      val before = values[1]
      try {
        return VcsLogFilterObject.fromDates(if (after.isEmpty()) 0 else after.toLong(),
                                            if (before.isEmpty()) 0 else before.toLong())
      }
      catch (e: NumberFormatException) {
        LOG.warn("Can not create date filter from $values")
      }
      return null
    }

    override fun getFilterValues(filter: VcsLogDateFilter): List<String> = getDateValues(filter)

    fun updateFilterFromProperties() {
      setFilter(getFilterFromProperties())
    }

    companion object {
      fun getDateValues(filter: VcsLogDateFilter): List<String> {
        val after = filter.after
        val before = filter.before
        return listOf(after?.time?.toString() ?: "", before?.time?.toString() ?: "")
      }
    }
  }

  private inner class UserFilterModel(uiProperties: MainVcsLogUiProperties, filters: VcsLogFilterCollection?) :
    SingleFilterModel<VcsLogUserFilter>(VcsLogFilterCollection.USER_FILTER, uiProperties, filters) {

    override fun createFilter(values: List<String>): VcsLogUserFilter = VcsLogFilterObject.fromUserNames(values, logData)
    override fun getFilterValues(filter: VcsLogUserFilter): List<String> = ArrayList(filter.valuesAsText)
  }

  private inner class TextFilterField(private val textFilterModel: TextFilterModel, parentDisposable: Disposable) :
    SearchTextField(VCS_LOG_TEXT_FILTER_HISTORY), DataProvider {

    init {
      text = textFilterModel.text
      textEditor.emptyText.setText(VcsLogBundle.message("vcs.log.filter.text.hash.empty.text"))
      textEditor.addActionListener { e: ActionEvent? -> applyFilter(true) }
      addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          if (isFilterOnTheFlyEnabled) applyFilter(false)
        }
      })
      textFilterModel.addSetFilterListener {
        val modelText = textFilterModel.text
        if (!isSameFilterAs(modelText)) text = modelText
      }

      textEditor.toolTipText = createTooltipText()
      Disposer.register(parentDisposable) { hidePopup() }
    }

    private fun applyFilter(addToHistory: Boolean) {
      textFilterModel.setFilterText(text)
      if (addToHistory) addCurrentTextToHistory()
    }

    override fun onFieldCleared() {
      textFilterModel.setFilter(null)
    }

    override fun onFocusLost() {
      if (!isSameFilterAs(textFilterModel.text)) applyFilter(isFilterOnTheFlyEnabled)
    }

    private fun isSameFilterAs(otherText: String): Boolean {
      val thisText = text
      if (thisText.isNullOrBlank()) return otherText.isBlank()
      return thisText == otherText
    }

    override fun getData(dataId: String): Any? {
      if (VcsLogInternalDataKeys.LOG_UI_PROPERTIES.`is`(dataId)) {
        return uiProperties
      }
      return null
    }
  }

  companion object {
    private const val VCS_LOG_TEXT_FILTER_HISTORY = "Vcs.Log.Text.Filter.History"
    private val LOG = Logger.getInstance(VcsLogClassicFilterUi::class.java)

    private fun createTooltipText(): @NlsContexts.Tooltip String {
      val text = VcsLogBundle.message("vcs.log.filter.text.hash.tooltip")
      val shortcut = HelpTooltip.getShortcutAsHtml(KeymapUtil.getFirstKeyboardShortcutText(VcsLogActionIds.VCS_LOG_FOCUS_TEXT_FILTER))
      return XmlStringUtil.wrapInHtml(text + shortcut)
    }

    private val isFilterOnTheFlyEnabled: Boolean
      get() = Registry.`is`("vcs.log.filter.text.on.the.fly")

    private fun createTextActionsToolbar(editor: JComponent?): ActionToolbar {
      val actionManager = ActionManager.getInstance()
      val textActionGroup = actionManager.getAction(VcsLogActionIds.TEXT_FILTER_SETTINGS_ACTION_GROUP) as ActionGroup
      val toolbar: ActionToolbarImpl = object : ActionToolbarImpl(ActionPlaces.VCS_LOG_TOOLBAR_PLACE, textActionGroup, true) {
        override fun createToolbarButton(action: AnAction,
                                         look: ActionButtonLook?,
                                         place: String,
                                         presentation: Presentation,
                                         minimumSize: Supplier<out Dimension?>): ActionButton {
          val button = MyActionButton(action, presentation)
          button.isFocusable = true
          applyToolbarLook(look, presentation, button)
          return button
        }
      }

      toolbar.setCustomButtonLook(FieldInplaceActionButtonLook())
      toolbar.setReservePlaceAutoPopupIcon(false)
      toolbar.targetComponent = editor
      toolbar.updateActionsImmediately()
      return toolbar
    }
  }
}
