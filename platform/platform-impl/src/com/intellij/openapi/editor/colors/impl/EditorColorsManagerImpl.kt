// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PropertyName")

package com.intellij.openapi.editor.colors.impl

import com.intellij.configurationStore.BundledSchemeEP
import com.intellij.configurationStore.LazySchemeProcessor
import com.intellij.configurationStore.SchemeDataHolder
import com.intellij.configurationStore.SchemeExtensionProvider
import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.UITheme
import com.intellij.ide.ui.laf.TempUIThemeLookAndFeelInfo
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfoImpl
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
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.serviceContainer.NonInjectable
import com.intellij.ui.ColorUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.util.ComponentTreeEventDispatcher
import com.intellij.util.ResourceUtil
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.xmlb.annotations.OptionTag
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

private val LOG: Logger
  get() = logger<EditorColorsManagerImpl>()

private const val TEMP_SCHEME_KEY: @NonNls String = "TEMP_SCHEME_KEY"
private const val TEMP_SCHEME_FILE_KEY: @NonNls String = "TEMP_SCHEME_FILE_KEY"

@State(name = EditorColorsManagerImpl.COMPONENT_NAME, storages = [Storage(EditorColorsManagerImpl.STORAGE_NAME)],
       additionalExportDirectory = EditorColorsManagerImpl.FILE_SPEC, category = SettingsCategory.UI)
