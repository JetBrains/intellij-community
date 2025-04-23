// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.openapi.editor.colors.impl

import com.intellij.configurationStore.BundledSchemeEP
import com.intellij.configurationStore.LazySchemeProcessor
import com.intellij.configurationStore.SchemeDataHolder
import com.intellij.configurationStore.SchemeExtensionProvider
import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.PluginException
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.laf.TempUIThemeLookAndFeelInfo
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo
import com.intellij.ide.ui.laf.UiThemeProviderListManager
import com.intellij.ide.util.RunOnceUtil.runOnceForApp
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.Scheme
import com.intellij.openapi.options.SchemeManager
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.options.SchemeState
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.JDOMUtil
import com.intellij.psi.PsiManager
import com.intellij.serviceContainer.NonInjectable
import com.intellij.ui.ColorUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.util.ComponentTreeEventDispatcher
import com.intellij.util.ResourceUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.xml.dom.createXmlStreamReader
import com.intellij.util.xmlb.annotations.OptionTag
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

private val LOG: Logger
  get() = logger<EditorColorsManagerImpl>()

private const val TEMP_SCHEME_KEY: String = "TEMP_SCHEME_KEY"
private const val TEMP_SCHEME_FILE_KEY: String = "TEMP_SCHEME_FILE_KEY"

