package com.intellij.grazie.ide.ui.proofreading

import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieConfig.State.Processing
import com.intellij.grazie.cloud.GrazieCloudConnector
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.ide.ui.proofreading.component.GrazieLanguagesComponent
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.remote.GrazieRemote
import com.intellij.grazie.remote.GrazieRemote.getLanguagesBasedOnUserAgreement
import com.intellij.grazie.remote.LanguageDownloader
import com.intellij.ide.DataManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.OptionsBundle
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.guessCurrentProject
import com.intellij.openapi.ui.DialogPanel
import com.intellij.profile.codeInspection.ui.ErrorsConfigurable
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBDimension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JLabel

class ProofreadConfigurable : BoundSearchableConfigurable(
  OptionsBundle.message("configurable.group.proofread.settings.display.name"),
  "reference.settings.ide.settings.proofreading",
  "proofread"
) {
  private val languages by lazy { GrazieLanguagesComponent(::download) }
  private val project by lazy { guessCurrentProject(languages.component) }

  private val downloadingLanguages: MutableSet<Lang> = ConcurrentHashMap.newKeySet()
  private val downloadLabel: JLabel by lazy {
    JLabel(msg("grazie.settings.proofreading.languages.download")).apply { isVisible = false }
  }
  private val asyncDownloadingIcon: AsyncProcessIcon by lazy {
    AsyncProcessIcon("Downloading language models").apply { isVisible = false }
  }

  var autoFix: Boolean
    get() = GrazieConfig.get().autoFix
    set(value) = GrazieConfig.update { it.copy(autoFix = value) }

  private suspend fun download(langs: Collection<Lang>) {
    withProcessIcon(langs) {
      LanguageDownloader.startDownloading(it)
    }
    languages.updateLinkToDownloadMissingLanguages()
  }

  override fun createPanel(): DialogPanel {
    lateinit var result: DialogPanel
    result = panel {
      lateinit var lbLanguage: JLabel
      row {
        lbLanguage = label(msg("grazie.settings.proofreading.languages.text"))
          .gap(RightGap.SMALL)
          .component
        cell(asyncDownloadingIcon).gap(RightGap.SMALL)
        cell(downloadLabel)
      }
      row {
        cell(languages.component)
          .onReset { languages.reset(GrazieConfig.get()) }
          .onApply { GrazieConfig.update { state -> languages.apply(state) } }
          .onIsModified { languages.isModified(GrazieConfig.get()) }
          .applyToComponent {
            preferredSize = JBDimension(350, 150)
            putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, VerticalComponentGap(top = false, bottom = true))
            lbLanguage.labelFor = this
          }
      }
      row {
        link(msg("grazie.settings.proofreading.link-to-inspection")) {
          Settings.KEY.getData(DataManager.getInstance().getDataContext(result))?.let { settings ->
            settings.find(ErrorsConfigurable::class.java)?.let {
              settings.select(it).doWhenDone {
                it.selectInspectionGroup(arrayOf(msg("grazie.group.name")))
              }
            }
          }
        }
      }
      generalSettings()
    }

    languages.reset(GrazieConfig.get())
    return result
  }

  private fun Panel.generalSettings(): Row {
    return group {
      row {
        checkBox(GrazieBundle.message("grazie.settings.auto.apply.fixes.label")).bindSelected(::autoFix)
      }

      row {
        @Suppress("DialogTitleCapitalization")
        val oxfordCb = checkBox(GrazieBundle.message("grazie.settings.use.oxford.spelling.checkbox"))
          .bindSelected(
            getter = { GrazieConfig.get().useOxfordSpelling },
            setter = { GrazieConfig.update { state -> state.withOxfordSpelling(it) } }
          )

        fun updateAvailability() {
          oxfordCb.enabled(Lang.BRITISH_ENGLISH in GrazieConfig.get().availableLanguages)
        }
        updateAvailability()
        GrazieConfig.subscribe(disposable!!) { updateAvailability() }
      }

      if (GrazieCloudConnector.hasCloudConnector()) {
        row {
          checkBox(GrazieBundle.message("grazie.settings.use.advanced.spelling.checkbox"))
            .bindSelected(
              getter = { GrazieConfig.get().processing == Processing.Cloud },
              setter = { isSelected ->
                val selectedProcessing = if (isSelected) Processing.Cloud else Processing.Local
                if (GrazieConfig.get().processing != selectedProcessing || GrazieConfig.get().explicitlyChosenProcessing != null) {
                  GrazieConfig.update { state -> state.copy(explicitlyChosenProcessing = selectedProcessing) }
                }
              }
            )
            .onChanged { checkBox ->
              if (checkBox.isSelected && GrazieConfig.get().explicitlyChosenProcessing == null) {
                val agreement = GrazieCloudConnector.EP_NAME.extensionList.first().askUserConsentForCloud()
                if (!agreement) checkBox.isSelected = false
              }
            }
        }
      }
    }
  }

  private suspend fun withProcessIcon(langs: Collection<Lang>, download: suspend (Collection<Lang>) -> Unit) {
    var failed = false
    try {
      if (GrazieRemote.allAvailableLocally(langs)) return
      val filteredLanguages = withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        getLanguagesBasedOnUserAgreement(langs, project)
      }
      if (filteredLanguages.isEmpty()) return
      if (downloadingLanguages.isEmpty()) {
        downloadingLanguages.addAll(langs)
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          downloadLabel.text = msg("grazie.settings.proofreading.languages.download")
          asyncDownloadingIcon.resume()
          asyncDownloadingIcon.isVisible = true
          downloadLabel.isVisible = true
        }
      }
      download(filteredLanguages)
    }
    catch (e: Exception) {
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        downloadLabel.text = msg("grazie.settings.proofreading.languages.download.failed", langs.joinToString { it.displayName })
        asyncDownloadingIcon.suspend()
      }
      failed = true
      downloadingLanguages.clear()
      throw e
    }
    finally {
      downloadingLanguages.removeAll(langs)
      if (downloadingLanguages.isEmpty() && !failed) {
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          asyncDownloadingIcon.isVisible = false
          downloadLabel.isVisible = false
        }
      }
    }
  }
}