@ApiStatus.Internal
class EditorColorsManagerImpl @NonInjectable constructor(schemeManagerFactory: SchemeManagerFactory)
  : EditorColorsManager(), PersistentStateComponent<EditorColorsManagerImpl.State?> {
  private val treeDispatcher = ComponentTreeEventDispatcher.create(EditorColorsListener::class.java)

  val schemeManager: SchemeManager<EditorColorsScheme>
  private var state = State()
  private var themeIsCustomized = false
  private var isInitialConfigurationLoaded = false

  constructor() : this(SchemeManagerFactory.getInstance())

  init {
    val additionalTextAttributes = collectAdditionalTextAttributesEPs()
    schemeManager = schemeManagerFactory.create(directoryName = FILE_SPEC,
                                                processor = EditorColorSchemeProcessor(additionalTextAttributes),
                                                presentableName = null,
                                                directoryPath = null,
                                                settingsCategory = SettingsCategory.UI)
    initDefaultSchemes()
    loadBundledSchemes()
    loadSchemesFromThemes()
    schemeManager.loadSchemes()
    loadRemainAdditionalTextAttributes(additionalTextAttributes)

    initEditableDefaultSchemesCopies()
    initEditableBundledSchemesCopies()
    resolveLinksToBundledSchemes()

    ApplicationManager.getApplication().getMessageBus()
      .simpleConnect().subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
        override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
          reloadKeepingActiveScheme()
        }

        override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
          reloadKeepingActiveScheme()
        }
      })
  }

  companion object {
    val ADDITIONAL_TEXT_ATTRIBUTES_EP_NAME: ExtensionPointName<AdditionalTextAttributesEP> = ExtensionPointName(
      "com.intellij.additionalTextAttributes")

    const val COMPONENT_NAME: String = "EditorColorsManagerImpl"
    const val STORAGE_NAME: String = "colors.scheme.xml"

    @JvmField
    val BUNDLED_EP_NAME: ExtensionPointName<BundledSchemeEP> = ExtensionPointName("com.intellij.bundledColorScheme")

    const val FILE_SPEC: String = "colors"

    fun isTempScheme(scheme: EditorColorsScheme?): Boolean {
      return (scheme ?: return false).getMetaProperties().getProperty(TEMP_SCHEME_KEY).toBoolean()
    }

    fun getTempSchemeOriginalFilePath(scheme: EditorColorsScheme): Path? {
      if (isTempScheme(scheme)) {
        val path = scheme.getMetaProperties().getProperty(TEMP_SCHEME_FILE_KEY)
        if (path != null) {
          return Path.of(path)
        }
      }
      return null
    }

    fun setTempScheme(scheme: EditorColorsScheme?, originalSchemeFile: VirtualFile?) {
      if (scheme == null) {
        return
      }

      scheme.getMetaProperties().setProperty(TEMP_SCHEME_KEY, true.toString())
      if (originalSchemeFile != null) {
        scheme.getMetaProperties().setProperty(TEMP_SCHEME_FILE_KEY, originalSchemeFile.getPath())
      }
    }
  }

  override fun reloadKeepingActiveScheme() {
    val activeScheme = schemeManager.currentSchemeName
    schemeManager.reload()

    if (!activeScheme.isNullOrEmpty()) {
      val scheme = getScheme(activeScheme)
      if (scheme != null) {
        globalScheme = scheme
      }
    }
  }

  private fun initDefaultSchemes() {
    for (defaultScheme in DefaultColorSchemesManager.getInstance().allSchemes) {
      schemeManager.addScheme(defaultScheme)
    }
  }

  // initScheme has to execute only after the LaF has been set in LafManagerImpl.initializeComponent
  private fun initEditableDefaultSchemesCopies() {
    for (defaultScheme in DefaultColorSchemesManager.getInstance().allSchemes) {
      if (defaultScheme.hasEditableCopy()) {
        createEditableCopy(initialScheme = defaultScheme, editableCopyName = defaultScheme.editableCopyName)
      }
    }
  }

  @TestOnly
  fun removeScheme(scheme: EditorColorsScheme) {
    assert(ApplicationManager.getApplication().isUnitTestMode()) { "Test-only method" }
    schemeManager.removeScheme(scheme)
  }

  private fun loadBundledSchemes() {
    if (!isUnitTestOrHeadlessMode) {
      BUNDLED_EP_NAME.processWithPluginDescriptor { ep, pluginDescriptor ->
        loadBundledScheme(resourceName = "${ep.path}.xml", requestor = null, descriptor = pluginDescriptor)
      }
    }
  }

  @TestOnly
  fun loadBundledScheme(themeName: String): EditorColorsScheme? {
    assert(ApplicationManager.getApplication().isUnitTestMode()) { "Test-only method" }
    val theme = UiThemeProviderListManager.getInstance().findThemeByName(themeName) as UIThemeLookAndFeelInfoImpl?
    val schemePath = theme?.theme?.editorSchemePath ?: return null
    val bundledScheme = loadBundledScheme(resourceName = schemePath, requestor = theme, descriptor = null)
    initEditableBundledSchemesCopies()
    return bundledScheme
  }

  @TestOnly
  fun loadBundledScheme(resourcePath: String, descriptor: PluginDescriptor): EditorColorsScheme? {
    assert(ApplicationManager.getApplication().isUnitTestMode()) { "Test-only method" }
    val bundledScheme = loadBundledScheme(resourceName = resourcePath, requestor = null, descriptor = descriptor)
    initEditableBundledSchemesCopies()
    return bundledScheme
  }

  private fun loadBundledScheme(resourceName: String,
                                requestor: Any?,
                                descriptor: PluginDescriptor?): EditorColorsScheme? {
    val scheme = schemeManager.loadBundledScheme(resourceName = resourceName,
                                                 requestor = requestor,
                                                 pluginDescriptor = descriptor) as BundledScheme?
    if (scheme != null && descriptor != null) {
      scheme.metaProperties.setProperty(AbstractColorsScheme.META_INFO_PLUGIN_ID, descriptor.getPluginId().idString)
    }
    return scheme
  }

  private fun loadSchemesFromThemes() {
    if (isUnitTestOrHeadlessMode) {
      return
    }

    for (laf in UiThemeProviderListManager.getInstance().getLaFs()) {
      val theme = (laf as UIThemeLookAndFeelInfoImpl).theme
      val pluginDescriptor = (laf.providerClassLoader as? PluginClassLoader)?.pluginDescriptor
      for (scheme in theme.additionalEditorSchemes) {
        loadBundledScheme(resourceName = scheme, requestor = theme, descriptor = pluginDescriptor)
      }

      theme.editorSchemePath?.let {
        loadBundledScheme(resourceName = it, requestor = theme, descriptor = pluginDescriptor)
      }
    }
  }

  private fun initEditableBundledSchemesCopies() {
    // process over allSchemes snapshot
    for (scheme in schemeManager.allSchemes.toList()) {
      if (scheme is BundledScheme) {
        createEditableCopy(initialScheme = scheme, editableCopyName = Scheme.EDITABLE_COPY_PREFIX + scheme.getName())
      }
    }
  }

  fun handleThemeAdded(theme: UITheme) {
    val editorScheme = theme.editorSchemePath
    if (editorScheme != null) {
      loadBundledScheme(resourceName = editorScheme, requestor = theme, descriptor = null)
      initEditableBundledSchemesCopies()
    }
  }

  private fun resolveLinksToBundledSchemes() {
    val brokenSchemesList: MutableList<EditorColorsScheme> = ArrayList()
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
      if (brokenScheme is AbstractColorsScheme && brokenScheme !is ReadOnlyColorsScheme) {
        brokenScheme.isVisible = false
      }
      else {
        schemeManager.removeScheme(brokenScheme)
      }
    }
  }

  override fun resolveSchemeParent(scheme: EditorColorsScheme) {
    if (scheme is AbstractColorsScheme && scheme !is ReadOnlyColorsScheme) {
      scheme.resolveParent { name -> schemeManager.findSchemeByName(name) }
    }
  }

  private fun createEditableCopy(initialScheme: AbstractColorsScheme, editableCopyName: String) {
    var editableCopy = getScheme(editableCopyName) as AbstractColorsScheme?
    if (editableCopy == null) {
      editableCopy = initialScheme.clone() as AbstractColorsScheme
      editableCopy.name = editableCopyName
      schemeManager.addScheme(editableCopy)
    }
    else if (initialScheme is BundledScheme) {
      editableCopy.copyMissingAttributes(initialScheme)
    }
    editableCopy.setCanBeDeleted(false)
  }

  fun handleThemeRemoved(theme: UITheme) {
    val editorSchemeName = theme.editorSchemeName
    if (editorSchemeName != null) {
      val scheme = schemeManager.findSchemeByName(editorSchemeName)
      if (scheme != null) {
        schemeManager.removeScheme(scheme)
        val editableCopyName = getEditableCopyName(scheme)
        if (editableCopyName != null) {
          schemeManager.removeScheme(editableCopyName)
        }
      }
    }
  }

  fun schemeChangedOrSwitched(newScheme: EditorColorsScheme?) {
    // refreshAllEditors is not enough - for example, change "Errors and warnings -> Typo" from green (default) to red
    for (project in ProjectManager.getInstance().getOpenProjects()) {
      PsiManager.getInstance(project).dropPsiCaches()
    }

    // we need to push events to components that use editor font, e.g., HTML editor panes
    ApplicationManager.getApplication().getMessageBus().syncPublisher(TOPIC).globalSchemeChange(newScheme)
    treeDispatcher.multicaster.globalSchemeChange(newScheme)
  }

  override fun getSchemeForCurrentUITheme(): EditorColorsScheme {
    val lookAndFeelInfo = LafManager.getInstance().getCurrentUIThemeLookAndFeel()
    var scheme: EditorColorsScheme? = null
    if (lookAndFeelInfo is TempUIThemeLookAndFeelInfo) {
      val globalScheme = getInstance().getGlobalScheme()
      if (isTempScheme(globalScheme)) {
        return globalScheme
      }
    }
    if (lookAndFeelInfo != null) {
      val schemeName = lookAndFeelInfo.editorSchemeName
      if (schemeName != null) {
        scheme = getScheme(schemeName)
        assert(scheme != null) { "Theme " + lookAndFeelInfo.name + " refers to unknown color scheme " + schemeName }
      }
    }
    if (scheme == null) {
      @Suppress("DEPRECATION") val schemeName = if (StartupUiUtil.isUnderDarcula) "Darcula" else DEFAULT_SCHEME_NAME
      val colorSchemeManager = DefaultColorSchemesManager.getInstance()
      scheme = colorSchemeManager.getScheme(schemeName)
      assert(scheme != null) {
        "The default scheme '" + schemeName + "' not found, " +
        "available schemes: " + colorSchemeManager.listNames()
      }
    }
    val editableCopy = getEditableCopy(scheme)
    return editableCopy ?: scheme!!
  }

  private fun loadRemainAdditionalTextAttributes(additionalTextAttributes: MutableMap<String, MutableList<AdditionalTextAttributesEP>>) {
    for ((schemeName, value) in additionalTextAttributes) {
      val editorColorsScheme = schemeManager.findSchemeByName(schemeName)
      if (editorColorsScheme !is AbstractColorsScheme) {
        if (!isUnitTestOrHeadlessMode) {
          LOG.warn("Cannot find scheme: $schemeName from plugins: " +
                   value.joinToString(separator = ";") { it.pluginDescriptor.getPluginId().idString })
        }
        continue
      }

      loadAdditionalTextAttributesForScheme(scheme = editorColorsScheme, attributeEps = value)
    }
    additionalTextAttributes.clear()
  }

  internal class BundledScheme : EditorColorsSchemeImpl(null), ReadOnlyColorsScheme {
    override fun isVisible() = false
  }

  class State {
    @JvmField
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
    return getScheme(if (dark) "Darcula" else EditorColorsScheme.DEFAULT_SCHEME_NAME)?.getAttributes(key)
  }

  override fun addColorScheme(scheme: EditorColorsScheme) {
    if (!isDefaultScheme(scheme) && !scheme.getName().isEmpty()) {
      schemeManager.addScheme(scheme)
    }
  }

  override fun getAllSchemes(): Array<EditorColorsScheme> {
    return schemeManager.allSchemes.asSequence()
      .filter { AbstractColorsScheme.isVisible(it) }
      .sortedWith(EditorColorSchemesComparator.INSTANCE)
      .toList()
      .toTypedArray()
  }

  fun setGlobalScheme(scheme: EditorColorsScheme?, processChangeSynchronously: Boolean) {
    val notify = LoadingState.COMPONENTS_LOADED.isOccurred
    schemeManager.setCurrent(scheme = scheme ?: getDefaultScheme(),
                             notify = notify,
                             processChangeSynchronously = processChangeSynchronously)
  }

  override fun setGlobalScheme(scheme: EditorColorsScheme?) {
    setGlobalScheme(scheme = scheme, processChangeSynchronously = false)
  }

  override fun getGlobalScheme(): EditorColorsScheme {
    val scheme = schemeManager.activeScheme
    if (scheme is AbstractColorsScheme &&
        scheme !is ReadOnlyColorsScheme &&
        !scheme.isVisible) {
      return getDefaultScheme()
    }

    val editableCopy = getEditableCopy(scheme)
    if (editableCopy != null) {
      return editableCopy
    }
    return scheme ?: getDefaultScheme()
  }

  private fun getDefaultScheme(): EditorColorsScheme {
    val defaultScheme = DefaultColorSchemesManager.getInstance().firstScheme
    val editableCopyName = defaultScheme.editableCopyName
    val editableCopy = getScheme(editableCopyName)
    if (editableCopy == null) {
      LOG.error("An editable copy of ${defaultScheme.name} has not been initialized.")
      return defaultScheme
    }
    return editableCopy
  }

  override fun getScheme(schemeName: @NonNls String): EditorColorsScheme? {
    return schemeManager.findSchemeByName(schemeName)
  }

  private fun getEditableCopy(scheme: EditorColorsScheme?): EditorColorsScheme? {
    if (isTempScheme(scheme)) {
      return scheme
    }

    val editableCopyName = getEditableCopyName(scheme)
    if (editableCopyName != null) {
      val editableCopy = getScheme(editableCopyName)
      if (editableCopy != null) {
        return editableCopy
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

  override fun isUseOnlyMonospacedFonts(): Boolean {
    return state.USE_ONLY_MONOSPACED_FONTS
  }

  override fun setUseOnlyMonospacedFonts(value: Boolean) {
    state.USE_ONLY_MONOSPACED_FONTS = value
  }

  override fun loadState(state: State) {
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

    colorScheme?.let {
      notifyAboutSolarizedColorSchemeDeprecationIfSet(it)
    }

    val activity = StartUpMeasurer.startActivity("editor color scheme initialization")
    val laf = if (ApplicationManager.getApplication().isUnitTestMode()) null else LafManager.getInstance().getCurrentUIThemeLookAndFeel()
    // null in a headless mode
    if (laf != null) {
      if (!themeIsCustomized) {
        var scheme1: EditorColorsScheme? = null
        val schemeName1 = laf.editorSchemeName
        if (schemeName1 != null) {
          scheme1 = getScheme(schemeName1)
        }
        if (scheme1 != null) {
          schemeManager.setCurrent(scheme = scheme1, notify = false)
        }
      }
    }
    activity.end()
  }

  override fun noStateLoaded() {
    themeIsCustomized = false

    val activity = StartUpMeasurer.startActivity("editor color scheme initialization")
    val laf = if (ApplicationManager.getApplication().isUnitTestMode()) null else LafManager.getInstance().getCurrentUIThemeLookAndFeel()

    var editorSchemeName = laf?.editorSchemeName
    if (editorSchemeName == null && laf != null && laf.isDark) {
      editorSchemeName = "Darcula"
    }

    val scheme = editorSchemeName?.let { getScheme(it) } ?: getDefaultScheme()
    schemeManager.setCurrent(scheme = scheme, notify = isInitialConfigurationLoaded)
    isInitialConfigurationLoaded = true
    activity.end()
  }

  override fun isDefaultScheme(scheme: EditorColorsScheme): Boolean = scheme is DefaultColorsScheme

  private inner class EditorColorSchemeProcessor(
    private val additionalTextAttributes: MutableMap<String, MutableList<AdditionalTextAttributesEP>>,
  ) :
    LazySchemeProcessor<EditorColorsScheme, EditorColorsSchemeImpl>(), SchemeExtensionProvider {
    override fun createScheme(dataHolder: SchemeDataHolder<EditorColorsSchemeImpl>,
                              name: String,
                              attributeProvider: (String) -> String?,
                              isBundled: Boolean): EditorColorsSchemeImpl {
      val scheme = if (isBundled) BundledScheme() else EditorColorsSchemeImpl(null)
      // todo be lazy
      scheme.readExternal(dataHolder.read())
      // we don't need to update digest for a bundled scheme because
      // 1) it can be computed on demand later (because a bundled scheme is not mutable)
      // 2) in the future user copy of a bundled scheme will use a bundled scheme as parent (not as full copy)
      if (isBundled ||
          (ApplicationManager.getApplication().isUnitTestMode() && scheme.metaProperties.getProperty("forceOptimize").toBoolean())) {
        if (scheme.myParentScheme is AbstractColorsScheme) {
          val attributesEPs = additionalTextAttributes.remove(scheme.myParentScheme.getName())
          if (!attributesEPs.isNullOrEmpty()) {
            loadAdditionalTextAttributesForScheme(scheme = scheme.myParentScheme as AbstractColorsScheme, attributeEps = attributesEPs)
          }
        }

        scheme.optimizeAttributeMap()
      }
      return scheme
    }

    override fun getState(scheme: EditorColorsScheme): SchemeState {
      return if (scheme is ReadOnlyColorsScheme) SchemeState.NON_PERSISTENT else SchemeState.POSSIBLY_CHANGED
    }

    override fun onCurrentSchemeSwitched(oldScheme: EditorColorsScheme?,
                                         newScheme: EditorColorsScheme?,
                                         processChangeSynchronously: Boolean) {
      if (processChangeSynchronously) {
        handleCurrentSchemeSwitched(newScheme)
      }
      else {
        // don't do heavy operations right away
        ApplicationManager.getApplication().invokeLater {
          handleCurrentSchemeSwitched(newScheme)
        }
      }
    }

    private fun handleCurrentSchemeSwitched(newScheme: EditorColorsScheme?) {
      LafManager.getInstance().updateUI()
      schemeChangedOrSwitched(newScheme)
    }

    override val schemeExtension: @NonNls String
      get() = COLOR_SCHEME_FILE_EXTENSION

    override fun isSchemeEqualToBundled(scheme: EditorColorsSchemeImpl): Boolean {
      if (!scheme.getName().startsWith(Scheme.EDITABLE_COPY_PREFIX)) {
        return false
      }

      val bundledScheme =
        (schemeManager.findSchemeByName(scheme.getName().substring(Scheme.EDITABLE_COPY_PREFIX.length)) as AbstractColorsScheme?)
        ?: return false
      return scheme.settingsEqual(bundledScheme)
    }

    override fun reloaded(schemeManager: SchemeManager<EditorColorsScheme>, schemes: Collection<EditorColorsScheme>) {
      loadBundledSchemes()
      loadSchemesFromThemes()
      initEditableDefaultSchemesCopies()
      initEditableBundledSchemesCopies()
      ApplicationManager.getApplication().getMessageBus().syncPublisher(EditorColorsManagerListener.TOPIC).schemesReloaded()
    }
  }
}

private val isUnitTestOrHeadlessMode: Boolean
  get() = ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isHeadlessEnvironment()

private fun collectAdditionalTextAttributesEPs(): MutableMap<String, MutableList<AdditionalTextAttributesEP>> {
  val result = HashMap<String, MutableList<AdditionalTextAttributesEP>>()
  EditorColorsManagerImpl.ADDITIONAL_TEXT_ATTRIBUTES_EP_NAME.forEachExtensionSafe {
    result.computeIfAbsent(it.scheme) { ArrayList() }.add(it)
  }
  return result
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

private fun getEditableCopyName(scheme: EditorColorsScheme?): String? {
  return when {
    scheme is DefaultColorsScheme && scheme.hasEditableCopy() -> scheme.editableCopyName
    scheme is EditorColorsManagerImpl.BundledScheme -> Scheme.EDITABLE_COPY_PREFIX + scheme.getName()
    else -> null
  }
}

private fun notifyAboutSolarizedColorSchemeDeprecationIfSet(scheme: EditorColorsScheme) {
  val solarizedColorSchemeNames = setOf("Solarized (dark)",
                                        "Solarized (light)",
                                        "Solarized Dark",
                                        "Solarized Light",
                                        "Solarized Dark (Darcula)")

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
          val notification = Notification("ColorSchemeDeprecation",
                                          IdeBundle.message(
                                            "notification.title.solarized.color.scheme.deprecation"),
                                          "",
                                          NotificationType.ERROR)
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
                  var matchingTheme: UIThemeLookAndFeelInfo? = null
                  var otherWasSet: Boolean = false

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

                      val lafManager = LafManager.getInstance()
                      if (lafManager.getCurrentUIThemeLookAndFeel() != matchingTheme) {
                        lafManager.setCurrentLookAndFeel(matchingTheme!!, false)
                        lafManager.updateUI()
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