@State(
  name = EditorColorsManagerImpl.COMPONENT_NAME,
  storages = [Storage(EditorColorsManagerImpl.STORAGE_NAME)],
  additionalExportDirectory = EditorColorsManagerImpl.FILE_SPEC,
  category = SettingsCategory.UI
)
@ApiStatus.Internal
class EditorColorsManagerImpl @NonInjectable constructor(schemeManagerFactory: SchemeManagerFactory)
  : EditorColorsManager(), PersistentStateComponent<EditorColorsManagerImpl.State?>
{
  private val treeDispatcher = ComponentTreeEventDispatcher.create(EditorColorsListener::class.java)

  private val schemeModificationCounter = AtomicLong()

  val schemeManager: SchemeManager<EditorColorsScheme>
  private var state = State()
  private var themeIsCustomized = false
  private var isInitialConfigurationLoaded = false

  constructor() : this(SchemeManagerFactory.getInstance())

  init {
    val additionalTextAttributes = HashMap<String, MutableList<AdditionalTextAttributesEP>>()
    ADDITIONAL_TEXT_ATTRIBUTES_EP_NAME.forEachExtensionSafe {
      additionalTextAttributes.computeIfAbsent(it.scheme) { ArrayList() }.add(it)
    }
    schemeManager = schemeManagerFactory.create(
      directoryName = FILE_SPEC,
      EditorColorSchemeProcessor(additionalTextAttributes),
      presentableName = null,
      directoryPath = null,
      SettingsCategory.UI
    )
    for (defaultScheme in DefaultColorSchemesManager.getInstance().allSchemes) {
      schemeManager.addScheme(defaultScheme)
    }
    if (!isHeadlessMode) {
      schemeManager.loadBundledSchemes(createLoadBundledSchemeRequests(additionalTextAttributes, checkId = false, schemeManager::findSchemeByName))
    }
    schemeManager.loadSchemes()
    loadRemainAdditionalTextAttributes(additionalTextAttributes)
    initEditableDefaultSchemesCopies()
    initEditableBundledSchemesCopies()
    resolveLinksToBundledSchemes()
  }

  companion object {
    @VisibleForTesting
    val ADDITIONAL_TEXT_ATTRIBUTES_EP_NAME: ExtensionPointName<AdditionalTextAttributesEP> =
      ExtensionPointName("com.intellij.additionalTextAttributes")

    const val COMPONENT_NAME: String = "EditorColorsManagerImpl"
    const val STORAGE_NAME: String = "colors.scheme.xml"
    const val FILE_SPEC: String = "colors"

    fun isTempScheme(scheme: EditorColorsScheme?): Boolean =
      scheme?.getMetaProperties()?.getProperty(TEMP_SCHEME_KEY).toBoolean()

    fun getTempSchemeOriginalFilePath(scheme: EditorColorsScheme): Path? {
      if (isTempScheme(scheme)) {
        val path = scheme.getMetaProperties().getProperty(TEMP_SCHEME_FILE_KEY)
        if (path != null) {
          return Path.of(path)
        }
      }
      return null
    }

    @JvmStatic
    fun fireGlobalSchemeChange(newScheme: EditorColorsScheme?) {
      val manager = getInstance()
      if (manager is EditorColorsManagerImpl) {
        manager.schemeChangedOrSwitched(newScheme)
      }
      else {
        // This branch may not be necessary, but we've had such calls scattered around the codebase,
        // bypassing the manager, so it's better to have a fallback here in case of some unusual environment, like a test.
        runCatching {
          ApplicationManager.getApplication().getMessageBus().syncPublisher(TOPIC).globalSchemeChange(newScheme)
        }.getOrLogException(LOG)
      }
    }
  }

  override fun getSchemeModificationCounter(): Long = schemeModificationCounter.get()

  override fun reloadKeepingActiveScheme() {
    val activeScheme = schemeManager.currentSchemeName
    schemeManager.reload()

    if (!activeScheme.isNullOrEmpty()) {
      getScheme(activeScheme)?.let {
        setGlobalScheme(it)
      }
    }
  }

  // initScheme has to execute only after the LaF has been set in LafManagerImpl.initializeComponent
  private fun initEditableDefaultSchemesCopies() {
    val to = ArrayList<EditorColorsScheme>()
    for (defaultScheme in DefaultColorSchemesManager.getInstance().allSchemes) {
      if (defaultScheme.hasEditableCopy()) {
        createEditableCopy(initialScheme = defaultScheme, editableCopyName = defaultScheme.editableCopyName, to = to)
      }
    }

    for (scheme in to) {
      schemeManager.addScheme(scheme)
    }
  }

  @TestOnly
  fun removeScheme(scheme: EditorColorsScheme) {
    assert(ApplicationManager.getApplication().isUnitTestMode()) { "Test-only method" }
    schemeManager.removeScheme(scheme)
  }

  private fun initEditableBundledSchemesCopies() {
    val to = ArrayList<EditorColorsScheme>()
    // process over allSchemes snapshot
    for (scheme in schemeManager.allSchemes.toList()) {
      if (scheme is BundledEditorColorScheme) {
        createEditableCopy(initialScheme = scheme, editableCopyName = Scheme.EDITABLE_COPY_PREFIX + scheme.name, to)
      }
    }
    for (scheme in to) {
      schemeManager.addScheme(scheme)
    }
  }

  private fun resolveLinksToBundledSchemes() {
    val brokenSchemesList = ArrayList<EditorColorsScheme>()
    for (scheme in schemeManager.allSchemes) {
      try {
        resolveSchemeParent(scheme)
      }
      catch (e: InvalidDataException) {
        LOG.warn("Skipping '${scheme.getName()}' because its parent scheme '${e.message}' is missing.")
        brokenSchemesList.add(scheme)
      }
    }
    for (brokenScheme in brokenSchemesList) {
      if (brokenScheme is EditorColorsSchemeImpl && !brokenScheme.isReadOnly) {
        brokenScheme.isVisible = false
      }
      else {
        schemeManager.removeScheme(brokenScheme)
      }
    }
  }

  override fun resolveSchemeParent(scheme: EditorColorsScheme) {
    if (scheme is AbstractColorsScheme && !scheme.isReadOnly) {
      scheme.resolveParent(schemeManager::findSchemeByName)
    }
  }

  private fun createEditableCopy(initialScheme: AbstractColorsScheme, editableCopyName: String, to: MutableList<EditorColorsScheme>) {
    var editableCopy = getScheme(editableCopyName) as AbstractColorsScheme?
    if (editableCopy == null) {
      editableCopy = initialScheme.clone() as AbstractColorsScheme
      editableCopy.name = editableCopyName
      to.add(editableCopy)
    }
    else if (initialScheme is BundledEditorColorScheme) {
      editableCopy.copyMissingAttributes(initialScheme)
    }
    editableCopy.setCanBeDeleted(false)
  }

  fun schemeChangedOrSwitched(newScheme: EditorColorsScheme?) {
    dropPsiCaches()
    callGlobalSchemeChange(newScheme)
  }

  override fun getSchemeForCurrentUITheme(): EditorColorsScheme {
    val lookAndFeelInfo: UIThemeLookAndFeelInfo? = LafManager.getInstance().getCurrentUIThemeLookAndFeel()
    var scheme: EditorColorsScheme? = null
    if (lookAndFeelInfo is TempUIThemeLookAndFeelInfo) {
      val globalScheme = getGlobalScheme()
      if (isTempScheme(globalScheme)) {
        return globalScheme
      }
    }

    if (lookAndFeelInfo != null) {
      val schemeId = lookAndFeelInfo.editorSchemeId
      if (schemeId != null) {
        scheme = getScheme(schemeId)
        if (scheme == null) {
          LOG.error("Theme ${lookAndFeelInfo.name} refers to unknown color scheme $schemeId")
        }
      }
    }

    if (scheme == null) {
      val schemeName = if (lookAndFeelInfo != null && lookAndFeelInfo.isDark) "Darcula" else getDefaultSchemeName()
      val defaultColorSchemeManager = DefaultColorSchemesManager.getInstance()
      scheme = defaultColorSchemeManager.getScheme(schemeName)
      if (scheme == null) {
        LOG.error("The scheme '$schemeName' not found, available schemes: ${defaultColorSchemeManager.listNames()}")
      }
    }
    return getEditableCopy(scheme!!) ?: scheme
  }

  private fun loadRemainAdditionalTextAttributes(additionalTextAttributes: MutableMap<String, MutableList<AdditionalTextAttributesEP>>) {
    for ((schemeName, value) in additionalTextAttributes) {
      val scheme = schemeManager.findSchemeByName(schemeName)
      if (scheme !is AbstractColorsScheme) {
        if (!isHeadlessMode) {
          LOG.warn("Cannot find scheme: $schemeName from plugins: " +
                   value.joinToString(separator = ";") { it.pluginDescriptor.getPluginId().idString })
        }
        continue
      }
      loadAdditionalTextAttributesForScheme(scheme = scheme, attributeEps = value)
    }
    additionalTextAttributes.clear()
  }

  class State {
    @JvmField
    @Suppress("PropertyName")
    var USE_ONLY_MONOSPACED_FONTS: Boolean = true

    @JvmField
    @OptionTag(tag = "global_color_scheme", nameAttribute = "", valueAttribute = "name")
    var colorScheme: String? = null
  }

  fun getDefaultAttributes(key: TextAttributesKey): TextAttributes? {
    @Suppress("DEPRECATION") val dark = StartupUiUtil.isUnderDarcula && getScheme("Darcula") != null
    // It is reasonable to fetch attributes from a Default color scheme.
    // Otherwise, if we launch IDE and then try to switch from a custom colors scheme (e.g., with a dark background) to the default one.
    // The editor will show incorrect highlighting with "traces" of a color scheme which was active during IDE startup.
    return getScheme(if (dark) "Darcula" else EditorColorsScheme.getDefaultSchemeName())?.getAttributes(key)
  }

  override fun addColorScheme(scheme: EditorColorsScheme) {
    if (!isDefaultScheme(scheme) && !scheme.getName().isEmpty()) {
      schemeManager.addScheme(scheme)
    }
  }

  override fun getAllSchemes(): Array<EditorColorsScheme> =
    schemeManager.allSchemes.asSequence()
      .filter { AbstractColorsScheme.isVisible(it) }
      .sortedWith(EditorColorSchemesComparator.INSTANCE)
      .toList()
      .toTypedArray()

  fun setGlobalScheme(scheme: EditorColorsScheme?, processChangeSynchronously: Boolean) {
    val notify = LoadingState.COMPONENTS_LOADED.isOccurred
    schemeManager.setCurrent(scheme ?: getEditableDefaultScheme(), notify, processChangeSynchronously)
  }

  override fun setGlobalScheme(scheme: EditorColorsScheme?) {
    setGlobalScheme(scheme, processChangeSynchronously = false)
  }

  @RequiresEdt
  override fun setCurrentSchemeOnLafChange(scheme: EditorColorsScheme) {
    if (scheme === schemeManager.activeScheme) {
      return
    }

    schemeManager.setCurrent(scheme = scheme, notify = false, processChangeSynchronously = false)
    if (!LoadingState.COMPONENTS_LOADED.isOccurred) {
      return
    }

    callGlobalSchemeChange(scheme)

    // don't do heavy operations right away
    ApplicationManager.getApplication().invokeLater {
      dropPsiCaches()
    }
  }

  // refreshAllEditors is not enough - for example, change "Errors and warnings -> Typo" from green (default) to red
  @RequiresEdt
  private fun dropPsiCaches() {
    for (project in ProjectManager.getInstance().getOpenProjects()) {
      PsiManager.getInstance(project).dropPsiCaches()
    }
  }

  @RequiresEdt
  private fun callGlobalSchemeChange(scheme: EditorColorsScheme?) {
    schemeModificationCounter.incrementAndGet()
    // we need to push events to components that use editor font, e.g., HTML editor panes
    runCatching {
      ApplicationManager.getApplication().getMessageBus().syncPublisher(TOPIC).globalSchemeChange(scheme)
    }.getOrLogException(LOG)
    runCatching {
      treeDispatcher.multicaster.globalSchemeChange(scheme)
    }.getOrLogException(LOG)
  }

  override fun getGlobalScheme(): EditorColorsScheme = activeVisibleScheme ?: getEditableDefaultScheme()

  override fun getActiveVisibleScheme(): EditorColorsScheme? {
    val scheme = schemeManager.activeScheme
    if (scheme is AbstractColorsScheme && !scheme.isReadOnly && !scheme.isVisible) {
      return when (val parentScheme = scheme.getParentScheme()) {
        null -> {
          LOG.error("Parent scheme for '${scheme.name}' is null!")
          null
        }
        is DefaultColorsScheme -> getScheme(parentScheme.editableCopyName)
        else -> {
          LOG.error("Color scheme '${parentScheme.name}' is not a DefaultColorsScheme!")
          null
        }
      }
    }
    return scheme?.let { getEditableCopy(it) } ?: scheme
  }

  private fun getEditableDefaultScheme(): EditorColorsScheme {
    val defaultScheme = DefaultColorSchemesManager.getInstance().firstScheme
    val editableCopyName = defaultScheme.editableCopyName
    val editableCopy = getScheme(editableCopyName)
    if (editableCopy == null) {
      LOG.error("An editable copy of ${defaultScheme.name} has not been initialized.")
      return defaultScheme
    }
    return editableCopy
  }

  override fun getScheme(schemeName: String): EditorColorsScheme? {
    if (schemeName.endsWith(".xml")) {
      val path = schemeName.removeSuffix(".xml").removePrefix("/")
      return schemeManager.allSchemes.firstOrNull {
        val base = if (it is EditorColorsSchemeImpl && it.name.startsWith(Scheme.EDITABLE_COPY_PREFIX)) it.original else null
        base is BundledEditorColorScheme && base.resourcePath.removeSuffix(".xml") == path
      }
    }
    return schemeManager.findSchemeByName(schemeName)
  }

  private fun getEditableCopy(scheme: EditorColorsScheme): EditorColorsScheme? {
    if (isTempScheme(scheme)) {
      return scheme
    }
    val editableCopyName = when {
      scheme is DefaultColorsScheme && scheme.hasEditableCopy() -> scheme.editableCopyName
      scheme is BundledEditorColorScheme -> Scheme.EDITABLE_COPY_PREFIX + scheme.name
      else -> null
    }
    if (editableCopyName != null) {
      getScheme(editableCopyName)?.let {
        return it
      }
    }
    return null
  }

  override fun getState(): State {
    val currentSchemeName = schemeManager.currentSchemeName
    if (currentSchemeName != null && !isTempScheme(schemeManager.activeScheme)) {
      state.colorScheme = currentSchemeName
    }
    return state
  }

  override fun isUseOnlyMonospacedFonts(): Boolean = state.USE_ONLY_MONOSPACED_FONTS

  override fun setUseOnlyMonospacedFonts(value: Boolean) {
    state.USE_ONLY_MONOSPACED_FONTS = value
  }

  override fun loadState(state: State) {
    hideIntellijLightSchemeIfNeeded(state)
    this.state = state
    val colorSchemeName = state.colorScheme
    var colorScheme = colorSchemeName?.let { getScheme(it) }
    if (colorScheme == null) {
      if (colorSchemeName != null) {
        LOG.warn("$colorSchemeName color scheme is missing")
      }
      noStateLoaded()
      return
    }

    themeIsCustomized = true
    val schemeName = colorScheme.getName()
    //todo[kb] remove after 23.1 EAPs
    // New Dark RC is renamed to Dark, switch the scheme accordingly
    if (ExperimentalUI.isNewUI() && (schemeName == "_@user_New Dark RC" || schemeName == "New Dark RC")) {
      runOnceForApp("force.switch.from.new.dark.editor.scheme") {
        schemeManager.findSchemeByName("Dark")?.let {
          colorScheme = it
        }
      }
    }
    schemeManager.setCurrent(scheme = colorScheme, notify = isInitialConfigurationLoaded)
    isInitialConfigurationLoaded = true

    @Suppress("UNNECESSARY_SAFE_CALL")
    colorScheme?.let {
      notifyAboutSolarizedColorSchemeDeprecationIfSet(scheme = it)
    }

    val activity = StartUpMeasurer.startActivity("editor color scheme initialization")
    val laf = if (ApplicationManager.getApplication().isUnitTestMode()) null else LafManager.getInstance().getCurrentUIThemeLookAndFeel()
    // null in a headless mode
    if (laf != null && !themeIsCustomized) {
      laf.editorSchemeId?.let { getScheme(it) }?.let {
        schemeManager.setCurrent(scheme = it, notify = false)
      }
    }
    activity.end()
  }

  override fun noStateLoaded() {
    themeIsCustomized = false

    val activity = StartUpMeasurer.startActivity("editor color scheme initialization")
    val laf = if (ApplicationManager.getApplication().isUnitTestMode()) null else LafManager.getInstance().getCurrentUIThemeLookAndFeel()

    var editorSchemeId = laf?.editorSchemeId
    if (editorSchemeId == null && laf != null && laf.isDark) {
      editorSchemeId = "Darcula"
    }

    val scheme = editorSchemeId?.let { getScheme(it) } ?: getEditableDefaultScheme()
    schemeManager.setCurrent(scheme = scheme, notify = isInitialConfigurationLoaded)
    isInitialConfigurationLoaded = true
    activity.end()
  }

  private fun hideIntellijLightSchemeIfNeeded(state: State) {
    if (!ExperimentalUI.isNewUI()) return
    val themeName = "IntelliJ Light"
    val intellijLightScheme = schemeManager.findSchemeByName(themeName) ?: return
    var noIntellijLightCustomThemes = true
    schemeManager.allSchemes.filter {
      it.metaProperties.any { property ->
        property.key == "originalScheme" && property.value == themeName
      }
    }.forEach { theme ->
      val isSchemeCustomized = (theme as? AbstractColorsScheme)?.settingsEqual(intellijLightScheme, null, true, false) != true
      if (isSchemeCustomized) {
        noIntellijLightCustomThemes = false
        return@forEach
      }
      if (state.colorScheme == theme.name) state.colorScheme = "Light"
      schemeManager.removeScheme(theme)
    }
    if (noIntellijLightCustomThemes) {
      if (state.colorScheme == themeName) state.colorScheme = "Light"
      schemeManager.removeScheme(themeName)
    }
  }

  override fun isDefaultScheme(scheme: EditorColorsScheme): Boolean = scheme is DefaultColorsScheme

  private inner class EditorColorSchemeProcessor(
    private val additionalTextAttributes: MutableMap<String, MutableList<AdditionalTextAttributesEP>>,
  ) : LazySchemeProcessor<EditorColorsScheme, EditorColorsSchemeImpl>(), SchemeExtensionProvider {
    override fun createScheme(
      dataHolder: SchemeDataHolder<EditorColorsSchemeImpl>,
      name: String,
      attributeProvider: (String) -> String?,
      isBundled: Boolean,
    ): EditorColorsSchemeImpl {
      // do we have BundledEditorColorScheme here?
      val scheme = if (isBundled) BundledEditorColorScheme(name) else EditorColorsSchemeImpl(null)
      // todo be lazy
      scheme.readExternal(dataHolder.read())
      // We don't need to update digest for a bundled scheme because:
      // 1) it can be computed on demand later (because a bundled scheme is not mutable)
      // 2) in the future, user copies of bundled schemes will use a bundled scheme as parent (not as full copy)
      if (isBundled ||
          (ApplicationManager.getApplication().isUnitTestMode() && scheme.metaProperties.getProperty("forceOptimize").toBoolean())) {
        val parentScheme = scheme.getParentScheme()
        if (parentScheme is AbstractColorsScheme) {
          val attributesEPs = additionalTextAttributes.remove(parentScheme.name)
          if (!attributesEPs.isNullOrEmpty()) {
            loadAdditionalTextAttributesForScheme(scheme = parentScheme, attributeEps = attributesEPs)
          }
        }

        scheme.optimizeAttributeMap()
      }
      return scheme
    }

    override fun getState(scheme: EditorColorsScheme): SchemeState =
      if (scheme.isReadOnly) SchemeState.NON_PERSISTENT else SchemeState.POSSIBLY_CHANGED

    override fun onCurrentSchemeSwitched(
      oldScheme: EditorColorsScheme?,
      newScheme: EditorColorsScheme?,
      processChangeSynchronously: Boolean,
    ) {
      // the method receives the base scheme as the argument, but the actual scheme might be different
      val actualNewScheme = if (schemeManager.activeScheme == newScheme) {
        activeVisibleScheme
      }
      else {
        newScheme
      }
      if (processChangeSynchronously) {
        handleCurrentSchemeSwitched(actualNewScheme)
      }
      else {
        // don't do heavy operations right away
        ApplicationManager.getApplication().invokeLater {
          handleCurrentSchemeSwitched(actualNewScheme)
        }
      }
    }

    private fun handleCurrentSchemeSwitched(newScheme: EditorColorsScheme?) {
      LafManager.getInstance().updateUI()
      schemeChangedOrSwitched(newScheme)
    }

    override val schemeExtension: String
      get() = getColorSchemeFileExtension()

    override fun isSchemeEqualToBundled(scheme: EditorColorsSchemeImpl): Boolean {
      if (!scheme.name.startsWith(Scheme.EDITABLE_COPY_PREFIX)) {
        return false
      }
      val bundledScheme =
        (schemeManager.findSchemeByName(scheme.name.substring(Scheme.EDITABLE_COPY_PREFIX.length)) as AbstractColorsScheme?)
        ?: return false
      return scheme.settingsEqual(bundledScheme)
    }

    override fun reloaded(schemeManager: SchemeManager<EditorColorsScheme>, schemes: Collection<EditorColorsScheme>) {
      if (!isHeadlessMode) {
        schemeManager.loadBundledSchemes(createLoadBundledSchemeRequests(additionalTextAttributes, checkId = false, schemeManager::findSchemeByName))
      }
      initEditableDefaultSchemesCopies()
      initEditableBundledSchemesCopies()
      hideIntellijLightSchemeIfNeeded(state)
      ApplicationManager.getApplication().getMessageBus().syncPublisher(EditorColorsManagerListener.TOPIC).schemesReloaded()
    }
  }

  private val isHeadlessMode: Boolean
    get() = ApplicationManager.getApplication().isHeadlessEnvironment()

  private fun notifyAboutSolarizedColorSchemeDeprecationIfSet(scheme: EditorColorsScheme) {
    val solarizedColorSchemeNames = setOf(
      "Solarized (dark)", "Solarized (light)", "Solarized Dark", "Solarized Light", "Solarized Dark (Darcula)"
    )

    val name = scheme.getName().removePrefix(Scheme.EDITABLE_COPY_PREFIX)
    if (!solarizedColorSchemeNames.contains(name)) {
      return
    }

    if (name == "Solarized Dark" || name == "Solarized Light") {
      @Suppress("SpellCheckingInspection")
      val solarizedPluginsContainingSchemesWithTheSameName = arrayOf(
        PluginId.getId("solarized"),
        PluginId.getId("com.tylerthrailkill.intellij.solarized")
      )
      for (t in solarizedPluginsContainingSchemesWithTheSameName) {
        if (PluginManager.getInstance().findEnabledPlugin(t) != null) {
          return
        }
      }
    }

    val connection = ApplicationManager.getApplication().getMessageBus().connect()
    connection.subscribe<ProjectManagerListener>(ProjectManager.TOPIC, object : ProjectManagerListener {
      @Suppress("removal", "OVERRIDE_DEPRECATION")
      override fun projectOpened(project: Project) {
        connection.disconnect()

        ApplicationManager.getApplication().invokeLater(
          {
            val pluginId = PluginId.getId("com.4lex4.intellij.solarized")
            val isDark = ColorUtil.isDark(scheme.getDefaultBackground())
            val neededThemeName = if (isDark) "Solarized Dark" else "Solarized Light"
            val neededTheme = UiThemeProviderListManager.getInstance().findThemeByName(neededThemeName)
            val notification = Notification("ColorSchemeDeprecation", IdeBundle.message(
              "notification.title.solarized.color.scheme.deprecation"), NotificationType.ERROR)
            if (neededTheme != null) {
              notification.setContent(IdeBundle.message(
                "notification.content.solarized.color.scheme.deprecation.enable", name,
                neededThemeName))
              notification.addAction(object : NotificationAction(IdeBundle.message(
                "notification.title.enable.action.solarized.color.scheme.deprecation",
                neededThemeName)) {
                override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                  val lafManager = LafManager.getInstance()
                  lafManager.setCurrentLookAndFeel(neededTheme, false)
                  lafManager.updateUI()
                  notification.expire()
                }
              })
            }
            else {
              notification.setContent(IdeBundle.message(
                "notification.content.solarized.color.scheme.deprecation.install", name,
                "Solarized Themes"))
              notification.addAction(object : NotificationAction(IdeBundle.message(
                "notification.title.install.action.solarized.color.scheme.deprecation")) {
                override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                  @Suppress("NAME_SHADOWING")
                  val connection = ApplicationManager.getApplication().getMessageBus().connect()
                  // Needed to enable matching theme after plugin installation.
                  // Since the plugin provides two themes, we need to wait for both of them to be added
                  // (and applied) to reapply the needed one if it wasn't added last.
                  connection.subscribe(LafManagerListener.TOPIC, object : LafManagerListener {
                    private var matchingTheme: UIThemeLookAndFeelInfo? = null
                    private var otherWasSet: Boolean = false

                    override fun lookAndFeelChanged(source: LafManager) {
                      val themeInfo = source.getCurrentUIThemeLookAndFeel()
                      if (themeInfo.name.contains("Solarized")) {
                        if ((isDark && themeInfo.isDark) || (!isDark && !themeInfo.isDark)) {
                          matchingTheme = themeInfo
                        }
                        else {
                          otherWasSet = true
                        }
                      }

                      if (matchingTheme != null && otherWasSet) {
                        connection.disconnect()

                        if (source.getCurrentUIThemeLookAndFeel() != matchingTheme) {
                          source.setCurrentLookAndFeel(matchingTheme!!, false)
                          source.updateUI()
                        }
                      }
                    }
                  })

                  installAndEnable(project = project, pluginIds = setOf(pluginId), onSuccess = notification::expire)
                }
              })
            }
            notification.notify(project)
          },
          ModalityState.nonModal(),
        )
      }
    })
  }
}

