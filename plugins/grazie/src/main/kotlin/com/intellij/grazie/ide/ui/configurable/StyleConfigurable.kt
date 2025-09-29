package com.intellij.grazie.ide.ui.configurable

import ai.grazie.nlp.langs.Language
import ai.grazie.nlp.langs.utils.englishName
import ai.grazie.nlp.langs.utils.nativeName
import ai.grazie.rules.Rule
import ai.grazie.rules.settings.Setting
import ai.grazie.rules.settings.SettingComponent
import ai.grazie.rules.settings.TextStyle
import ai.grazie.rules.toolkit.LanguageToolkit
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.detection.toLanguage
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.ide.ui.configurable.StyleConfigurable.Companion.ruleEngineLanguages
import com.intellij.grazie.ide.ui.grammar.tabs.rules.component.GrazieDescriptionComponent
import com.intellij.grazie.ide.ui.grammar.tabs.rules.component.GrazieTreeComponent
import com.intellij.grazie.rule.RuleIdeClient
import com.intellij.grazie.utils.*
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
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
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.ui.*
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.*
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Borders
import com.intellij.util.ui.UIUtil.FontSize
import com.intellij.util.ui.update.UiNotifyConnector
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Image
import java.awt.Rectangle
import java.net.URL
import javax.swing.*
import javax.swing.ScrollPaneConstants.*
import javax.swing.event.DocumentEvent
import javax.swing.event.HyperlinkEvent

class StyleConfigurable : BoundConfigurable(GrazieBundle.message("grazie.settings.grammar.tabs.rules"), null), Disposable, Configurable.NoScroll {
  private val settings: Settings = Settings()
  private val langComboModel = CollectionComboBoxModel(ArrayList<Language>())
  private lateinit var langCombo: ComboBox<Language>

