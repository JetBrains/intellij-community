package com.intellij.settingsSync.core.config

import com.intellij.idea.AppMode
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.settingsSync.core.SettingsSyncBundle.message
import com.intellij.settingsSync.core.*
import com.intellij.ui.CheckBoxList
import com.intellij.ui.CheckBoxListListener
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.PlatformUtils
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ThreeStateCheckBox
import com.intellij.util.ui.ThreeStateCheckBox.State
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JPanel

internal class SettingsSyncPanelHolder() {
  private lateinit var panel : DialogPanel
  private var isCrossIdeSyncEnabled = false
  internal val crossSyncSupported = AtomicBooleanProperty(true)

  fun setSyncSettings(syncSettings: SettingsSyncState?) {
    val notNullState = syncSettings ?: SettingsSyncStateHolder()
    SyncCategoryHolder.updateState(notNullState)
  }

  fun setSyncScopeSettings(settings: SettingsSyncLocalState?) {
    isCrossIdeSyncEnabled = settings?.isCrossIdeSyncEnabled ?: false
  }

  fun createCombinedSyncSettingsPanel(
    syncLabel: @Nls String,
    syncSettings: SettingsSyncState?,
    syncScopeSettings: SettingsSyncLocalState?,
  ): DialogPanel {
    setSyncSettings(syncSettings)
    setSyncScopeSettings(syncScopeSettings)
    val categoriesPanel = createSyncCategoriesPanel(syncLabel)
    val syncScopePanel = createSyncScopePanel()

    panel = panel {
      row {
        cell(categoriesPanel)
          .onApply(categoriesPanel::apply)
          .onReset(categoriesPanel::reset)
          .onIsModified {
            categoriesPanel.isModified()
          }
      }
      row {
        cell(syncScopePanel)
          .onApply(syncScopePanel::apply)
          .onReset(syncScopePanel::reset)
          .onIsModified(syncScopePanel::isModified)
      }.visibleIf(crossSyncSupported)
      onApply {
        // do nothing, handled by descendants
      }
      onIsModified {
        categoriesPanel.isModified() || syncScopePanel.isModified()
      }

    }
    return panel
  }

  private fun createSyncScopePanel(): DialogPanel {
    return panel {
      onApply {
        SettingsSyncLocalSettings.getInstance().isCrossIdeSyncEnabled = isCrossIdeSyncEnabled
        SettingsSyncEvents.getInstance().fireSettingsChanged(
          SyncSettingsEvent.CrossIdeSyncStateChanged(SettingsSyncLocalSettings.getInstance().isCrossIdeSyncEnabled))
      }
      row {
        topGap(TopGap.MEDIUM)
        label(message("settings.cross.product.sync"))
      }
      buttonsGroup(indent = true) {
        row {
          val productName = ApplicationNamesInfo.getInstance().fullProductName
          radioButton(message("settings.cross.product.sync.choice.only.this.product", productName), false)
        }
        row {
          val allProductsText = if (PlatformUtils.getPlatformPrefix() == "AndroidStudio") {
            message("settings.cross.product.sync.choice.all.products.android")
          } else {
            message("settings.cross.product.sync.choice.all.products")
          }
          radioButton(allProductsText, true)
        }
      }.bind(::isCrossIdeSyncEnabled)
    }
  }

  private fun Row.createSyncCategoryCheckbox(holder: SyncCategoryHolder) {
    val checkBox = checkBox(holder.name)
    if (!isModifiable(holder)) {
      checkBox.enabled(false)
      comment(holder.description)
      return
    }

    checkBox
      .bindSelected(holder::isSynchronized)
      .onReset {
        holder.reset()
        checkBox.component.isSelected = holder.isSynchronized
      }
      .onApply {
        holder.apply()
      }
      .onIsModified {
        holder.isModified()
      }
    comment(holder.description)
  }

  private fun Row.createTopSyncCategoryCheckbox(holder: SyncCategoryHolder) {
    fun Row.addComment(): Cell<JEditorPane> {
      return comment(holder.description).visible(!holder.description.isEmpty())
    }

    val topCheckBox = ThreeStateCheckBox(holder.name)
    topCheckBox.isThirdStateEnabled = false
    topCheckBox.isEnabled = isModifiable(holder)
    if (!isModifiable(holder)) {
      cell(topCheckBox)
      topCheckBox.state = State.NOT_SELECTED
      addComment()
      return
    }

    cell(topCheckBox)
      .onReset {
        holder.reset()
        topCheckBox.state = getGroupState(holder)
      }
      .onApply {
        holder.isSynchronized = topCheckBox.state != State.NOT_SELECTED
        holder.apply()
      }
      .onIsModified { holder.isModified() }
    val c = addComment()
    val subcategoryLink = configureLink(holder.secondaryGroup!!, c.component.font.size2D) {
      topCheckBox.state = getGroupState(holder)
      holder.isSynchronized = topCheckBox.state != State.NOT_SELECTED
    }
    val subcategoryLinkCell = cell(subcategoryLink)
    subcategoryLinkCell
      .visible(holder.secondaryGroup!!.getDescriptors().size > 1 || !holder.secondaryGroup!!.isComplete())
      .onReset {
        subcategoryLinkCell.visible(holder.secondaryGroup!!.getDescriptors().size > 1 || !holder.secondaryGroup!!.isComplete())
        subcategoryLink.isEnabled = holder.secondaryGroup!!.isComplete() || holder.isSynchronized
      }
    topCheckBox.addActionListener {
      holder.isSynchronized = topCheckBox.state != State.NOT_SELECTED
      holder.secondaryGroup!!.getDescriptors().forEach {
        it.isSelected = holder.isSynchronized
      }
      subcategoryLink.isEnabled = holder.secondaryGroup!!.isComplete() || holder.isSynchronized
    }
  }

