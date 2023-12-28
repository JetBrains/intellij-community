// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.filter

import com.intellij.execution.ui.FragmentedSettingsUtil
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.impl.FieldInplaceActionButtonLook
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ClientProperty
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.SearchFieldWithExtension
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogDataPack
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogUserFilter
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.ui.VcsLogActionIds
import com.intellij.vcs.log.ui.VcsLogColorManager
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys
import com.intellij.vcs.log.ui.filter.FilterModel.SingleFilterModel
import com.intellij.vcs.log.ui.filter.VcsLogFilterUiEx.VcsLogFilterListener
import com.intellij.vcs.log.visible.VisiblePack
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import com.intellij.xml.util.XmlStringUtil
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.util.function.Consumer
import java.util.function.Supplier
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

  @RequiresEdt
  override fun setFilters(collection: VcsLogFilterCollection) {
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
      FragmentedSettingsUtil.setupPlaceholderVisibility(textEditor);
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
