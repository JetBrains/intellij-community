// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.filter

import com.intellij.ide.HelpTooltip
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.impl.FieldInplaceActionButtonLook
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.SearchFieldWithExtension
import com.intellij.ui.components.TextComponentEmptyText
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.ui.VcsLogActionIds
import com.intellij.vcs.log.ui.VcsLogColorManager
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys
import com.intellij.vcs.log.ui.filter.FilterModel.SingleFilterModel
import com.intellij.vcs.log.ui.filter.VcsLogFilterUiEx.VcsLogFilterListener
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.visible.VisiblePack
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import com.intellij.xml.util.XmlStringUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.util.function.Consumer
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import kotlin.math.max

open class VcsLogClassicFilterUi(private val logData: VcsLogData,
                                 filterConsumer: Consumer<VcsLogFilterCollection>,
                                 private val uiProperties: MainVcsLogUiProperties,
                                 private val colorManager: VcsLogColorManager,
                                 filters: VcsLogFilterCollection?,
                                 parentDisposable: Disposable) : VcsLogFilterUiEx {
  private var dataPack: VcsLogDataPack = VisiblePack.EMPTY
  private var visibleRoots: Collection<VirtualFile>? = null

  protected val branchFilterModel: BranchFilterModel
  protected val userFilterModel: SingleFilterModel<VcsLogUserFilter>
  protected val dateFilterModel: DateFilterModel
  protected val structureFilterModel: FileFilterModel
  protected val textFilterModel: TextFilterModel
  protected val parentFilterModel: ParentFilterModel

  private val textFilterField: VcsLogTextFilterField

  private val filterListenerDispatcher = EventDispatcher.create(VcsLogFilterListener::class.java)

  init {
    branchFilterModel = BranchFilterModel(::dataPack, logData.storage, logData.roots, ::visibleRoots, uiProperties, filters)
    userFilterModel = UserFilterModel(uiProperties, filters)
    dateFilterModel = DateFilterModel(uiProperties, filters)
    structureFilterModel = FileFilterModel(logData.logProviders.keys, uiProperties, filters)
    textFilterModel = TextFilterModel(uiProperties, filters, parentDisposable)
    parentFilterModel = ParentFilterModel(uiProperties, logData.logProviders, ::visibleRoots, filters)

    val field = TextFilterField(textFilterModel, parentDisposable)
    val toolbar = createTextActionsToolbar(field.textEditor)
    textFilterField = MyVcsLogTextFilterField(SearchFieldWithExtension(toolbar.component, field))

    val models = arrayOf(branchFilterModel, userFilterModel, dateFilterModel, structureFilterModel, textFilterModel, parentFilterModel)
    for (model in models) {
      model.addSetFilterListener {
        filterConsumer.accept(getFilters())
        filterListenerDispatcher.multicaster.onFiltersChanged()
        onStructureFilterChanged(structureFilterModel.rootFilter, structureFilterModel.structureFilter)
      }
    }
  }

  private fun onStructureFilterChanged(rootFilter: VcsLogRootFilter?, structureFilter: VcsLogStructureFilter?) {
    visibleRoots = if (rootFilter != null || structureFilter != null)
      VcsLogUtil.getAllVisibleRoots(logData.roots, rootFilter, structureFilter)
    else null
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
    val actions = listOfNotNull(createBranchComponent(), createUserComponent(), createDateComponent(), createStructureFilterComponent(),
                                createGraphComponent())
    return DefaultActionGroup(actions)
  }

  @RequiresEdt
  override fun getFilters(): VcsLogFilterCollection {
    val filters = buildList {
      addAll(branchFilterModel.filtersList)
      addAll(textFilterModel.filtersList)
      addAll(structureFilterModel.filtersList)
      add(dateFilterModel.getFilter())
      add(userFilterModel.getFilter())
      add(parentFilterModel.getFilter())
    }.filterNotNull()
    return VcsLogFilterObject.collection(*filters.toTypedArray())
  }

  @RequiresEdt
  override fun setFilters(collection: VcsLogFilterCollection) {
    branchFilterModel.setFilter(collection)
    structureFilterModel.setFilter(collection)
    textFilterModel.setFilter(collection)
    dateFilterModel.setFilter(collection.get(VcsLogFilterCollection.DATE_FILTER))
    userFilterModel.setFilter(collection.get(VcsLogFilterCollection.USER_FILTER))
    parentFilterModel.setFilter(collection.get(VcsLogFilterCollection.PARENT_FILTER))
  }

  protected open fun createBranchComponent(): AnAction? {
    return MainUiActionComponent(VcsLogBundle.messagePointer("vcs.log.branch.filter.action.text")) {
      BranchFilterPopupComponent(uiProperties, branchFilterModel).initUi()
    }
  }

  protected fun createUserComponent(): AnAction? {
    return MainUiActionComponent(VcsLogBundle.messagePointer("vcs.log.user.filter.action.text")) {
      UserFilterPopupComponent(uiProperties, logData, userFilterModel).initUi()
    }
  }

  protected fun createDateComponent(): AnAction? {
    return MainUiActionComponent(VcsLogBundle.messagePointer("vcs.log.date.filter.action.text")) {
      DateFilterPopupComponent(dateFilterModel).initUi()
    }
  }

  protected fun createStructureFilterComponent(): AnAction? {
    return MainUiActionComponent(VcsLogBundle.messagePointer("vcs.log.path.filter.action.text")) {
      StructureFilterPopupComponent(uiProperties, structureFilterModel, colorManager).initUi()
    }
  }

  protected fun createGraphComponent(): AnAction? {
    return VcsLogGraphOptionsChooserGroup(parentFilterModel)
  }

  override fun addFilterListener(listener: VcsLogFilterListener) {
    filterListenerDispatcher.addListener(listener)
  }

  @ApiStatus.Internal
  protected class MainUiActionComponent(dynamicText: Supplier<String>, private val componentCreator: Supplier<out JComponent>) :
    VcsLogPopupComponentAction(dynamicText) {
    override fun createCustomComponent(presentation: Presentation, place: String): JComponent = componentCreator.get()
    override fun getTargetComponent(e: AnActionEvent) = e.getData(VcsLogInternalDataKeys.MAIN_UI)?.toolbar
  }

  private inner class UserFilterModel(uiProperties: MainVcsLogUiProperties, filters: VcsLogFilterCollection?) :
    SingleFilterModel<VcsLogUserFilter>(VcsLogFilterCollection.USER_FILTER, uiProperties, filters) {

    override fun createFilter(values: List<String>): VcsLogUserFilter = VcsLogFilterObject.fromUserNames(values, logData)
    override fun getFilterValues(filter: VcsLogUserFilter): List<String> = ArrayList(filter.valuesAsText)
  }

  private inner class TextFilterField(private val textFilterModel: TextFilterModel, parentDisposable: Disposable) :
    SearchTextField(VCS_LOG_TEXT_FILTER_HISTORY), UiDataProvider {

    init {
      text = textFilterModel.text
      textEditor.emptyText.setText(VcsLogBundle.message("vcs.log.filter.text.hash.empty.text"))
      TextComponentEmptyText.setupPlaceholderVisibility(textEditor)
      textEditor.addActionListener { applyFilter(true) }
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

    override fun getMinimumSize(): Dimension {
      val size = super.getMinimumSize()
      size.width = max(size.width, JBUIScale.scale(150))
      return size
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

    override fun uiDataSnapshot(sink: DataSink) {
      sink[VcsLogInternalDataKeys.LOG_UI_PROPERTIES] = uiProperties
    }
  }

  companion object {
    private const val VCS_LOG_TEXT_FILTER_HISTORY = "Vcs.Log.Text.Filter.History"

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
      toolbar.isReservePlaceAutoPopupIcon = false
      toolbar.targetComponent = editor
      toolbar.updateActionsImmediately()
      return toolbar
    }
  }
}