  private fun createSyncCategoriesPanel(syncLabel: @Nls String): DialogPanel {
    return panel {
      onApply {
        SettingsSyncSettings.getInstance().updateCategories(
          SyncCategoryHolder.disabledCategories,
          SyncCategoryHolder.disabledSubcategories
        )
        SettingsSyncEvents.getInstance().fireCategoriesChanged()
      }
      row {
        label(syncLabel)
      }
      for (holder in SyncCategoryHolder.allHolders) {
        indent {
          row {
            if (holder.secondaryGroup == null) {
              createSyncCategoryCheckbox(holder)
            }
            else {
              createTopSyncCategoryCheckbox(holder)
            }
          }
        }
      }
    }
  }

  // IJPL-173541 Disable everything except for plugins from Setting Sync in Remote Development
  private fun isModifiable(holder: SyncCategoryHolder) : Boolean {
    return !AppMode.isRemoteDevHost() || holder.descriptor.category == SettingsCategory.PLUGINS
  }

  private fun getGroupState(descriptor: SyncCategoryHolder): State {
    val group = descriptor.secondaryGroup
    if (group == null) {
      return if (descriptor.isSynchronized) State.SELECTED else State.NOT_SELECTED
    }
    if (!group.isComplete() && !descriptor.isSynchronized) return State.NOT_SELECTED
    val descriptors = group.getDescriptors()
    if (descriptors.isEmpty()) return State.NOT_SELECTED
    val isFirstSelected = descriptors.first().isSelected
    descriptors.forEach {
      if (it.isSelected != isFirstSelected) return State.DONT_CARE
    }
    return when {
      isFirstSelected -> State.SELECTED
      group.isComplete() -> State.NOT_SELECTED
      else -> State.DONT_CARE
    }
  }

  private fun configureLink(group: SyncSubcategoryGroup,
                            fontSize: Float,
                            onCheckBoxChange: () -> Unit): JComponent {
    val actionLink = ActionLink(message("subcategory.config.link")) {}
    actionLink.withFont(actionLink.font.deriveFont(fontSize))
    actionLink.addActionListener {
      showSubcategories(actionLink, group.getDescriptors(), onCheckBoxChange)
    }
    actionLink.setDropDownLinkIcon()
    return actionLink
  }

  private fun showSubcategories(owner: JComponent,
                                descriptors: List<SettingsSyncSubcategoryDescriptor>,
                                onCheckBoxChange: () -> Unit) {
    val panel = JPanel(BorderLayout())
    panel.border = JBUI.Borders.empty()
    val checkboxList = PluginsCheckboxList(descriptors) { i: Int, isSelected: Boolean ->
      descriptors[i].isSelected = isSelected
      onCheckBoxChange()
    }
    descriptors.forEach { checkboxList.addItem(it, it.name, it.isSelected) }
    val scrollPane = JBScrollPane(checkboxList)
    panel.add(scrollPane, BorderLayout.CENTER)
    scrollPane.border = JBUI.Borders.empty(5)
    scrollPane.viewportBorder = JBUI.Borders.emptyRight(JBUI.scale(8))
    val chooserBuilder = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, checkboxList)
    chooserBuilder.createPopup().showUnderneathOf(owner)
  }

  private class PluginsCheckboxList(
    val descriptors: List<SettingsSyncSubcategoryDescriptor>,
    listener : CheckBoxListListener) : CheckBoxList<SettingsSyncSubcategoryDescriptor>(listener) {


    override fun getBackground(isSelected: Boolean): Color? {
      return super.getBackground(false)
    }

    override fun adjustRendering(rootComponent: JComponent,
                                 checkBox: JCheckBox?,
                                 index: Int,
                                 selected: Boolean,
                                 hasFocus: Boolean): JComponent {
      if (descriptors[index].isSubGroupEnd) {
        val itemWrapper = JPanel()
        itemWrapper.layout = BoxLayout(itemWrapper, BoxLayout.Y_AXIS)
        itemWrapper.add(rootComponent)
        itemWrapper.add(SeparatorComponent(5, JBUI.CurrentTheme.Popup.separatorColor(), null))
        return itemWrapper
      }
      return rootComponent
    }
  }
}