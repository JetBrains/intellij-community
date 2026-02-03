package com.intellij.grazie.ide.ui.configurable

import ai.grazie.gec.model.problem.ActionSuggestion
import ai.grazie.nlp.langs.Language
import ai.grazie.nlp.langs.utils.englishName
import ai.grazie.nlp.langs.utils.nativeName
import ai.grazie.rules.Rule
import ai.grazie.rules.settings.Setting
import ai.grazie.rules.settings.SettingComponent
import ai.grazie.rules.settings.TextStyle
import ai.grazie.rules.toolkit.LanguageToolkit
import ai.grazie.rules.tree.Parameter
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.detection.toLanguage
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.ide.ui.configurable.StyleConfigurable.Companion.ruleEngineLanguages
import com.intellij.grazie.ide.ui.grammar.tabs.rules.component.GrazieDescriptionComponent
import com.intellij.grazie.ide.ui.grammar.tabs.rules.component.GrazieTreeComponent
import com.intellij.grazie.rule.RuleIdeClient
import com.intellij.grazie.utils.TextStyleDomain
import com.intellij.grazie.utils.featuredSettings
import com.intellij.grazie.utils.getAffectedGlobalRules
import com.intellij.grazie.utils.getOtherDomainStyles
import com.intellij.grazie.utils.getTextDomain
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
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.JBSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.IntelliJSpacingConfiguration
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toNullableProperty
import com.intellij.ui.dsl.builder.whenItemSelectedFromUi
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
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
import javax.swing.SwingUtilities
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
      val treeSettings = settings.getTreeSettings(textStyle, Language.ENGLISH)
      treeSettings.description.listener(Language.ENGLISH)
      firstComponent = createScrollTreeComponent(textStyle, Language.ENGLISH)
      secondComponent = treeSettings.description.component
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
            settings.updateFilter(textStyle, language, filterComponent.text)
          }
          .component
        langCombo.selectedItem = Language.ENGLISH
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
    settings.updateFilter(textStyle, language, filterComponent.text)
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
    val treeSettings = settings.getTreeSettings(textStyle, language)
    treeSettings.description.listener(language)
    treeWrapper.removeAll()
    treeWrapper.firstComponent = createScrollTreeComponent(textStyle, language)
    treeWrapper.secondComponent = treeSettings.description.component
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
    fun focusSetting(parameter: ActionSuggestion.ChangeParameter, domain: TextStyleDomain, language: Language, project: Project): Boolean {
      val configurable = StyleConfigurable().apply {
        createComponent()
        val style = if (domain == TextStyleDomain.Other) GrazieConfig.get().getTextStyle() else domain.textStyle
        selectTextStyle(style, language)
        featuredSettings(language)
          .filterIsInstance<Parameter>()
          .find { it.id() == parameter.parameterId }
          ?.let { focusFeaturedSetting(this, it, style, language, project) }
      }
      return ShowSettingsUtil.getInstance().editConfigurable(project, configurable)
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

    @JvmStatic
    fun open(project: Project?): Boolean = ShowSettingsUtil.getInstance().editConfigurable(project, StyleConfigurable())
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
  val combinedSettings: MutableMap<String, MutableMap<Language, CombinedSettings>> = HashMap(),
) {
  fun getFeaturedSettings(textStyle: TextStyle, language: Language): FeaturedSettings? = combinedSettings[textStyle.id]!![language]!!.featuredSettings
  fun getTreeSettings(textStyle: TextStyle, language: Language): TreeSettings = combinedSettings[textStyle.id]!![language]!!.treeSettings

  fun isModified(state: GrazieConfig.State): Boolean =
    combinedSettings.values.any { settings -> settings.values.any { it.isModified(state) } }

  fun reset(state: GrazieConfig.State) {
    combinedSettings.forEach { (textStyleId, settingsMap) ->
      settingsMap.forEach { (language, settings) ->
        settings.reset(state, getTextStyle(textStyleId), language)
      }
    }
  }

  fun apply(originalState: GrazieConfig.State) {
    combinedSettings
      .filter { it.value.any { settings -> settings.value.isModified(originalState) } }
      .forEach { (textStyleId, settingsMap) ->
        val domain = getTextStyle(textStyleId).getTextDomain()
        val userEnabledRules = HashSet<String>()
        val userDisabledRules = HashSet<String>()
        val parameters = HashMap<Language, Map<String, String>>()

        val changedSettings = settingsMap.filter { settings -> settings.value.isModified(originalState) }
        changedSettings.forEach { (language, combinedSettings) ->
          val userEnabledRulesPerLanguage = HashSet<String>()
          val userDisabledRulesPerLanguage = HashSet<String>()

          combinedSettings.featuredSettings?.let { featuredSettings ->
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

          val updatedState = combinedSettings.treeSettings.tree.apply(originalState)
          val affectedGlobalRules = getAffectedGlobalRules(language)
          updatedState.getUserChangedRules(domain).let { (enabledRules, disabledRules) ->
            userEnabledRulesPerLanguage.addAll(enabledRules - affectedGlobalRules)
            userDisabledRulesPerLanguage.addAll(disabledRules - affectedGlobalRules)
          }
          userEnabledRules.addAll(userEnabledRulesPerLanguage)
          userDisabledRules.addAll(userDisabledRulesPerLanguage)
        }

        GrazieConfig.update {
          val withParameters = if (domain == TextStyleDomain.Other) it.copy(parameters = parameters) else it.copy(parametersPerDomain = mapOf(domain to parameters))
          withParameters.updateUserRules(domain, userEnabledRules, userDisabledRules)
        }
        changedSettings.forEach { (_, settings) -> settings.treeSettings.tree.reset(GrazieConfig.get()) }
      }
  }

  fun addTextStyle(textStyle: TextStyle, language: Language, filterComponent: SearchTextField) {
    val settings = combinedSettings[textStyle.id]
    if (settings != null && language in settings) return
    if (settings == null) combinedSettings[textStyle.id] = HashMap()
    addLanguage(textStyle, language, filterComponent)
  }

  fun addLanguage(textStyle: TextStyle, language: Language, filterComponent: SearchTextField) {
    val settingsPerLanguage = combinedSettings[textStyle.id]!!
    if (language in settingsPerLanguage) return

    val domain = textStyle.getTextDomain()
    val description = GrazieDescriptionComponent()
    val tree = GrazieTreeComponent(description.listener, language, domain, filterComponent)
    tree.reset(GrazieConfig.get())

    if (language !in ruleEngineLanguages) {
      settingsPerLanguage[language] = CombinedSettings(null, TreeSettings(description, tree))
      return
    }

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
    settingsPerLanguage[language] = CombinedSettings(FeaturedSettings(component, settingState), TreeSettings(description, tree))
  }

  fun updateFilter(textStyle: TextStyle, language: Language, option: String): Unit =
    combinedSettings[textStyle.id]!![language]!!.updateFilter(option)

  fun clear(): Unit = combinedSettings.clear()
}

data class CombinedSettings(
  val featuredSettings: FeaturedSettings?,
  val treeSettings: TreeSettings,
) {

  fun isModified(state: GrazieConfig.State): Boolean {
    return areModifiedFeaturedSettings(featuredSettings) || areModifiedTreeSettings(treeSettings, state)
  }

  fun reset(state: GrazieConfig.State, textStyle: TextStyle, language: Language) {
    if (areModifiedFeaturedSettings(featuredSettings)) {
      featuredSettings!!.component.loadState(getSettingsState(language, textStyle), textStyle)
      featuredSettings.resetState = featuredSettings.component.state
    }
    if (areModifiedTreeSettings(treeSettings, state)) treeSettings.tree.reset(state)
  }

  fun updateFilter(option: String) {
    featuredSettings?.component?.filter(option)
    treeSettings.tree.filter(option)
  }

  private fun areModifiedFeaturedSettings(featuredSettings: FeaturedSettings?): Boolean {
    return featuredSettings != null && featuredSettings.component.state != featuredSettings.resetState
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

fun Row.writingStyleComboBox() = comboBox(
  CollectionComboBoxModel(getOtherDomainStyles()),
  SimpleListCellRenderer.create { label, value, _ ->
    label.text = GrazieBundle.messageOrNull("grazie.settings.style.profile.display.${value.id}")
                 ?: NameUtil.splitNameIntoWordList(value.id).joinToString(" ")
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