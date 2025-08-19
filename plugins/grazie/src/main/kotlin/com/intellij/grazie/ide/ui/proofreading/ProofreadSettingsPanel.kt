package com.intellij.grazie.ide.ui.proofreading

import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.ide.ui.components.dsl.border
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.ide.ui.components.dsl.panel
import com.intellij.grazie.ide.ui.proofreading.component.GrazieLanguagesComponent
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.remote.GrazieRemote
import com.intellij.grazie.remote.GrazieRemote.getLanguagesBasedOnUserAgreement
import com.intellij.grazie.remote.LanguageDownloader
import com.intellij.ide.DataManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessCurrentProject
import com.intellij.openapi.ui.DialogPanel
import com.intellij.profile.codeInspection.ui.ErrorsConfigurable
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.layout.migLayout.createLayoutConstraints
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.miginfocom.layout.CC
import net.miginfocom.swing.MigLayout
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.HyperlinkEvent

class ProofreadSettingsPanel : BoundConfigurable(
  GrazieBundle.message("grazie.settings.configurable.name"),
  null
), ConfigurableUi<GrazieConfig>, SearchableConfigurable {
  private val languages = GrazieLanguagesComponent(::download)
  private val project: Project = guessCurrentProject(languages.component)

  private val downloadingLanguages: MutableSet<Lang> = ConcurrentHashMap.newKeySet()
  private val downloadLabel: JLabel by lazy {
    JLabel(msg("grazie.settings.proofreading.languages.download")).apply { isVisible = false }
  }
  private val asyncDownloadingIcon: AsyncProcessIcon by lazy {
    AsyncProcessIcon("Downloading language models").apply { isVisible = false }
  }

  private val config get() = GrazieConfig.get()

  var autoFix: Boolean
    get() = config.autoFix
    set(value) = GrazieConfig.update { it.copy(autoFix = value) }

  private suspend fun download(langs: Collection<Lang>) {
    withProcessIcon(langs) {
      LanguageDownloader.startDownloading(it)
    }
    languages.updateLinkToDownloadMissingLanguages()
  }

  override fun reset(settings: GrazieConfig) {
    super<SearchableConfigurable>.reset()
    super<BoundConfigurable>.reset()
    languages.reset(settings.state)
  }

  override fun isModified(settings: GrazieConfig): Boolean = languages.isModified(settings.state)
                                                             || super<BoundConfigurable>.isModified

  override fun apply(settings: GrazieConfig) {
    super.apply()
    GrazieConfig.update { state ->
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
    add(super.createComponent(), CC().wrap())
  }

  private val component: DialogPanel by lazy {
    com.intellij.ui.dsl.builder.panel {
      generalSettings()
    }
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
            getter = { config.useOxfordSpelling },
            setter = { GrazieConfig.update { state -> state.withOxfordSpelling(it) } }
          )

        fun updateAvailability() {
          oxfordCb.enabled(Lang.BRITISH_ENGLISH in GrazieConfig.get().availableLanguages)
        }
        updateAvailability()
        GrazieConfig.subscribe(disposable!!) { updateAvailability() }
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

  override fun createPanel(): DialogPanel = component

  override fun getPreferredFocusedComponent(): JComponent? {
    return super<ConfigurableUi>.preferredFocusedComponent
  }

  override fun enableSearch(option: String?): Runnable? {
    return super<ConfigurableUi>.enableSearch(option)
  }

  override fun getId(): String = ID

  companion object {
    internal const val ID = "reference.settings.grazie"
  }
}
