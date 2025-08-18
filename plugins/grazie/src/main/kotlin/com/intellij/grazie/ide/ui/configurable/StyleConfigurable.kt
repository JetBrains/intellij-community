package com.intellij.grazie.ide.ui.configurable

import ai.grazie.nlp.langs.Language
import ai.grazie.nlp.langs.utils.nativeName
import ai.grazie.rules.Rule
import ai.grazie.rules.settings.*
import ai.grazie.rules.toolkit.LanguageToolkit
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.ide.ui.grammar.GrazieConfigurable
import com.intellij.grazie.rule.RuleIdeClient
import com.intellij.grazie.rule.SentenceBatcher
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.DataManager
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableWithId
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.pom.Navigatable
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.ui.*
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.*
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil.FontSize
import com.intellij.util.ui.update.UiNotifyConnector
import java.awt.BorderLayout
import java.awt.Image
import java.net.URL
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.HyperlinkEvent

class StyleConfigurable:
  BoundConfigurable(GrazieBundle.message("grazie.settings.style.configurable.name"), null),
  ConfigurableWithId, Configurable.NoScroll { //todo searchable?

  private var focusedControl: JComponent? = null

  private var langData = LinkedHashMap<Language, LangData>()
  private val langComboModel = CollectionComboBoxModel(ArrayList<Language>())

  private val settingWrapper by lazy {
    JPanel(BorderLayout()).also { it.add(langData[Language.ENGLISH]!!.component) }
  }
  private lateinit var styleProfileCombo: ComboBox<TextStyle>
  private lateinit var langCombo: ComboBox<Language>

  private val config get() = GrazieConfig.get()

  private var styleProfile: TextStyle
    get() = config.textStyle
    set(value) = GrazieConfig.update { it.copy(styleProfile = value.id) }

  override fun getId() = ID

  @Suppress("UnstableApiUsage")
  private val component by lazy {
    ruleLanguages.forEach { addLanguage(it) }
    panel {
      row(GrazieBundle.message("grazie.settings.writing.style.text")) {
        styleProfileCombo = writingStyleComboBox()
          .widthGroup("TopCombo")
          .bindItem(::styleProfile.toNullableProperty())
          .whenItemSelectedFromUi { profile ->
            for (data in langData.values) {
              data.component.loadState(data.component.state, profile)
            }
          }
          .component
      }

      row(GrazieBundle.message("grazie.settings.style.language.chooser.label")) {
        langCombo = comboBox(
          langComboModel,
          SimpleListCellRenderer.create { label, lang, _ -> label.text = lang.nativeName }
        )
          .widthGroup("TopCombo")
          .whenItemSelectedFromUi {lang -> languageChanged(lang) }
          .component

        selectLanguage(Language.ENGLISH)
        trackNewLanguageAddition()

        link(GrazieBundle.message("grazie.settings.style.configure.all.rules.link")) {
          val settings = ideSettings()
          val configurable = settings?.find(GrazieConfigurable::class.java)
          if (configurable != null) {
            settings.select(configurable)
          } else {
            ShowSettingsUtil.getInstance().editConfigurable(settingWrapper, GrazieConfigurable())
          }
        }
      }

      row {
        cell(settingWrapper).resizableColumn().align(Align.FILL)
        resizableRow()
      }
    }
  }

  override fun createPanel(): DialogPanel = component

  private fun trackNewLanguageAddition() {
    GrazieConfig.subscribe(disposable!!) {
      for (lang in ruleLanguages) {
        if (lang !in langData) {
          addLanguage(lang)
        }
      }
    }
  }

  private fun createLinkLabel(text: String, onClick: Runnable): JComponent {
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

  private fun addLanguage(lang: Language) {
    if (lang !in ruleLanguages || SentenceBatcher.findInstalledLTLanguage(lang) == null) return

    val toolkit = LanguageToolkit.forLanguage(lang)
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

      override fun createGroupHeader(name: String): JComponent {
        val title = JBLabel(name)
        val separator = object : TitledSeparator(title.text) {
          override fun createLabel(): JBLabel = title
        }
        separator.border = JBEmptyBorder(spacing.verticalMediumGap, 0, spacing.verticalSmallGap, 0)
        return separator
      }

      override fun createFilterComponent(doFilter: Consumer<String>): JComponent {
        val field = SearchTextField(false)
        field.textEditor.emptyText.text = GrazieBundle.message("grazie.settings.style.search.placeholder")
        field.textEditor.document.addDocumentListener(object : DocumentAdapter() {
          override fun textChanged(e: DocumentEvent) {
            doFilter.accept(field.text)
          }
        })
        field.border = JBEmptyBorder(5)
        return field
      }

      override fun customizeSettingSection(setting: Setting, section: JComponent) {
        section.border = JBEmptyBorder(0, spacing.horizontalIndent, spacing.verticalComponentGap, 0)
      }

      override fun customizeRuleDescription(pane: JEditorPane) {
        pane.font = JBFont.medium()
        pane.foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
      }

      override fun shouldShowNavBar(groups: MutableList<SettingGroup>) = false
    }
    val comp = SettingComponent(toolkit, RuleIdeClient.INSTANCE, ui)

    val prefix = Rule.globalIdPrefix(lang)
    val affectedRules = affectedSettings(toolkit)
      .filterIsInstance<RuleSetting>()
      .map { prefix + it.rule.id }
      .toSet()

    langData[lang] = LangData(comp, SettingComponent.SettingState.UNCHANGED, affectedRules)
    langComboModel.add(lang)
  }

  private fun selectLanguage(lang: Language) {
    langCombo.selectedItem = lang
    languageChanged(lang)
  }

  private fun languageChanged(lang: Language) {
    settingWrapper.removeAll()
    settingWrapper.add(langData[lang]!!.component)
    settingWrapper.repaint()
  }

  override fun apply() {
    val prev = GrazieConfig.get()
    super.apply()

    val userEnabledRules = HashSet(GrazieConfig.get().userEnabledRules)
    val userDisabledRules = HashSet(GrazieConfig.get().userDisabledRules)
    val params = HashMap(GrazieConfig.get().parameters)

    for ((lang, data) in langData) {
      val prefix = Rule.globalIdPrefix(lang)
      val state = data.component.state
      data.resetState = state

      val affectedIds = data.affectedGlobalRuleIds
      userEnabledRules.removeIf { it.startsWith(prefix) && it in affectedIds }
      userDisabledRules.removeIf { it.startsWith(prefix) && it in affectedIds }

      for (id in state.enabledRules) {
        userEnabledRules.add(prefix + id)
        userDisabledRules.remove(prefix + id)
      }
      for (id in state.disabledRules) {
        userEnabledRules.remove(prefix + id)
        userDisabledRules.add(prefix + id)
      }

      params[lang] = state.paramValues
    }

    GrazieConfig.update { it.copy(parameters = params) }

    val liteConfigurable = ideSettings()?.find(GrazieConfigurable::class.java)

    if (userEnabledRules != GrazieConfig.get().userEnabledRules ||
        userDisabledRules != GrazieConfig.get().userDisabledRules) {
      GrazieConfig.update { it.copy(userEnabledRules = userEnabledRules, userDisabledRules = userDisabledRules) }
      liteConfigurable?.reset()
    }

    if (GrazieConfig.get().styleProfile != prev.styleProfile) {
      liteConfigurable?.ruleEnablednessChanged(GrazieConfig.get())
    }
  }

  override fun isModified(): Boolean {
    return super<BoundConfigurable>.isModified() ||
           langData.values.any { it.component.state != it.resetState }
  }

  override fun reset() {
    super<BoundConfigurable>.reset()

    val currentStyle = styleProfileCombo.selectedItem as TextStyle

    val userEnabledRules = GrazieConfig.get().userEnabledRules
    val userDisabledRules = GrazieConfig.get().userDisabledRules

    for ((lang, data) in langData) {
      val prefix = Rule.globalIdPrefix(lang)
      val langState = SettingComponent.SettingState(
        GrazieConfig.get().parameters[lang] ?: emptyMap(),
        userEnabledRules.filter { it.startsWith(prefix) }.map { it.substring(prefix.length) }.toSet(),
        userDisabledRules.filter { it.startsWith(prefix) }.map { it.substring(prefix.length) }.toSet(),
      )
      data.component.loadState(langState, currentStyle)
      data.resetState = data.component.state
    }
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    return focusedControl ?: super<BoundConfigurable>.getPreferredFocusedComponent()
  }

  private fun ideSettings() = DataManager.getInstance().getDataContext(settingWrapper).getData(Settings.KEY)

  companion object {
    internal const val ID = "reference.settings.grazie.pro.style"

    val ruleLanguages = listOf(Language.ENGLISH, Language.GERMAN, Language.RUSSIAN, Language.UKRAINIAN)


    @JvmStatic
    fun affectedSettings(toolkit: LanguageToolkit): List<Setting> = toolkit.getSettings(RuleIdeClient.INSTANCE).flatMap { it.settings }

    @JvmStatic
    fun focusSetting(setting: Setting, contextProject: Project?): Navigatable {
      return object : Navigatable {
        override fun navigate(requestFocus: Boolean) {
          ShowSettingsUtil.getInstance().showSettingsDialog(contextProject, StyleConfigurable::class.java) { conf ->
            conf.createComponent()
            for ((lang, data) in conf.langData) {
              val settingComponent = data.component.findParentSettingComponent(setting)
              val paramComponent = data.component.findOwnSettingComponent(setting)
              if (paramComponent != null && settingComponent != null) {
                conf.focusedControl = paramComponent
                conf.selectLanguage(lang)
                UiNotifyConnector.doWhenFirstShown(data.component) {
                  SwingUtilities.invokeLater {
                    data.component.scrollComponentToVisible(settingComponent)
                    IdeFocusManager.getInstance(contextProject).requestFocus(paramComponent, true)
                  }
                }
                break
              }
            }
          }
        }
        override fun canNavigate() = true
        override fun canNavigateToSource() = false
      }
    }
  }

  private data class LangData(
    val component: SettingComponent,
    var resetState: SettingComponent.SettingState,
    var affectedGlobalRuleIds: Set<String>
  )
}

fun Row.writingStyleComboBox() = comboBox(
  CollectionComboBoxModel(TextStyle.styles(RuleIdeClient.INSTANCE)),
  SimpleListCellRenderer.create { label, value, _ ->
    label.text = GrazieBundle.messageOrNull("grazie.settings.style.profile.display.${value.id}")
                 ?: NameUtil.splitNameIntoWords(value.id).joinToString(" ")
  }
)
