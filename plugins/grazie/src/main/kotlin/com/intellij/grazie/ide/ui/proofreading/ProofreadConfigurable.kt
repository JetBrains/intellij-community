@file:Suppress("DialogTitleCapitalization")

package com.intellij.grazie.ide.ui.proofreading

import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieConfig.State.Processing
import com.intellij.grazie.GrazieScope
import com.intellij.grazie.cloud.GrazieCloudConnector
import com.intellij.grazie.cloud.license.GrazieLoginManager
import com.intellij.grazie.icons.GrazieIcons
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.ide.ui.proofreading.component.GrazieLanguagesComponent
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.remote.GrazieRemote
import com.intellij.grazie.remote.GrazieRemote.getLanguagesBasedOnUserAgreement
import com.intellij.grazie.remote.LanguageDownloader
import com.intellij.grazie.utils.isPromotionAllowed
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.OptionsBundle
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.guessCurrentProject
import com.intellij.openapi.ui.DialogPanel
import com.intellij.profile.codeInspection.ui.ErrorsConfigurable
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.VerticalComponentGap
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBDimension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JLabel

private val logger = logger<ProofreadConfigurable>()

class ProofreadConfigurable : BoundSearchableConfigurable(
  OptionsBundle.message("configurable.group.proofread.settings.display.name"),
  "reference.settings.ide.settings.proofreading",
  ID
) {
  companion object {
    const val ID: String = "proofread"
  }

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

  private suspend fun download(langs: Collection<Lang>): Boolean {
    val downloaded = withProcessIcon(langs) {
      LanguageDownloader.startDownloading(it)
    }
    languages.updateLinkToDownloadMissingLanguages()
    return downloaded
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
      cloudSettings()
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
    }
  }

  private fun Panel.cloudSettings() {
    if (!isPromotionAllowed) return
    row {
      label(GrazieBundle.message("grazie.status.bar.widget.language.processing.label.text"))

      icon(GrazieIcons.Stroke.GrazieCloudProcessing)
        .visibleIf(GrazieListeningComponentPredicate(disposable!!) { isLoggedIn })
      label(GrazieBundle.message("grazie.status.bar.widget.cloud.processing.label.text"))
        .visibleIf(GrazieListeningComponentPredicate(disposable!!) { isLoggedIn })
      link(GrazieBundle.message("grazie.status.bar.widget.disable.cloud.link.text")) {
        GrazieConfig.update { state -> state.copy(explicitlyChosenProcessing = Processing.Local) }
      }.visibleIf(GrazieListeningComponentPredicate(disposable!!) { isLoggedIn })
      link(GrazieBundle.message("grazie.settings.logout.action.text")) {
        GrazieConfig.update { state -> state.copy(explicitlyChosenProcessing = Processing.Local) }
        GrazieScope.coroutineScope().launch { GrazieLoginManager.getInstance().logOutFromCloud() }
      }.visibleIf(GrazieListeningComponentPredicate(disposable!!) {
        isLoggedIn && !GrazieCloudConnector.hasAdditionalConnectors()
      })

      icon(GrazieIcons.Stroke.Grazie)
        .visibleIf(GrazieListeningComponentPredicate(disposable!!) { !isLoggedIn })
      label(GrazieBundle.message("grazie.status.bar.widget.local.processing.label.text"))
        .visibleIf(GrazieListeningComponentPredicate(disposable!!) { !isLoggedIn })
      link(GrazieBundle.message("grazie.status.bar.widget.enable.cloud.link.text")) {
        if (!GrazieCloudConnector.askUserConsentForCloud()) return@link
        logger.debug { "Connect to Grazie Cloud button started from settings" }
        if (!GrazieCloudConnector.isAuthorized() && !GrazieCloudConnector.connect(project)) return@link
        GrazieConfig.update { state -> state.copy(explicitlyChosenProcessing = Processing.Cloud) }
      }.visibleIf(GrazieListeningComponentPredicate(disposable!!) { !isLoggedIn })
    }
    row {
      val commentText = GrazieBundle.message("grazie.status.bar.widget.cloud.comment.text")
      // Reserve the space for comment to prevent "jumping" UI
      comment("")
        .applyToComponent {
          GrazieConfig.subscribe(disposable!!) {
            text = if (!isLoggedIn) commentText else ""
          }
          GrazieCloudConnector.subscribeToAuthorizationStateEvents(disposable!!) {
            text = if (!isLoggedIn) commentText else ""
          }
        }
    }
  }

  private suspend fun withProcessIcon(langs: Collection<Lang>, download: suspend (Collection<Lang>) -> Unit): Boolean {
    var failed = false
    try {
      if (GrazieRemote.allAvailableLocally(langs)) return true
      val filteredLanguages = withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        getLanguagesBasedOnUserAgreement(langs, project)
      }
      if (filteredLanguages.isEmpty()) return false
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
      return true
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

  private class GrazieListeningComponentPredicate(private val disposable: Disposable, private val invoker: () -> Boolean) : ComponentPredicate() {
    override fun addListener(listener: (Boolean) -> Unit) {
      GrazieConfig.subscribe(disposable) { listener(invoke()) }
      GrazieCloudConnector.subscribeToAuthorizationStateEvents(disposable) { listener(invoke()) }
    }

    override fun invoke(): Boolean = invoker()
  }

  private val isLoggedIn: Boolean
    get() = GrazieConfig.get().processing == Processing.Cloud && GrazieCloudConnector.isAuthorized()
}