  private val filterComponent: SearchTextField = SearchTextField(false).also {
    it.textEditor.emptyText.text = GrazieBundle.message("grazie.settings.style.search.placeholder")
    it.textEditor.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        settings.updateFilter(textStyle, langComboModel.selected!!, filterComponent.text)
      }
    })
    it.border = JBEmptyBorder(5)
  }

  private val treeWrapper by lazy {
    JBSplitter(false, 0.45f).apply {
      firstComponent = createScrollTreeComponent(textStyle, Language.ENGLISH)
      secondComponent = settings.getTreeSettings(textStyle, Language.ENGLISH).description.component
    }
  }

  private val settingWrapper by lazy {
    JPanel(BorderLayout()).also {
      it.add(settings.getFeaturedSettings(textStyle, Language.ENGLISH)!!.component)
      it.maximumSize = Dimension(Int.MAX_VALUE, it.preferredSize.height)
    }
  }

  private val separator by lazy {
    createTitledSeparator(GrazieBundle.message("grazie.settings.style.rules.other"), IntelliJSpacingConfiguration())
  }

  private lateinit var domainComboBox: ComboBox<TextStyleDomain>
  private lateinit var styleProfileCombo: ComboBox<TextStyle>
  private lateinit var styleRowVisibleUpdater: ((Boolean) -> Unit)

  private val config get() = GrazieConfig.get()

  private val textStyle
    get(): TextStyle {
      val domain = domainComboBox.selected!!
      if (domain == TextStyleDomain.Other) return styleProfileCombo.selected!!
      return TextStyle.styles(RuleIdeClient.INSTANCE).find { it.id == domain.name }!!
    }

  private var otherDomainStyle: TextStyle
    get() = config.getTextStyle()
    set(value) {
      GrazieConfig.update { it.copy(styleProfile = value.id) }
    }

  val component: DialogPanel by lazy {
    loadLanguages()
    val userTextStyle = GrazieConfig.get().getTextStyle()
    panel {
      row {
        comment(GrazieBundle.message("grazie.settings.writing.style.hint"))
      }
      row {
        label(GrazieBundle.message("grazie.settings.writing.style.domain"))
        domainComboBox = domainComboBox()
          .whenItemSelectedFromUi { domainId ->
            val textStyle = if (domainId == TextStyleDomain.Other) GrazieConfig.get().getTextStyle() else domainId.textStyle
            selectTextStyle(textStyle, langComboModel.selected!!)
          }
          .component
        domainComboBox.selectedItem = TextStyleDomain.Other

        val styleLabelCell = label(GrazieBundle.message("grazie.settings.writing.style.text"))
        val styleComboCell = writingStyleComboBox()
          .applyIfEnabled()
          .bindItem(::otherDomainStyle.toNullableProperty())
          .whenItemSelectedFromUi { textStyle ->
            if (domainComboBox.selected == TextStyleDomain.Other) {
              selectTextStyle(textStyle, langComboModel.selected!!)
              settings.reset(GrazieConfig.get())
              selectLanguage(textStyle, langComboModel.selected!!)
            }
          }
        settings.addTextStyle(userTextStyle, Language.ENGLISH, filterComponent)
        styleProfileCombo = styleComboCell.component
        styleProfileCombo.selectedItem = userTextStyle
        styleRowVisibleUpdater = { visible ->
          styleLabelCell.visible(visible)
          styleComboCell.visible(visible)
          styleComboCell.enabled(visible)
        }
        styleRowVisibleUpdater.invoke(domainComboBox.selectedItem == TextStyleDomain.Other)
      }

      row(GrazieBundle.message("grazie.settings.language.chooser.label")) {
        langCombo = comboBox(langComboModel, SimpleListCellRenderer.create { label, lang, _ -> label.text = lang.nativeName })
          .widthGroup("TopCombo")
          .whenItemSelectedFromUi { language ->
            selectTextStyle(textStyle, language)
            separator.text = GrazieBundle.message(if (language in ruleEngineLanguages) "grazie.settings.style.rules.other" else "grazie.settings.style.rules.all")
            if (filterComponent.text.isNotBlank()) settings.updateFilter(textStyle, language, filterComponent.text)
            settings.getTreeSettings(textStyle, language).description.listener(language)
          }
          .component
        selectLanguage(userTextStyle, Language.ENGLISH)
        trackNewLanguageAddition()
      }

      row {
        cell(filterComponent).resizableColumn().align(Align.FILL)
      }

      row {
        val content = JPanel().apply {
          layout = BoxLayout(this, BoxLayout.Y_AXIS)
          add(settingWrapper)
          add(separator)
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

      settings.getTreeSettings(textStyle, Language.ENGLISH).description.listener(Language.ENGLISH)
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

  private fun selectTextStyle(textStyle: TextStyle, language: Language) {
    val domain = textStyle.getTextDomain()
    if (domain != TextStyleDomain.Other) domainComboBox.selectedItem = domain else styleProfileCombo.selectedItem = textStyle
    styleRowVisibleUpdater.invoke(domain == TextStyleDomain.Other)

    settings.addTextStyle(textStyle, language, filterComponent)
    langCombo.selectedItem = language
    repaintSettings(textStyle, language)
  }

  private fun selectLanguage(textStyle: TextStyle, language: Language) {
    settings.addLanguage(textStyle, language, filterComponent)
    langCombo.selectedItem = language
    repaintSettings(textStyle, language)
  }

  private fun repaintSettings(textStyle: TextStyle, language: Language) {
    val hasFeaturedVisible = language in ruleEngineLanguages
    settingWrapper.isVisible = hasFeaturedVisible
    if (hasFeaturedVisible) {
      settingWrapper.removeAll()
      settingWrapper.add(settings.getFeaturedSettings(textStyle, language)!!.component)
      settingWrapper.maximumSize = Dimension(Int.MAX_VALUE, settingWrapper.preferredSize.height)
      settingWrapper.repaint()
    }
    treeWrapper.removeAll()
    treeWrapper.firstComponent = createScrollTreeComponent(textStyle, language)
    treeWrapper.secondComponent = settings.getTreeSettings(textStyle, language).description.component
    treeWrapper.repaint()
  }

  private fun trackNewLanguageAddition() {
    GrazieConfig.subscribe(this) {
      if (loadLanguages()) {
        settings.clear()
        settings.addTextStyle(textStyle, Language.ENGLISH, filterComponent)
      }
    }
  }

  private fun loadLanguages(): Boolean {
    val langs = GrazieConfig.get().availableLanguages.map { it.toLanguage() }.sortedBy { it.englishName }
    if (langComboModel.items == langs) return false
    langComboModel.removeAll()
    langComboModel.add(langs)
    return true
  }

  override fun getDisplayName(): @NlsContexts.ConfigurableName String = msg("grazie.settings.page.name")

  private fun createScrollTreeComponent(textStyle: TextStyle, language: Language): JScrollPane {
    return ScrollPaneFactory.createScrollPane(
      settings.getTreeSettings(textStyle, language).tree,
      VERTICAL_SCROLLBAR_AS_NEEDED,
      HORIZONTAL_SCROLLBAR_AS_NEEDED
    )
  }

  companion object {

    @JvmStatic
    val ruleEngineLanguages: List<Language> = listOf(Language.ENGLISH, Language.GERMAN, Language.RUSSIAN, Language.UKRAINIAN)

    @JvmStatic
    fun focusSetting(setting: Setting, domain: TextStyleDomain, language: Language, project: Project): Boolean {
      return focusSetting(setting, null, domain, language, project)
    }

    @JvmStatic
    fun focusSetting(setting: Setting?, rule: com.intellij.grazie.text.Rule?, domain: TextStyleDomain, language: Language, project: Project): Boolean {
      require(setting != null || rule != null) { "Setting and Rule can't be null" }
      val configurable = StyleConfigurable().apply {
        createComponent()
        val style = if (domain == TextStyleDomain.Other) GrazieConfig.get().getTextStyle() else domain.textStyle
        selectTextStyle(style, language)

        if (setting != null && featuredSettings(language).contains(setting)) focusFeaturedSetting(this, setting, style, language, project)
        else if (rule != null) focusTreeSetting(this, rule, style, language, project)
      }
      return ShowSettingsUtil.getInstance().editConfigurable(project, configurable)
    }
  }

  private fun focusFeaturedSetting(styleConfigurable: StyleConfigurable, setting: Setting, style: TextStyle, language: Language, project: Project) {
    val data = settings.getFeaturedSettings(style, language)
    if (data == null) return
    styleConfigurable.apply {
      val settingComponent = data.component.findParentSettingComponent(setting)
      val paramComponent = data.component.findOwnSettingComponent(setting)
      if (paramComponent != null && settingComponent != null) {
        UiNotifyConnector.doWhenFirstShown(data.component) {
          SwingUtilities.invokeLater {
            val scrollPane = settingComponent.getParentOfType<JBScrollPane>()!!
            settingComponent.scrollRectToVisible(Rectangle(settingComponent.width, scrollPane.height))
            IdeFocusManager.getInstance(project).requestFocus(paramComponent, true)
          }
        }
      }
    }
  }

  private fun focusTreeSetting(styleConfigurable: StyleConfigurable, rule: com.intellij.grazie.text.Rule, style: TextStyle, language: Language, project: Project) {
    val data = settings.getTreeSettings(style, language)
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
  val featuredSettings: MutableMap<String, MutableMap<Language, FeaturedSettings>> = HashMap(),
  val treeSettings: MutableMap<String, MutableMap<Language, TreeSettings>> = HashMap(),
) {
  fun getFeaturedSettings(textStyle: TextStyle, language: Language): FeaturedSettings? = featuredSettings[textStyle.id]?.get(language)
  fun getTreeSettings(textStyle: TextStyle, language: Language): TreeSettings = treeSettings[textStyle.id]!![language]!!

  fun isModified(state: GrazieConfig.State): Boolean =
    featuredSettings.values.any { areModifiedFeaturedSettings(it) } ||
    treeSettings.values.any { areModifiedTreeSettings(it, state) }

  fun reset(state: GrazieConfig.State) {
    featuredSettings.forEach { (domain, featuredSettings) ->
      if (areModifiedFeaturedSettings(featuredSettings)) {
        featuredSettings.filter { areModifiedFeaturedSettings(it.value) }.forEach { (language, settings) ->
          val textStyle = getTextStyle(domain)
          settings.component.loadState(getSettingsState(language, textStyle), textStyle)
          settings.resetState = settings.component.state
        }
      }
    }
    treeSettings.forEach { (_, treesSettings) ->
      if (areModifiedTreeSettings(treesSettings, state)) {
        treesSettings.filter { areModifiedTreeSettings(it.value, state) }.forEach { (_, settings) -> settings.tree.reset(state) }
      }
    }
  }

  fun apply(originalState: GrazieConfig.State) {
    treeSettings
      .filter { (domainId, treeSettingsMap) -> areModifiedFeaturedSettings(featuredSettings[domainId]!!) || areModifiedTreeSettings(treeSettingsMap, originalState) }
      .forEach { (domainId, treeSettingsMap) ->
        val domain = getTextStyle(domainId).getTextDomain()
        val userEnabledRules = HashSet<String>()
        val userDisabledRules = HashSet<String>()
        val parameters = HashMap<Language, Map<String, String>>()

        treeSettingsMap.filter { areModifiedTreeSettings(it.value, originalState) }.forEach { (language, treeSettings) ->
          val userEnabledRulesPerLanguage = HashSet<String>()
          val userDisabledRulesPerLanguage = HashSet<String>()

          featuredSettings[domainId]?.filter { areModifiedFeaturedSettings(it.value) }?.get(language)?.let { featuredSettings ->
            val prefix = Rule.globalIdPrefix(language)
            val settingsState = featuredSettings.component.state
            featuredSettings.resetState = settingsState

            for (id in settingsState.enabledRules) {
              userEnabledRulesPerLanguage.add(prefix + id)
              userDisabledRulesPerLanguage.remove(prefix + id)
            }
            for (id in settingsState.disabledRules) {
              userEnabledRulesPerLanguage.remove(prefix + id)
              userDisabledRulesPerLanguage.add(prefix + id)
            }
            parameters[language] = settingsState.paramValues
          }

          val updatedState = treeSettings.tree.apply(originalState)
          val affectedGlobalRules = getAffectedGlobalRules(language)
          updatedState.getUserChangedRules(domain).let { (enabledRules, disabledRules) ->
            userEnabledRulesPerLanguage.addAll(enabledRules - affectedGlobalRules)
            userDisabledRulesPerLanguage.addAll(disabledRules - affectedGlobalRules)
          }
          userEnabledRules.addAll(userEnabledRulesPerLanguage)
          userDisabledRules.addAll(userDisabledRulesPerLanguage)
        }

        if (domain == TextStyleDomain.Other) GrazieConfig.update { it.copy(parameters = parameters) }
        else GrazieConfig.update { it.copy(parametersPerDomain = mapOf(domain to parameters)) }
        GrazieConfig.update { it.updateUserRules(domain, userEnabledRules, userDisabledRules) }
        treeSettingsMap.forEach { it.value.tree.reset(GrazieConfig.get()) }
      }
  }

  fun addTextStyle(textStyle: TextStyle, language: Language, filterComponent: SearchTextField) {
    val settings = treeSettings[textStyle.id]
    if (settings != null && language in settings) return
    if (featuredSettings[textStyle.id] == null) featuredSettings[textStyle.id] = HashMap()
    if (treeSettings[textStyle.id] == null) treeSettings[textStyle.id] = HashMap()
    addLanguage(textStyle, language, filterComponent)
  }

  fun addLanguage(textStyle: TextStyle, language: Language, filterComponent: SearchTextField) {
    val featuredSettingsPerLanguage = featuredSettings[textStyle.id]!!
    val treeSettingsPerLanguage = treeSettings[textStyle.id]!!
    if (language in featuredSettingsPerLanguage || language in treeSettingsPerLanguage) return

    val domain = textStyle.getTextDomain()
    val description = GrazieDescriptionComponent()
    val tree = GrazieTreeComponent(description.listener, language, domain, filterComponent)
    treeSettingsPerLanguage[language] = TreeSettings(description, tree)
    treeSettingsPerLanguage[language]!!.tree.reset(GrazieConfig.get())

    if (language !in ruleEngineLanguages) return

    val toolkit = LanguageToolkit.forLanguage(language)
    val spacing = IntelliJSpacingConfiguration()
    val ui = object : SettingComponent.UI {
      override fun getExamplePrefix(): String = GrazieBundle.message("grazie.settings.style.configurable.example.prefix")

      override fun getCorrectionPrefix(): String = GrazieBundle.message("grazie.settings.style.configurable.corrected.prefix")

      override fun navigateHyperlink(url: URL) {
        BrowserUtil.browse(url)
      }

      override fun externalLinkArrow(): Image = IconUtil.toBufferedImage(AllIcons.Ide.External_link_arrow, true)

      override fun createResetToDefaultComponent(action: Runnable): JComponent =
        createLinkLabel(GrazieBundle.message("grazie.settings.style.configurable.reset.to.default.link"), action)

      override fun createExpandComponent(examplesOnly: Boolean, doExpand: Runnable): JComponent {
        val text =
          if (examplesOnly) GrazieBundle.message("grazie.settings.style.configurable.expand.examples.link")
          else GrazieBundle.message("grazie.settings.style.configurable.expand.link")
        return createLinkLabel(text, doExpand)
      }

      override fun createCollapseComponent(examplesOnly: Boolean, doCollapse: Runnable): JComponent {
        val text =
          if (examplesOnly) GrazieBundle.message("grazie.settings.style.configurable.collapse.examples.link")
          else GrazieBundle.message("grazie.settings.style.configurable.collapse.link")
        return createLinkLabel(text, doCollapse)
      }

      override fun createGroupHeader(@NlsContexts.Label name: String): JComponent = createTitledSeparator(name, spacing)

      override fun customizeSettingSection(setting: Setting, section: JComponent) {
        section.border = JBEmptyBorder(0, spacing.horizontalIndent, spacing.verticalComponentGap, 0)
      }

      override fun customizeRuleDescription(pane: JEditorPane) {
        pane.font = JBFont.medium()
        pane.foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
      }
    }
    val component = SettingComponent(toolkit, RuleIdeClient.INSTANCE, ui)

    val settingState = getSettingsState(language, textStyle)
    component.loadState(settingState, textStyle)
    featuredSettingsPerLanguage[language] = FeaturedSettings(component, settingState)
  }

  fun updateFilter(textStyle: TextStyle, language: Language, option: String) {
    featuredSettings[textStyle.id]!![language]!!.component.filter(option)
    treeSettings[textStyle.id]!![language]!!.tree.filter(option)
  }

  fun clear() {
    featuredSettings.clear()
    treeSettings.clear()
  }

  private fun areModifiedFeaturedSettings(featuredSettings: MutableMap<Language, FeaturedSettings>): Boolean {
    return featuredSettings.any { areModifiedFeaturedSettings(it.value) }
  }

  private fun areModifiedTreeSettings(treeSettings: MutableMap<Language, TreeSettings>, state: GrazieConfig.State): Boolean {
    return treeSettings.any { areModifiedTreeSettings(it.value, state) }
  }

  private fun areModifiedFeaturedSettings(featuredSettings: FeaturedSettings): Boolean {
    return featuredSettings.component.state != featuredSettings.resetState
  }

  private fun areModifiedTreeSettings(treeSettings: TreeSettings, state: GrazieConfig.State): Boolean {
    return treeSettings.tree.isModified(state)
  }
}

data class FeaturedSettings(
  val component: SettingComponent,
  var resetState: SettingComponent.SettingState,
)

data class TreeSettings(
  val description: GrazieDescriptionComponent,
  val tree: GrazieTreeComponent,
)

private fun getSettingsState(language: Language, textStyle: TextStyle): SettingComponent.SettingState {
  val state = GrazieConfig.get()

  val prefix = Rule.globalIdPrefix(language)
  val domain = textStyle.getTextDomain()
  val (userEnabledRules, userDisabledRules) = state.getUserChangedRules(domain)
  val parameters = if (domain != TextStyleDomain.Other) state.parametersPerDomain[domain] else state.parameters
  return SettingComponent.SettingState(
    parameters?.get(language) ?: emptyMap(),
    userEnabledRules.filter { it.startsWith(prefix) }.map { it.substring(prefix.length) }.toSet(),
    userDisabledRules.filter { it.startsWith(prefix) }.map { it.substring(prefix.length) }.toSet(),
  )
}

private fun getTextStyle(textStyleId: String): TextStyle {
  return TextStyle.styles(RuleIdeClient.INSTANCE).find { it.id == textStyleId } ?: TextStyle.Unspecified
}

private fun createTitledSeparator(@NlsContexts.Label name: String, spacing: IntelliJSpacingConfiguration): TitledSeparator {
  val title = JBLabel(name)
  return createTitledSeparator(title, spacing)
}

private fun createTitledSeparator(title: JBLabel, spacing: IntelliJSpacingConfiguration): TitledSeparator {
  val separator = object : TitledSeparator(title.text) {
    override fun createLabel(): JBLabel = title
  }
  separator.border = JBEmptyBorder(spacing.verticalMediumGap, 0, spacing.verticalSmallGap, 0)
  separator.maximumSize = Dimension(Int.MAX_VALUE, separator.preferredSize.height)
  return separator
}

private fun createLinkLabel(@NlsContexts.LinkLabel text: String, onClick: Runnable): JComponent {
  val link = HyperlinkLabel(text)
  link.addHyperlinkListener { e: HyperlinkEvent ->
    if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
      onClick.run()
    }
  }
  link.setFontSize(FontSize.SMALL)
  link.updateUI()
  return link
}

private fun Row.writingStyleComboBox() = comboBox(
  CollectionComboBoxModel(getOtherDomainStyles()),
  SimpleListCellRenderer.create { label, value, _ ->
    label.text = GrazieBundle.messageOrNull("grazie.settings.style.profile.display.${value.id}")
                 ?: NameUtil.splitNameIntoWords(value.id).joinToString(" ")
  }
)

private fun Row.domainComboBox() = comboBox(
  CollectionComboBoxModel(TextStyleDomain.entries),
  SimpleListCellRenderer.create { label, value, _ ->
    label.text = GrazieBundle.messageOrNull("grazie.settings.domain.profile.display.$value")
  }
)

@Suppress("UNCHECKED_CAST")
private val <T> ComboBox<T>.selected: T?
  get() = this.selectedItem as? T