private fun loadAdditionalTextAttributesForScheme(scheme: AbstractColorsScheme, attributeEps: Collection<AdditionalTextAttributesEP>) {
  for (attributesEP in attributeEps) {
    try {
      val data = ResourceUtil.getResourceAsBytes(attributesEP.file.removePrefix("/"), attributesEP.pluginDescriptor.getClassLoader())
      if (data == null) {
        LOG.warn("resource not found: " + attributesEP.file)
        continue
      }

      val root = JDOMUtil.load(data)
      scheme.readAttributes(root.getChild("attributes") ?: root)
      root.getChild("colors")?.let {
        scheme.readColors(it)
      }
    }
    catch (e: Exception) {
      LOG.error(e)
    }
  }
}

@VisibleForTesting
fun readEditorSchemeNameFromXml(parser: XMLStreamReader): String? {
  var eventType = parser.eventType
  do {
    if (eventType == XMLStreamConstants.START_ELEMENT) {
      return parser.getAttributeValue(null, "name")
    }
    eventType = parser.next()
  }
  while (eventType != XMLStreamConstants.END_DOCUMENT)
  return null
}

private val BUNDLED_EP_NAME = ExtensionPointName<BundledSchemeEP>("com.intellij.bundledColorScheme")

@VisibleForTesting
fun createLoadBundledSchemeRequests(
  additionalTextAttributes: MutableMap<String, MutableList<AdditionalTextAttributesEP>>,
  checkId: Boolean = false,
  nameResolver: ((String) -> EditorColorsScheme?)?
) : Sequence<SchemeManager.LoadBundleSchemeRequest<EditorColorsScheme>> {
  return sequence {
    for (item in BUNDLED_EP_NAME.filterableLazySequence()) {
      val pluginDescriptor = item.pluginDescriptor
      val bean = item.instance ?: continue
      val resourcePath = (bean.path ?: continue).removePrefix("/").let { if (it.endsWith(".xml")) it else "$it.xml" }

      yield(object : SchemeManager.LoadBundleSchemeRequest<EditorColorsScheme> {
        override val pluginId: PluginId = pluginDescriptor.pluginId

        override val schemeKey: String
          get() {
            val idFromExtension = item.id
            if (idFromExtension == null) {
              LOG.warn("id is not specified for extension ${bean.path}")
            }
            else if (!checkId) {
              return idFromExtension
            }

            val reader = createXmlStreamReader(loadBytes())
            try {
              val idFromFile = readEditorSchemeNameFromXml(reader)!!
              if (checkId && idFromFile != idFromExtension) {
                LOG.warn("id specified for extension $item is not equal to id from file $resourcePath")
              }
              return idFromFile
            }
            finally {
              reader.close()
            }
          }

        override fun loadBytes(): ByteArray =
          ResourceUtil.getResourceAsBytes(resourcePath, pluginDescriptor.classLoader)!!

        override fun createScheme(): EditorColorsScheme =
          createBundledEditorColorScheme(resourcePath, additionalTextAttributes, loadBytes(), pluginId, nameResolver)
      })
    }

    for (item in UiThemeProviderListManager.getInstance().getDescriptors()) {
      val pluginDescriptor = item.pluginDescriptor
      if (pluginDescriptor.pluginId == PluginManagerCore.CORE_ID) {
        continue
      }

      val uiTheme = item.theme.get() ?: continue
      val editorSchemeId = uiTheme.theme.originalEditorSchemeId ?: continue
      // we must check `originalEditorSchemeId` to load its corresponding editor scheme on the `reloadKeepingActiveScheme` call
      if (!editorSchemeId.endsWith(".xml")) {
        continue
      }

      val resourcePath = editorSchemeId.removePrefix("/")
      val data = ResourceUtil.getResourceAsBytes(resourcePath, pluginDescriptor.classLoader)
                 // https://youtrack.jetbrains.com/issue/IDEA-341932
                 ?: ResourceUtil.getResourceAsBytes("theme/$resourcePath", pluginDescriptor.classLoader)
      if (data == null) {
        LOG.error(PluginException("Cannot find $resourcePath", pluginDescriptor.pluginId))
        continue
      }

      val reader = createXmlStreamReader(data)
      val colorSchemeId = try {
        readEditorSchemeNameFromXml(reader)!!
      }
      finally {
        reader.close()
      }

      // update ID to make sure that other consumers use a correct one
      uiTheme.theme.editorSchemeId = colorSchemeId

      yield(object : SchemeManager.LoadBundleSchemeRequest<EditorColorsScheme> {
        override val pluginId: PluginId = pluginDescriptor.pluginId
        override val schemeKey: String = colorSchemeId
        override fun loadBytes(): ByteArray = data
        override fun createScheme(): EditorColorsScheme =
          createBundledEditorColorScheme(resourcePath, additionalTextAttributes, loadBytes(), pluginId, nameResolver)
      })
    }
  }
}

