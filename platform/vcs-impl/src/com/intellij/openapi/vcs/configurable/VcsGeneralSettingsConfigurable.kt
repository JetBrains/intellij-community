// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.configurable

import com.intellij.application.options.editor.checkBox
import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.BoundCompositeSearchableConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableEP
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsListener
import com.intellij.openapi.vcs.VcsShowConfirmationOption
import com.intellij.openapi.vcs.changes.RemoteRevisionsCache
import com.intellij.openapi.vcs.contentAnnotation.VcsContentAnnotationSettings
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx
import com.intellij.openapi.vcs.impl.VcsEP
import com.intellij.openapi.vcs.impl.projectlevelman.PersistentVcsSetting
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.util.containers.MultiMap
import com.intellij.util.containers.toMultiMap
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

/**
 * EP that allows appending options to the bottom of *File | Settings | Version Control | Confirmation* settings panel.
 *
 * @see com.intellij.openapi.options.UiDslUnnamedConfigurable
 */
internal class GeneralVcsSettingsProviderEP(project: Project) : ConfigurableEP<UnnamedConfigurable>(project) {
  /**
   * Allowed values: 'confirmations', anything else is considered to be 'other'.
   */
  @Attribute("location")
  var location: String? = "other"

  companion object {
    val VCS_SETTINGS_EP_NAME = ExtensionPointName<GeneralVcsSettingsProviderEP>("com.intellij.generalVcsSettingsExtension")
  }

  fun getLocationEnum(): Location = when (location?.lowercase()) {
    "confirmations" -> Location.Confirmations
    else -> Location.Other
  }
}

@ApiStatus.Internal
enum class Location {
  Confirmations,
  Other
}

