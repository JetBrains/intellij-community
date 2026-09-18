package com.intellij.grazie.ide.ui.configurable

import ai.grazie.nlp.langs.Language
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.detection.toLanguage
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.ide.ui.grammar.tabs.rules.component.GrazieDescriptionComponent
import com.intellij.grazie.ide.ui.grammar.tabs.rules.component.GrazieTreeComponent
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.utils.TextStyleDomain
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.getParentOfType
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.whenItemSelectedFromUi
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Borders
import com.intellij.util.ui.update.UiNotifyConnector
import java.awt.Dimension
import java.awt.Rectangle
import java.util.EnumMap
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent

class StyleConfigurable : BoundConfigurable(GrazieBundle.message("grazie.settings.grammar.tabs.rules"), null), Disposable, Configurable.NoScroll {
  private val settings: Settings = Settings()
  private val langComboModel = CollectionComboBoxModel(ArrayList<Lang>())
  private lateinit var langCombo: ComboBox<Lang>

  private val filterComponent: SearchTextField = SearchTextField(false).also {
    it.textEditor.emptyText.text = GrazieBundle.message("grazie.settings.style.search.placeholder")
    it.textEditor.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        settings.updateFilter(domainComboBox.selected!!, langComboModel.selected!!.toLanguage(), filterComponent.text)
      }
    })
    it.border = JBEmptyBorder(5)
  }

  private val treeWrapper by lazy {
    JBSplitter(false, 0.45f).apply {
      val domain = domainComboBox.selected!!
      val treeSettings = settings.getTreeSettings(domain, Language.ENGLISH)
      treeSettings.description.listener(Language.ENGLISH)
      firstComponent = createScrollTreeComponent(domain, Language.ENGLISH)
      secondComponent = treeSettings.description.component
    }
  }

  private lateinit var domainComboBox: ComboBox<TextStyleDomain>

  val component: DialogPanel by lazy {
    loadLanguages()
    panel {
      row {
        comment(GrazieBundle.message("grazie.settings.writing.style.hint"))
      }
      row {
        label(GrazieBundle.message("grazie.settings.writing.style.domain"))
        domainComboBox = domainComboBox()
          .whenItemSelectedFromUi { domain -> selectDomain(domain, langComboModel.selected!!) }
          .component
        domainComboBox.selected = TextStyleDomain.Other
        settings.addDomain(TextStyleDomain.Other, Language.ENGLISH, filterComponent)

        label(GrazieBundle.message("grazie.settings.language.chooser.label"))
        langCombo = comboBox(langComboModel, textListCellRenderer("") { it.nativeName })
          .widthGroup("TopCombo")
          .whenItemSelectedFromUi { lang ->
            val language = lang.toLanguage()
            selectDomain(TextStyleDomain.Other, lang)
            settings.updateFilter(TextStyleDomain.Other, language, filterComponent.text)
          }
          .component
        langCombo.selected = GrazieConfig.get().availableLanguages.find { it.isEnglish() }
        trackNewLanguageAddition()
      }

      row {
        cell(filterComponent).resizableColumn().align(Align.FILL)
      }

      row {
        val content = JPanel().apply {
          layout = BoxLayout(this, BoxLayout.Y_AXIS)
          add(treeWrapper)
          add(Box.createVerticalGlue())
        }
        val scroll = ScrollPaneFactory.createScrollPane(content, VERTICAL_SCROLLBAR_ALWAYS, HORIZONTAL_SCROLLBAR_NEVER)
        cell(scroll).resizableColumn().align(Align.FILL)
        resizableRow()
      }

      treeWrapper.minimumSize = JBUI.size(150, 200)
      treeWrapper.maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
      treeWrapper.preferredSize = JBUI.size(-1, 300)
      treeWrapper.setHonorComponentsMinimumSize(true)
    }.also { it.border = Borders.empty() }
  }

  override fun createPanel(): DialogPanel = component

  override fun isModified(): Boolean = super<BoundConfigurable>.isModified || settings.isModified(GrazieConfig.get())

  override fun apply() {
    super.apply()
    settings.apply(GrazieConfig.get())
  }

  override fun reset() {
    super.reset()
    settings.reset(GrazieConfig.get())
  }

  override fun dispose() {
    disposeUIResources()
  }

  private fun selectDomain(domain: TextStyleDomain, lang: Lang) {
    val language = lang.toLanguage()
    domainComboBox.selected = domain

    settings.addDomain(domain, language, filterComponent)
    langCombo.selected = lang
    repaintSettings(domain, language)
    settings.updateFilter(domain, language, filterComponent.text)
  }

  private fun repaintSettings(domain: TextStyleDomain, language: Language) {
    val treeSettings = settings.getTreeSettings(domain, language)
    treeSettings.description.listener(language)
    treeWrapper.firstComponent = createScrollTreeComponent(domain, language)
    treeWrapper.secondComponent = treeSettings.description.component
    treeWrapper.revalidate()
    treeWrapper.repaint()
  }

  private fun trackNewLanguageAddition() {
    GrazieConfig.subscribe(this) {
      SwingUtilities.invokeLater {
        val newLanguages = loadLanguages() ?: return@invokeLater

        val lang = if (langComboModel.selected != null && langComboModel.selected in newLanguages) {
          langComboModel.selected!!
        } else {
          GrazieConfig.get().availableLanguages.first { it.isEnglish() }
        }
        val language = lang.toLanguage()
        val domain = domainComboBox.selected!!
        settings.clear()
        settings.addDomain(domain, language, filterComponent)
        repaintSettings(domain, language)
        langCombo.selected = lang
      }
    }
  }

  private fun loadLanguages(): Set<Lang>? {
    val langs = runWithModalProgressBlocking(
      ModalTaskOwner.guess(),
      GrazieBundle.message("grazie.settings.grammar.tabs.rules.loading.message"),
    ) {
      GrazieConfig.get().availableLanguages
    }
    if (langComboModel.items == langs) return null
    langComboModel.removeAll()
    langComboModel.add(langs.toList())
    return langs.toSet()
  }

  override fun getDisplayName(): @NlsContexts.ConfigurableName String = msg("grazie.settings.page.name")

  private fun createScrollTreeComponent(domain: TextStyleDomain, language: Language): JScrollPane {
    return ScrollPaneFactory.createScrollPane(
      settings.getTreeSettings(domain, language).tree,
      VERTICAL_SCROLLBAR_AS_NEEDED,
      HORIZONTAL_SCROLLBAR_AS_NEEDED
    )
  }

  companion object {

    @JvmStatic
    val ruleEngineLanguages: List<Language> = listOf(Language.ENGLISH, Language.GERMAN, Language.RUSSIAN, Language.UKRAINIAN)

    @JvmStatic
    fun focusSetting(rule: com.intellij.grazie.text.Rule, domain: TextStyleDomain, lang: Lang, project: Project): Boolean {
      val language = lang.toLanguage()
      val configurable = StyleConfigurable().apply {
        createComponent()
        selectDomain(domain, lang)
        focusTreeSetting(this, rule, domain, language, project)
      }
      return ShowSettingsUtil.getInstance().editConfigurable(project, configurable)
    }

    @JvmStatic
    fun open(project: Project?): Boolean = ShowSettingsUtil.getInstance().editConfigurable(project, StyleConfigurable())
  }

  private fun focusTreeSetting(
    styleConfigurable: StyleConfigurable,
    rule: com.intellij.grazie.text.Rule,
    domain: TextStyleDomain,
    language: Language,
    project: Project) {
    val data = settings.getTreeSettings(domain, language)
    styleConfigurable.apply {
      data.tree.focusRule(rule)
      UiNotifyConnector.doWhenFirstShown(data.tree) {
        SwingUtilities.invokeLater {
          val scroll = data.tree.getParentOfType<JBScrollPane>()
          val dataTreeScroll = scroll?.getParentOfType<JBScrollPane>()
          val componentScroll = dataTreeScroll?.getParentOfType<JBScrollPane>()
          if (dataTreeScroll == null || componentScroll == null) return@invokeLater
          val destination = SwingUtilities.convertRectangle(
            scroll,
            Rectangle(0, 0, scroll.width, scroll.height),
            dataTreeScroll.viewport.view
          )
          componentScroll.scrollRectToVisible(destination)
          IdeFocusManager.getInstance(project).requestFocus(data.tree, true)
        }
      }
    }
  }
}