private fun createBundledEditorColorScheme(
  resourcePath: String,
  additionalTextAttributes: MutableMap<String, MutableList<AdditionalTextAttributesEP>>,
  data: ByteArray,
  pluginId: PluginId,
  nameResolver: ((String) -> EditorColorsScheme?)?
): BundledEditorColorScheme {
  val scheme = BundledEditorColorScheme(resourcePath)
  // todo be lazy
  scheme.readExternal(JDOMUtil.load(data))
  if (nameResolver != null)
    scheme.resolveParent(nameResolver)
  // We don't need to update digest for a bundled scheme because:
  // 1) it can be computed on demand later (because a bundled scheme is not mutable)
  // 2) in the future, user copies of bundled schemes will use a bundled scheme as parent (not as full copy)
  val parentScheme = scheme.getParentScheme()
  if (parentScheme is AbstractColorsScheme) {
    val attributesEPs = additionalTextAttributes.remove(parentScheme.name)
    if (!attributesEPs.isNullOrEmpty()) {
      loadAdditionalTextAttributesForScheme(parentScheme, attributesEPs)
    }
  }

  scheme.optimizeAttributeMap()
  scheme.metaProperties.setProperty(AbstractColorsScheme.META_INFO_PLUGIN_ID, pluginId.idString)
  return scheme
}

private class BundledEditorColorScheme(@JvmField val resourcePath: String) : EditorColorsSchemeImpl(/* parentScheme = */ null) {
  override fun isVisible() = false
  override fun isReadOnly() = true
  override fun getSchemeState() = SchemeState.UNCHANGED
  override fun isFromIntellij(): Boolean = (metaProperties["pluginId"] as? String)?.startsWith("com.intellij") == true
}