@ApiStatus.Internal
class VcsGeneralSettingsConfigurable(val project: Project) : BoundCompositeSearchableConfigurable<UnnamedConfigurable>(
  message("configurable.VcsGeneralConfigurationConfigurable.display.name"),
  "project.propVCSSupport.Confirmation"
), Configurable.WithEpDependencies {
  private val extensions: ClearableLazyValue<MultiMap<Location, UnnamedConfigurable>> = ClearableLazyValue.create {
    GeneralVcsSettingsProviderEP.VCS_SETTINGS_EP_NAME.getExtensionList(project)
      .mapNotNull { ext -> ext.createConfigurable()?.let { ext.getLocationEnum() to it } }
      .toMultiMap()
  }

  override fun createConfigurables(): List<UnnamedConfigurable> =
    extensions.value.values().toList()

  override fun getDependencies() = listOf(VcsEP.EP_NAME, GeneralVcsSettingsProviderEP.VCS_SETTINGS_EP_NAME)

  override fun createPanel(): DialogPanel {
    val vcsManager = ProjectLevelVcsManagerEx.getInstanceEx(project)
    val vcsConfiguration = VcsConfiguration.getInstance(project)
    val contentAnnotationSettings = VcsContentAnnotationSettings.getInstance(project)

    val vcsListeners = mutableListOf<Runnable>()
    fun updateActiveVcses() {
      runInEdt {
        vcsListeners.forEach { it.run() }
      }
    }
    project.messageBus.connect(disposable!!).subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED,
                                                       VcsListener { updateActiveVcses() })
    VcsEP.EP_NAME.addChangeListener({ updateActiveVcses() }, disposable)

    return panel {
      group(message("settings.general.confirmation.group.title")) {
        row {
          val addConfirmation = vcsManager.getConfirmation(VcsConfiguration.StandardConfirmation.ADD)

          label(message("settings.border.when.files.are.created"))
            .withApplicableVcsesTooltip(addConfirmation, vcsListeners)
            .gap(RightGap.SMALL)
          val addComboBox = comboBox(
            EnumComboBoxModel(VcsShowConfirmationOption.Value::class.java),
            renderer = textListCellRenderer {
              when (it) {
                VcsShowConfirmationOption.Value.SHOW_CONFIRMATION -> message("radio.after.creation.show.options")
                VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY -> message("radio.after.creation.add.silently")
                VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY -> message("radio.after.creation.do.not.add")
                null -> ""
              }
            })
            .bindItem(addConfirmation::getValue, addConfirmation::setValue)
            .withApplicableVcsesTooltip(addConfirmation, vcsListeners)

          checkBox(message("checkbox.including.files.created.outside.ide", ApplicationNamesInfo.getInstance().fullProductName))
            .bindSelected(vcsConfiguration::ADD_EXTERNAL_FILES_SILENTLY)
            .enabledIf(OptionEnabledPredicate(addComboBox.component))
        }.layout(RowLayout.PARENT_GRID)

        row {
          val removeConfirmation = vcsManager.getConfirmation(VcsConfiguration.StandardConfirmation.REMOVE)

          label(message("settings.when.files.are.deleted"))
            .withApplicableVcsesTooltip(removeConfirmation, vcsListeners)
            .gap(RightGap.SMALL)
          comboBox(EnumComboBoxModel(VcsShowConfirmationOption.Value::class.java),
                   renderer = textListCellRenderer {
                     when (it) {
                       VcsShowConfirmationOption.Value.SHOW_CONFIRMATION -> message("radio.after.deletion.show.options")
                       VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY -> message("radio.after.deletion.remove.silently")
                       VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY -> message("radio.after.deletion.do.not.remove")
                       null -> ""
                     }
                   })
            .bindItem(removeConfirmation::getValue, removeConfirmation::setValue)
            .withApplicableVcsesTooltip(removeConfirmation, vcsListeners)
        }.layout(RowLayout.PARENT_GRID)

        row(message("settings.general.show.options.before.command.label")) {
          for (setting in vcsManager.allOptions) {
            checkBox(setting.displayName)
              .bindSelected(setting::getValue, setting::setValue)
              .withApplicableVcsesTooltip(setting, vcsListeners)
              .visibleIf(OptionVisibleForVcsesPredicate(project, setting, vcsListeners))
          }
        }

        if (project.isDefault || ProjectLevelVcsManager.getInstance(project).allSupportedVcss.any { it.editFileProvider != null }) {
          row {
            checkBox(cdShowReadOnlyStatusDialog(project))
          }
        }

        extensions.value.get(Location.Confirmations).forEach { configurable ->
          appendDslConfigurable(configurable)
        }
      }

      group(message("settings.general.changes.group.title")) {
        row {
          val checkBox = checkBox(message("vcs.config.track.changed.on.server"))
            .bindSelected(vcsConfiguration::CHECK_LOCALLY_CHANGED_CONFLICTS_IN_BACKGROUND)
            .gap(RightGap.SMALL)
            .onApply {
              if (!project.isDefault) {
                RemoteRevisionsCache.getInstance(project).updateAutomaticRefreshAlarmState(true)
              }
            }
          spinner(5..48 * 10 * 60, 5)
            .bindIntValue(vcsConfiguration::CHANGED_ON_SERVER_INTERVAL)
            .enabledIf(checkBox.selected)
            .gap(RightGap.SMALL)
          @Suppress("DialogTitleCapitalization")
          label(message("settings.check.every.minutes"))
        }
        row {
          val checkBox = checkBox(message("settings.checkbox.show.changed.in.last"))
            .bindSelected(contentAnnotationSettings::isShow, contentAnnotationSettings::setShow)
            .comment(message("settings.checkbox.show.changed.in.last.comment"))
            .gap(RightGap.SMALL)
          spinner(1..VcsContentAnnotationSettings.ourMaxDays, 1)
            .bindIntValue(contentAnnotationSettings::getLimitDays, contentAnnotationSettings::setLimitDays)
            .enabledIf(checkBox.selected)
            .gap(RightGap.SMALL)
          @Suppress("DialogTitleCapitalization")
          label(message("settings.checkbox.measure.days"))
        }
        row {
          checkBox(cdShowDirtyRecursively(project))
        }
      }

      row(message("show.patch.in.explorer.after.creation.label")) {
        comboBox(EnumComboBoxModel(ShowPatchAfterCreationEnum::class.java))
          .bindItem({ ShowPatchAfterCreationEnum.getByState(vcsConfiguration.SHOW_PATCH_IN_EXPLORER) },
                    { vcsConfiguration.SHOW_PATCH_IN_EXPLORER = it?.state })
      }
      row {
        checkBox(message("radio.restore.workspace.on.branch.switching"))
          .bindSelected(vcsConfiguration::RELOAD_CONTEXT)
          .comment(message("radio.restore.workspace.on.branch.switching.comment"))
      }
      row {
        val checkBox = checkBox(message("settings.checkbox.limit.history.to"))
          .bindSelected(vcsConfiguration::LIMIT_HISTORY)
          .gap(RightGap.SMALL)
        spinner(10..1000000, 10)
          .bindIntValue(vcsConfiguration::MAXIMUM_HISTORY_ROWS)
          .enabledIf(checkBox.selected)
          .gap(RightGap.SMALL)
        @Suppress("DialogTitleCapitalization")
        label(message("settings.checkbox.rows"))
      }

      extensions.value.get(Location.Other).forEach { configurable ->
        appendDslConfigurable(configurable)
      }
    }
  }

  override fun disposeUIResources() {
    super.disposeUIResources()
    extensions.drop()
  }

  private fun <T : JComponent> Cell<T>.withApplicableVcsesTooltip(setting: PersistentVcsSetting,
                                                                  vcsListeners: MutableList<Runnable>): Cell<T> {
    vcsListeners.add(Runnable { updateApplicableVcsesTooltip(project, component, setting) })
    updateApplicableVcsesTooltip(project, component, setting)
    return this
  }
}

