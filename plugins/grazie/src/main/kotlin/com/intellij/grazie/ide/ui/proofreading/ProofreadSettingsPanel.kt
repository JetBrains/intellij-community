package com.intellij.grazie.ide.ui.proofreading

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.ide.ui.components.dsl.border
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.ide.ui.components.dsl.panel
import com.intellij.grazie.ide.ui.proofreading.component.GrazieLanguagesComponent
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.remote.GrazieRemote
import com.intellij.grazie.remote.LanguageDownloader
import com.intellij.ide.DataManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.options.ex.Settings
import com.intellij.profile.codeInspection.ui.ErrorsConfigurable
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.layout.migLayout.createLayoutConstraints
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.miginfocom.layout.CC
import net.miginfocom.swing.MigLayout
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.HyperlinkEvent

private val logger = logger<ProofreadSettingsPanel>()

class ProofreadSettingsPanel : ConfigurableUi<GrazieConfig> {
  private val EP: ExtensionPointName<Configurable> = ExtensionPointName("com.intellij.grazie.proofreadSettingsExtension")
  private val languages = GrazieLanguagesComponent(::download)

  private val downloadingLanguages: MutableSet<Lang> = ConcurrentHashMap.newKeySet()
  private val downloadLabel: JLabel by lazy {
    JLabel(msg("grazie.settings.proofreading.languages.download")).apply { isVisible = false }
  }
  private val asyncDownloadingIcon: AsyncProcessIcon by lazy {
    AsyncProcessIcon("Downloading language models").apply { isVisible = false }
  }

  private suspend fun download(lang: Lang) {
    withProcessIcon(lang) {
      LanguageDownloader.startDownloading(listOf(it))
    }
    languages.updateLinkToDownloadMissingLanguages()
  }

  override fun reset(settings: GrazieConfig) {
    languages.reset(settings.state)
    EP.extensionList.forEach { it.reset() }
  }

  override fun isModified(settings: GrazieConfig): Boolean = languages.isModified(settings.state)
                                                             || EP.extensionList.any { it.isModified }

  override fun apply(settings: GrazieConfig) {
    GrazieConfig.update { state ->
      EP.extensionList.forEach { it.apply() }
      languages.apply(state)
    }
  }

  override fun getComponent(): JPanel = panel(MigLayout(createLayoutConstraints().hideMode(3))) {
    val downloadPanel = JPanel(MigLayout("insets 0, gap 5, hidemode 3"))
    downloadPanel.add(JLabel(msg("grazie.settings.proofreading.languages.text")))
    downloadPanel.add(asyncDownloadingIcon)
    downloadPanel.add(downloadLabel)

    panel(MigLayout(createLayoutConstraints().hideMode(3)), constraint = CC().growX().wrap()) {
      border = border("", false, JBUI.insetsBottom(10), false)
      add(downloadPanel, CC().growX().wrap())
      add(languages.component, CC().width("350px").height("150px"))
    }

    languages.reset(GrazieConfig.get())

    val link = HyperlinkLabel(msg("grazie.settings.proofreading.link-to-inspection"))
    link.addHyperlinkListener { e: HyperlinkEvent ->
      if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
        Settings.KEY.getData(DataManager.getInstance().getDataContext(this))?.let { settings ->
          settings.find(ErrorsConfigurable::class.java)?.let {
            settings.select(it).doWhenDone {
              it.selectInspectionGroup(arrayOf(msg("grazie.group.name")))
            }
          }
        }
      }
    }

    add(link, CC().wrap())

    EP.extensionList.forEach { add(it.createComponent(), CC().wrap()) }
  }

  private suspend fun withProcessIcon(lang: Lang, download: suspend (Lang) -> Unit) {
    var failed = false
    try {
      if (GrazieRemote.isAvailableLocally(lang)) return
      if (downloadingLanguages.isEmpty()) {
        downloadingLanguages.add(lang)
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          downloadLabel.text = msg("grazie.settings.proofreading.languages.download")
          asyncDownloadingIcon.resume()
          asyncDownloadingIcon.isVisible = true
          downloadLabel.isVisible = true
        }
      }
      download(lang)
    }
    catch (e: Exception) {
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        downloadLabel.text = msg("grazie.settings.proofreading.languages.download.failed", lang.displayName)
        asyncDownloadingIcon.suspend()
      }
      failed = true
      downloadingLanguages.clear()
      logger.warn("Failed to download language '${lang.displayName}'", e)
      throw e
    }
    finally {
      downloadingLanguages.remove(lang)
      if (downloadingLanguages.isEmpty() && !failed) {
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          asyncDownloadingIcon.isVisible = false
          downloadLabel.isVisible = false
        }
      }
    }
  }
}