data class Settings(
  val combinedSettings: MutableMap<TextStyleDomain, MutableMap<Language, CombinedSettings>> = EnumMap(TextStyleDomain::class.java),
) {
  fun getTreeSettings(domain: TextStyleDomain, language: Language): TreeSettings = combinedSettings[domain]!![language]!!.treeSettings

  fun isModified(state: GrazieConfig.State): Boolean =
    combinedSettings.values.any { settings -> settings.values.any { it.isModified(state) } }

  fun reset(state: GrazieConfig.State) {
    combinedSettings.forEach { (_, settingsMap) ->
      settingsMap.forEach { (_, settings) ->
        settings.reset(state)
      }
    }
  }

  fun apply(originalState: GrazieConfig.State) {
    combinedSettings
      .filter { it.value.any { settings -> settings.value.isModified(originalState) } }
      .forEach { (domain, settingsMap) ->
        val userEnabledRules = HashSet<String>()
        val userDisabledRules = HashSet<String>()

        val changedSettings = settingsMap.filter { settings -> settings.value.isModified(originalState) }
        changedSettings.forEach { (_, combinedSettings) ->
          val userEnabledRulesPerLanguage = HashSet<String>()
          val userDisabledRulesPerLanguage = HashSet<String>()

          val updatedState = combinedSettings.treeSettings.tree.apply(originalState)
          updatedState.getUserChangedRules(domain).let { (enabledRules, disabledRules) ->
            userEnabledRulesPerLanguage.addAll(enabledRules)
            userDisabledRulesPerLanguage.addAll(disabledRules)
          }
          userEnabledRules.addAll(userEnabledRulesPerLanguage)
          userDisabledRules.addAll(userDisabledRulesPerLanguage)
        }

        GrazieConfig.update { it.updateUserRules(domain, userEnabledRules, userDisabledRules) }
        changedSettings.forEach { (_, settings) -> settings.treeSettings.tree.reset(GrazieConfig.get()) }
      }
  }

  fun addDomain(domain: TextStyleDomain, language: Language, filterComponent: SearchTextField) {
    val settings = combinedSettings[domain]
    if (settings != null && language in settings) return
    if (settings == null) combinedSettings[domain] = EnumMap(Language::class.java)
    addLanguage(domain, language, filterComponent)
  }

  fun addLanguage(domain: TextStyleDomain, language: Language, filterComponent: SearchTextField) {
    val settingsPerLanguage = combinedSettings[domain]!!
    if (language in settingsPerLanguage) return

    val description = GrazieDescriptionComponent()
    val tree = GrazieTreeComponent(description.listener, language, domain, filterComponent)
    tree.reset(GrazieConfig.get())
    settingsPerLanguage[language] = CombinedSettings(TreeSettings(description, tree))
  }

  fun updateFilter(domain: TextStyleDomain, language: Language, option: String): Unit =
    combinedSettings[domain]!![language]!!.updateFilter(option)

  fun clear(): Unit = combinedSettings.clear()
}

data class CombinedSettings(val treeSettings: TreeSettings) {
  fun isModified(state: GrazieConfig.State): Boolean = treeSettings.tree.isModified(state)
  fun reset(state: GrazieConfig.State) {
    if (treeSettings.tree.isModified(state)) treeSettings.tree.reset(state)
  }
  fun updateFilter(option: String) {
    treeSettings.tree.filter(option)
  }
}

data class TreeSettings(
  val description: GrazieDescriptionComponent,
  val tree: GrazieTreeComponent,
)

private fun Row.domainComboBox() = comboBox(
  CollectionComboBoxModel(TextStyleDomain.entries),
  textListCellRenderer("") {
    GrazieBundle.messageOrNull("grazie.settings.domain.profile.display.$it")
  }
)

@Suppress("UNCHECKED_CAST")
private var <T> ComboBox<T>.selected: T?
  get() = this.selectedItem as? T
  set(value) { this.selectedItem = value }