private fun updateApplicableVcsesTooltip(project: Project, component: JComponent, setting: PersistentVcsSetting) {
  val vcses = setting.getApplicableVcses(project)
    .map { it.displayName }
  component.toolTipText = when {
    vcses.isNotEmpty() -> message("description.text.option.applicable.to.vcses", VcsUtil.joinWithAnd(vcses.toList(), 0))
    else -> null
  }
}

private enum class ShowPatchAfterCreationEnum(private val text: () -> @Nls String,
                                              val state: Boolean?) {
  ASK({ message("show.patch.in.explorer.after.creation.combobox.text.ask") }, null),
  NO({ message("show.patch.in.explorer.after.creation.combobox.text.no") }, false),
  YES({ message("show.patch.in.explorer.after.creation.combobox.text.show.in.file.manager", RevealFileAction.getFileManagerName()) }, true);

  override fun toString(): String = text()

  companion object {
    fun getByState(state: Boolean?): ShowPatchAfterCreationEnum = when (state) {
      null -> ASK
      true -> YES
      false -> NO
    }
  }
}

@ApiStatus.Internal
class OptionEnabledPredicate(private val comboBox: ComboBox<VcsShowConfirmationOption.Value>) : ComponentPredicate() {
  override fun addListener(listener: (Boolean) -> Unit) {
    comboBox.addItemListener { listener(invoke()) }
  }

  override fun invoke(): Boolean = comboBox.item == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY
}

@ApiStatus.Internal
class OptionVisibleForVcsesPredicate(private val project: Project,
                                     private val setting: PersistentVcsSetting,
                                     private val vcsListeners: MutableList<Runnable>) : ComponentPredicate() {
  override fun addListener(listener: (Boolean) -> Unit) {
    vcsListeners.add(Runnable { listener(invoke()) })
  }

  override fun invoke(): Boolean {
    return project.isDefault ||
           setting.getApplicableVcses(project).isNotEmpty()
  }
}
