// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.jb

import com.intellij.configurationStore.*
import com.intellij.configurationStore.schemeManager.SchemeManagerFactoryBase
import com.intellij.diagnostic.VMOptions
import com.intellij.ide.fileTemplates.FileTemplatesScheme
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginInstaller
import com.intellij.ide.plugins.RepositoryHelper
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.data.SettingsService
import com.intellij.ide.startup.importSettings.statistics.ImportSettingsEventsCollector
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.laf.LafManagerImpl
import com.intellij.openapi.application.*
import com.intellij.openapi.components.*
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.impl.KeymapManagerImpl
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.psi.codeStyle.CodeStyleSchemes
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.serviceContainer.getComponentManagerImpl
import com.intellij.ui.ExperimentalUI
import com.intellij.util.application
import com.intellij.util.io.copy
import kotlinx.coroutines.CoroutineScope
import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.*

class JbSettingsImporter(private val configDirPath: Path,
                         private val pluginsPath: Path,
                         private val prevIdeHome: Path?
) {
  private val componentStore = ApplicationManager.getApplication().stateStore as ComponentStoreImpl
  private val defaultNewUIValue = true
  private val additionalSchemeDirs = mapOf(FileTemplatesScheme.TEMPLATES_DIR to SettingsCategory.CODE)

  // will be used as toposort for dependencies
  // TODO: move to the component declaration instead
  private val componentNamesDependencies = mapOf(
    //IDEA-342818
    "SshLocalRecentConnectionsManager" to listOf("SshConfigs"),
    "SshHostStorage" to listOf("SshConfigs"),
    //IDEA-324914
    "EditorColorsManagerImpl" to listOf("LafManager")
  )

  // these are options that need to be reloaded after restart
  // for instance, LaFManager, because the actual theme might be provided by a plugin.
  // Same applies to the Keymap manager.
  // So far, it doesn't look like there's a viable way to detect those, so we just hardcode them.
  suspend fun importOptionsAfterRestart(categories: Set<SettingsCategory>, pluginIds: Set<String>) {
    val storageManager = componentStore.storageManager
    val (components, files) = findComponentsAndFiles()
    withExternalStreamProvider(arrayOf(storageManager)) {
      val componentManagerImpl = ApplicationManager.getApplication().getComponentManagerImpl()
      val availableComponents = loadNotLoadedComponents(EmptyProgressIndicator(), componentManagerImpl, components, pluginIds)
      componentStore.reloadComponents(files, emptyList(), availableComponents)
      if (categories.contains(SettingsCategory.KEYMAP)) {
        // ensure component is loaded
        KeymapManager.getInstance()
        componentStore.reloadState(KeymapManagerImpl::class.java)
      }
      if (categories.contains(SettingsCategory.UI) && !ExperimentalUI.wasThemeReset) {
        // ensure component is loaded
        LafManager.getInstance()
        EditorColorsManager.getInstance()
        componentStore.reloadState(LafManagerImpl::class.java)
        componentStore.reloadState(EditorColorsManagerImpl::class.java)
      }
    }
  }

  private fun loadProjectDefaultComponentNames() : Set<String> {
    val projectDefaultXmlPath = configDirPath / PathManager.OPTIONS_DIRECTORY / PROJECT_DEFAULT_FILE_NAME
    if (!projectDefaultXmlPath.isRegularFile())
      return emptySet()

    val parentElement = JDOMUtil.load(projectDefaultXmlPath)
    val defaultProjectElement = parentElement.getChild("component")?.getChild("defaultProject") ?: return emptySet()

    val retval = mutableSetOf<String>()
    for (componentElement in defaultProjectElement.getChildren("component")) {
      val componentName = componentElement.getAttributeValue("name")
      retval.add(componentName)
    }
    return retval
  }

  private fun findComponentsAndFiles(): Pair<Set<String>, Set<String>> {
    val optionsPath = configDirPath / PathManager.OPTIONS_DIRECTORY
    val allFiles = mutableSetOf<String>()
    val components = mutableSetOf<String>()
    for (optionsEntry in optionsPath.listDirectoryEntries()) {
      if (optionsEntry.name == PROJECT_DEFAULT_FILE_NAME)
        continue
      if (optionsEntry.name.lowercase().endsWith(".xml")) {
        allFiles.add(optionsEntry.name)
        val element = JDOMUtil.load(optionsEntry)
        val children = element.getChildren("component")
        allFiles.add(optionsEntry.name)
        for (componentElement in children) {
          val componentName = componentElement.getAttributeValue("name")
          components.add(componentName)
        }
      }
      else if (optionsEntry.isDirectory() && optionsEntry.name.lowercase() == getPerOsSettingsStorageFolderName()) {
        // i.e. mac/keymap.xml
        allFiles.addAll(filesFromFolder(optionsEntry, optionsEntry.name))
      }
    }
    return Pair(components, allFiles)
  }

  /**
   * Imports options from XML files and applies them to the application.
   *
   * @param categories The set of settings categories to import.
   * @return <strong>true</strong> if restart is required,
   * `false` otherwise.
   */
  suspend fun importOptions(progressIndicator: ProgressIndicator, categories: Set<SettingsCategory>): Boolean {
    // load all components
    // import all, except schema managers
    progressIndicator.checkCanceled()
    val allFiles = mutableSetOf<String>()
    val notLoadedComponents = arrayListOf<String>()
    val unknownStorage = arrayListOf<String>()
    val storageManager = componentStore.storageManager as StateStorageManagerImpl
    val cachedFileStorages = storageManager.getCachedFileStorages()
      .mapNotNull { (it as? FileBasedStorage)?.file?.name }
    val (components, files) = findComponentsAndFiles()
    notLoadedComponents.addAll(components)
    notLoadedComponents.removeAll(componentStore.getComponentNames())
    allFiles.addAll(files)
    unknownStorage.addAll(files)
    unknownStorage.removeAll(cachedFileStorages.toSet())


    //TODO: remove later, now keep for logging purposes
    LOG.info("NOT loaded components(${notLoadedComponents.size}):\n${notLoadedComponents.joinToString()}")
    LOG.info("NOT loaded storages(${unknownStorage.size}):\n${unknownStorage.joinToString()}")
    progressIndicator.checkCanceled()
    val componentManagerImpl = ApplicationManager.getApplication().getComponentManagerImpl()
    val defaultProject = ProjectManager.getInstance().defaultProject
    val defaultProjectStore = (defaultProject as ComponentManager).stateStore as ComponentStoreImpl
    val defaultProjectStorage = defaultProjectStore.storageManager.getStateStorage(FileStorageAnnotation("", false))

    val loadNotLoadedComponents = loadNotLoadedComponents(progressIndicator, componentManagerImpl, notLoadedComponents, null)
    notLoadedComponents.removeAll(loadNotLoadedComponents)

    val projectDefaultComponentNames = loadProjectDefaultComponentNames()
    if (projectDefaultComponentNames.isNotEmpty()) {
      loadNotLoadedComponents(progressIndicator, defaultProject.getComponentManagerImpl(),
                              projectDefaultComponentNames, null)
    }

    for (component in notLoadedComponents) {
      LOG.info("Component $component was not found and loaded. Its settings will not be migrated")
    }

    // load code style scheme manager
    CodeStyleSchemes.getInstance()
    InspectionProfileManager.getInstance()
    val schemeManagerFactory = SchemeManagerFactory.getInstance() as SchemeManagerFactoryBase
    schemeManagerFactory.process {
      progressIndicator.checkCanceled()
      if ((configDirPath / it.fileSpec).isDirectory()) {
        allFiles.addAll(filesFromFolder(configDirPath / it.fileSpec, it.fileSpec))
      }
    }
    for (entry in additionalSchemeDirs) {
      if ((configDirPath / entry.key).isDirectory()) {
        allFiles.addAll(filesFromFolder(configDirPath / entry.key, entry.key))
      }
    }

    LOG.info("Detected ${allFiles.size} files that could be imported: ${allFiles.joinToString()}")
    val componentAndFilesMap = filterComponents(allFiles, categories)
    val componentFiles = componentAndFilesMap.values.toSet()
    LOG.info("After filtering we have ${componentFiles.size} component files to import: ${componentFiles.joinToString()}")
    val schemeFiles = filterSchemes(allFiles, categories)
    LOG.info("After filtering we have ${schemeFiles.size} scheme files to import: ${schemeFiles.joinToString()}")

    // setting dummy valueChangeListener, so effects won't affect the UI, etc.
    Registry.setValueChangeListener(object : RegistryValueListener {
      // do nothing
    })
    // copy scheme files first:
    schemeFiles.forEach {
      (configDirPath / it).copy(PathManager.getConfigDir() / it)
    }

    // we use LinkedHashSet, because we need ordering here
    val appComponentNames: LinkedHashSet<String> = toposortComponentNames(componentAndFilesMap.keys)

    withExternalStreamProvider(arrayOf(storageManager, defaultProjectStore.storageManager)) {
      progressIndicator.checkCanceled()
      application.runReadAction {
        componentStore.reloadComponents(changedFileSpecs = componentFiles + schemeFiles,
                                        deletedFileSpecs = emptyList(),
                                        componentNames2reload = appComponentNames,
                                        forceReloadNonReloadable = true)
      }
      progressIndicator.checkCanceled()
      application.runReadAction {
        defaultProjectStore.reinitComponents(projectDefaultComponentNames, setOf(defaultProjectStorage), emptySet())
      }
    }
    progressIndicator.checkCanceled()
    JbImportSpecialHandler.postProcess(configDirPath)
    RegistryManager.getInstanceAsync().resetValueChangeListener()

    // there's currently only one reason to restart after reading configs
    // plugins are handled separately
    return Registry.getInstance().isRestartNeeded
  }

  // very basic and primitive toposort. Doesn't traverse, doesn't support transitive deps, etc.
  private fun toposortComponentNames(components: Collection<String>): LinkedHashSet<String> {
    val retval = LinkedHashSet<String>()
    for (c in components) {
      for (d in componentNamesDependencies[c]?:emptyList()) {
        if (!retval.contains(d)){
          retval.add(d)
        }
      }
      retval.add(c)
    }
    return retval
  }

  private suspend fun withExternalStreamProvider(storageManagers: Array<StateStorageManager>, action: () -> Unit) {
    val provider = ImportStreamProvider(configDirPath)
    for (storageManager in storageManagers) {
      storageManager.addStreamProvider(provider)
    }

    action()

    for (storageManager in storageManagers) {
      storageManager.removeStreamProvider(provider::class.java)
    }
    saveSettings(ApplicationManager.getApplication(), true)
  }

  private fun loadNotLoadedComponents(
    progressIndicator: ProgressIndicator,
    componentManagerImpl: ComponentManagerImpl,
    componentsToLoad: Collection<String>,
    pluginIds: Set<String>?
  ): Set<String> {
    val start = System.currentTimeMillis()
    val notLoadedComponents = arrayListOf<String>()
    notLoadedComponents.addAll(componentsToLoad)
    val foundComponents = hashMapOf<String, Class<*>>()
    componentManagerImpl.processAllHolders { key, clazz, pluginDescriptor ->
      progressIndicator.checkCanceled()
      if (pluginDescriptor != null && pluginIds != null && !pluginIds.contains(pluginDescriptor.pluginId.idString))
        return@processAllHolders

      val stateAnnotation = getStateOrNull(clazz) ?: return@processAllHolders
      val componentName = stateAnnotation.name
      if (componentsToLoad.contains(componentName)) {
        val service: Any? = componentManagerImpl.getServiceByClassName(key)
        if (service != null) {
          notLoadedComponents.remove(componentName)
          foundComponents[componentName] = clazz
        }
        else {
          LOG.warn("Service $key is not found")
        }
      }
    }

    LOG.info("Loaded notFoundComponents in ${System.currentTimeMillis() - start} ms")
    return foundComponents.keys
  }

  internal fun isNewUIValueChanged(): Boolean {
    val earlyAccessRegistryPath = configDirPath / EarlyAccessRegistryManager.fileName
    if (!earlyAccessRegistryPath.exists()) {
      return false
    }
    val eaLinesIterator = earlyAccessRegistryPath.toFile().readLines().iterator()
    while (eaLinesIterator.hasNext()) {
      val key = eaLinesIterator.next()
      if (eaLinesIterator.hasNext()) {
        val value = eaLinesIterator.next()
        if (key == ExperimentalUI.KEY) {
          return value != defaultNewUIValue.toString()
        }
      }
    }
    return false
  }

  private fun filesFromFolder(dir: Path, prefix: String = dir.name): Collection<String> {
    val retval = ArrayList<String>()
    for (entry in dir.listDirectoryEntries()) {
      if (entry.isRegularFile()) {
        if (prefix.isEmpty()) {
          retval.add(entry.name)
        }
        else {
          retval.add("$prefix/${entry.name}")
        }
      }
      else {
        val folderFiles = filesFromFolder(entry, "$prefix/${entry.name}")
        retval.addAll(folderFiles)
      }
    }
    return retval
  }


  // key: PSC, value - file
  private fun filterComponents(allFiles: Set<String>, categories: Set<SettingsCategory>): Map<String, String> {
    val componentManager = ApplicationManager.getApplication() as ComponentManagerEx
    val retval = hashMapOf<String, String>()
    val osFolderName = getPerOsSettingsStorageFolderName()
    componentManager.processAllImplementationClasses { aClass, _ ->
      val state = getStateOrNull(aClass) ?: return@processAllImplementationClasses
      if (!categories.contains(state.category))
        return@processAllImplementationClasses

      val activeStorage = state.storages.find { !it.deprecated } ?: return@processAllImplementationClasses
      if (activeStorage.value == StoragePathMacros.CACHE_FILE ||
          (!activeStorage.roamingType.canBeMigrated() && !state.exportable && !activeStorage.exportable))
        return@processAllImplementationClasses

      if (activeStorage.roamingType.isOsSpecific && allFiles.contains("$osFolderName/${activeStorage.value}")) {
        retval[state.name] = "$osFolderName/${activeStorage.value}"
      }
      else if (allFiles.contains(activeStorage.value)) {
        retval[state.name] = activeStorage.value
      }
    }
    return retval
  }

  private fun getStateOrNull(aClass: Class<*>): State? {
    var clazz = aClass
    while (PersistentStateComponent::class.java.isAssignableFrom(clazz)) {
      val state = clazz.getAnnotation(State::class.java)
      if (state != null)
        return state
      clazz = clazz.superclass
    }
    return null
  }

  private fun filterSchemes(allFiles: Set<String>, categories: Set<SettingsCategory>): Set<String> {
    val retval = hashSetOf<String>()
    val schemeCategories = hashSetOf<String>()
    // fileSpec is e.g. keymaps/mykeymap.xml
    (SchemeManagerFactory.getInstance() as SchemeManagerFactoryBase).process {
      if (categories.contains(it.getSettingsCategory())) {
        schemeCategories.add(it.fileSpec)
      }
    }
    for (entry in additionalSchemeDirs) {
      if (categories.contains(entry.value)) {
        schemeCategories.add(entry.key)
      }
    }
    for (file in allFiles) {
      val split = file.split('/')
      if (split.size < 2)
        continue

      if (schemeCategories.contains(split[0])) {
        retval.add(file)
      }
    }
    return retval
  }

  suspend fun installPlugins(
    coroutineScope: CoroutineScope,
    progressIndicator: ProgressIndicator,
    pluginsMap: Map<PluginId, IdeaPluginDescriptor?>,
  ) {
    if (!SettingsService.getInstance().pluginIdsPreloaded) {
      LOG.warn("Couldn't preload plugin ids, which indicates problems with connection. Will use old import")
      val importOptions = configImportOptions(progressIndicator, pluginsMap.keys)
      ImportSettingsEventsCollector.jbPluginsOldImport()
      ConfigImportHelper.migratePlugins(
        pluginsPath,
        configDirPath,
        PathManager.getPluginsDir(),
        PathManager.getConfigDir(),
        importOptions
      ) { false }
      return
    }
    ImportSettingsEventsCollector.jbPluginsNewImport()
    RepositoryHelper.updatePluginHostsFromConfigDir(configDirPath, LOG)
    val updateableMap = HashMap(pluginsMap)
    progressIndicator.text2 = ImportSettingsBundle.message("progress.details.checking.for.plugin.updates")
    val internalPluginUpdates = UpdateChecker.getInternalPluginUpdates(
      buildNumber = null,
      indicator = progressIndicator,
      updateablePluginsMap = updateableMap
    )
    for (pluginDownloader in internalPluginUpdates.pluginUpdates.all) {
      LOG.info("Downloading ${pluginDownloader.id}")
      if (pluginDownloader.prepareToInstall(progressIndicator)) {
        PluginInstaller.unpackPlugin(pluginDownloader.filePath, PathManager.getPluginsDir())
        LOG.info("Downloaded and unpacked newer version of plugin '${pluginDownloader.id}' : ${pluginDownloader.pluginVersion}")
      }
      else {
        val descriptor = pluginsMap[pluginDownloader.id] ?: continue
        updateableMap[pluginDownloader.id] = descriptor
        // failed to download - should copy instead
        ImportSettingsEventsCollector.jbPluginImportConnectionError()
        LOG.info("Failed to download a newer version of '${pluginDownloader.id}' : ${pluginDownloader.pluginVersion}. " +
                 "Will try to copy old version (${descriptor.version}) instead")
      }
    }
    checkPluginsCompatibility(updateableMap, progressIndicator)
    progressIndicator.text2 = ImportSettingsBundle.message("progress.details.copying.plugins")
    ConfigImportHelper.migratePlugins(
      PathManager.getPluginsDir(),
      updateableMap.values.toList(),
      LOG
    )
  }

  private fun checkPluginsCompatibility(
    updateablePluginsMap: MutableMap<PluginId, IdeaPluginDescriptor?>,
    progressIndicator: ProgressIndicator
  ) {
    val myIdeData = IDEData.getSelf() ?: return
    progressIndicator.text2 = ImportSettingsBundle.message("progress.details.checking.plugins.compatibility")
    val updates = MarketplaceRequests.getNearestUpdate(updateablePluginsMap.keys)
    for (update in updates) {
      if (update.compatible)
        continue

      if (!update.products.contains(myIdeData.marketplaceCode)) {
        val pluginId = PluginId.findId(update.pluginId) ?: continue
        LOG.info("Plugins ${update.pluginId} is incompatible with ${myIdeData.fullName}. Will not migrate it")
        updateablePluginsMap.remove(pluginId)
      }
    }
  }

  private fun configImportOptions(progressIndicator: ProgressIndicator,
                                  pluginIds: Collection<PluginId>): ConfigImportHelper.ConfigImportOptions {
    val importOptions = ConfigImportHelper.ConfigImportOptions(LOG)
    importOptions.isHeadless = true
    importOptions.headlessProgressIndicator = progressIndicator
    importOptions.importSettings = object : ConfigImportSettings {
      override fun processPluginsToMigrate(
        newConfigDir: Path,
        oldConfigDir: Path,
        bundledPlugins: MutableList<IdeaPluginDescriptor>, // FIXME wrong arg name
        nonBundledPlugins: MutableList<IdeaPluginDescriptor>, // FIXME wrong arg name
      ) {
        nonBundledPlugins.removeIf { !pluginIds.contains(it.pluginId) }
        bundledPlugins.removeIf { !pluginIds.contains(it.pluginId) }
      }
    }
    return importOptions
  }

  fun importRaw() {
    val startTime = System.currentTimeMillis()
    val externalVmOptionsFile = configDirPath.listDirectoryEntries("*.vmoptions").firstOrNull()
    if (externalVmOptionsFile != null) {
      val currentVMFile = PathManager.getConfigDir().resolve(VMOptions.getFileName())
      if (currentVMFile.exists()) {
        ConfigImportHelper.mergeVmOptions(externalVmOptionsFile, currentVMFile, LOG)
      }
      else {
        Files.copy(externalVmOptionsFile, currentVMFile)
      }
      ConfigImportHelper.updateVMOptions(PathManager.getConfigDir(), LOG)
    }
    CustomConfigMigrationOption.MigrateFromCustomPlace(configDirPath).writeConfigMarkerFile(PathManager.getConfigDir())
    migrateLocalization()
    (System.currentTimeMillis() - startTime).let {
      LOG.info("Raw import finished in $it ms.")
      ImportSettingsEventsCollector.jbTotalImportTimeSpent(it)
    }
  }

  fun migrateLocalization() {
    ConfigImportHelper.migrateLocalization(configDirPath, pluginsPath)
  }

  internal class ImportStreamProvider(private val configDirPath: Path) : StreamProvider {
    override val isExclusive = false

    override val saveStorageDataOnReload: Boolean
      get() = false

    override fun isApplicable(fileSpec: String, roamingType: RoamingType): Boolean {
      return false
    }

    override fun write(fileSpec: String, content: ByteArray, roamingType: RoamingType) {
    }

    override fun read(fileSpec: String, roamingType: RoamingType, consumer: (InputStream?) -> Unit): Boolean {
      if (fileSpec == PROJECT_DEFAULT_FILE_SPEC) {
        val path = configDirPath / PathManager.OPTIONS_DIRECTORY / PROJECT_DEFAULT_FILE_NAME
        if (!path.isRegularFile())
          return false
        consumer(FileInputStream(path.toFile()))
        return true
      }
      (configDirPath / PathManager.OPTIONS_DIRECTORY / fileSpec).let {
        if (it.exists()) {
          consumer(FileInputStream(it.toFile()))
          return true
        }
      }
      (configDirPath / fileSpec).let {
        if (it.exists()) {
          consumer(FileInputStream(it.toFile()))
          return true
        }
      }
      return false
    }

    override fun processChildren(path: String,
                                 roamingType: RoamingType,
                                 filter: (name: String) -> Boolean,
                                 processor: (name: String, input: InputStream, readOnly: Boolean) -> Boolean): Boolean {
      LOG.debug("Process Children $path")
      val folder = configDirPath.resolve(path)
      if (!folder.exists()) return true

      Files.walkFileTree(folder, object : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path, attrs: BasicFileAttributes?): FileVisitResult {
          if (!filter(file.name)) return FileVisitResult.CONTINUE
          if (!file.isRegularFile()) return FileVisitResult.CONTINUE

          val shouldProceed = file.inputStream().use { inputStream ->
            val fileSpec = configDirPath.relativize(file).invariantSeparatorsPathString
            read(fileSpec) {
              processor(file.fileName.toString(), inputStream, false)
            }
          }
          return if (shouldProceed) FileVisitResult.CONTINUE else FileVisitResult.TERMINATE
        }
      })
      // this method is called only for reading => no SETTINGS_CHANGED_TOPIC message is needed
      return true
    }

    override fun delete(fileSpec: String, roamingType: RoamingType): Boolean {
      LOG.debug("Deleting $fileSpec")
      return false
    }

  }
}

private val LOG = logger<JbSettingsImporter>()
