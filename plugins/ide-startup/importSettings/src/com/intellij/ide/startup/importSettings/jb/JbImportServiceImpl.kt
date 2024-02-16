// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.jb

import com.intellij.configurationStore.getPerOsSettingsStorageFolderName
import com.intellij.ide.GeneralSettings
import com.intellij.ide.plugins.DescriptorListLoadingContext
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.loadCustomDescriptorsFromDirForImportSettings
import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.StartupImportIcons
import com.intellij.ide.startup.importSettings.data.*
import com.intellij.ide.startup.importSettings.transfer.TransferSettingsProgress
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.keymap.impl.KeymapManagerImpl
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.JDOMUtil
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import kotlinx.coroutines.*
import org.jdom.Element
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern
import javax.swing.Icon
import kotlin.Result
import kotlin.io.path.*
import kotlin.time.Duration.Companion.seconds

internal data class JbProductInfo(
  override val version: String,
  val lastUsageTime: FileTime,
  override val id: String,
  override val name: String,
  internal val codeName: String,
  val configDir: Path,
  val pluginDir: Path,
) : Product {
  private val descriptors = CopyOnWriteArrayList<Pair<IdeaPluginDescriptorImpl, Boolean>>()
  private var descriptors2ProcessCnt: Int = 0
  private var keymapRef: AtomicReference<String> = AtomicReference()
  val activeKeymap: String?
    get() = keymapRef.get()


  internal fun prefetchData(coroutineScope: CoroutineScope, context: DescriptorListLoadingContext) {
    prefetchPluginDescriptors(coroutineScope, context)
    prefetchKeymap(coroutineScope)
  }

  private fun prefetchKeymap(coroutineScope: CoroutineScope) {
    coroutineScope.async {
      val keymapFilePath = configDir.resolve("${PathManager.OPTIONS_DIRECTORY}/${getPerOsSettingsStorageFolderName()}/${KeymapManagerImpl.KEYMAP_STORAGE}")
      if (keymapFilePath.exists()) {
        val element = JDOMUtil.load(keymapFilePath)
        val children = element.getChildren("component")
        for (child in children) {
          if (child.getAttributeValue("name") == KeymapManagerImpl.KEYMAP_MANAGER_COMPONENT_NAME) {
            val activeKeymap: Element = child.getChild(KeymapManagerImpl.KEYMAP_FIELD) ?: return@async
            val keymapName = activeKeymap.getAttributeValue("name") ?: return@async
            keymapRef.set(keymapName)
          }
        }
      }
    }
  }

  private fun prefetchPluginDescriptors(coroutineScope: CoroutineScope, context: DescriptorListLoadingContext) {
    JbImportServiceImpl.LOG.debug("Prefetching plugin descriptors from $pluginDir")
    val descriptorDeferreds = loadCustomDescriptorsFromDirForImportSettings(scope = coroutineScope, dir = pluginDir, context = context)
    descriptors2ProcessCnt = descriptorDeferreds.size
    JbImportServiceImpl.LOG.debug { "There are ${descriptorDeferreds.size} plugins in $pluginDir" }
    for (def in descriptorDeferreds) {
      def.invokeOnCompletion {
        coroutineScope.async {
          val descr = def.await() ?: return@async
          val compatible = isCompatible(descr)
          descriptors.add(descr to compatible)
        }
      }
    }
  }

  private fun isCompatible(descriptor: IdeaPluginDescriptorImpl): Boolean {
    if (PluginManagerCore.getPluginSet().isPluginEnabled(descriptor.pluginId)) {
      JbImportServiceImpl.LOG.info("Plugin \"${descriptor.name}\" from \"$name\" is already present in \"${IDEData.getSelf()?.fullName}\"")
      return false
    }

    // check for incompatibilities
    for (ic in descriptor.incompatibilities) {
      if (PluginManagerCore.getPluginSet().isPluginEnabled(ic)) {
        JbImportServiceImpl.LOG.info("Plugin \"${descriptor.name}\" from \"$name\" could not be migrated to \"${IDEData.getSelf()?.fullName}\", " +
                                     "because it is incompatible with ${ic}")
        return false
      }
    }

    // check for missing dependencies
    for (dependency in descriptor.pluginDependencies) {
      if (dependency.isOptional)
        continue
      if (!PluginManagerCore.getPluginSet().isPluginEnabled(dependency.pluginId)) {
        JbImportServiceImpl.LOG.info("Plugin \"${descriptor.name}\" from \"$name\" could not be migrated to \"${IDEData.getSelf()?.fullName}\", " +
                                     "because of the missing required dependency: ${dependency.pluginId}")
        return false
      }
    }
    return true
  }

  fun getPluginsDescriptors(): List<IdeaPluginDescriptorImpl> {
    val retval = descriptors.toList()
    if (retval.size != descriptors2ProcessCnt) {
      JbImportServiceImpl.LOG.warn("found ${retval.size} custom plugins, but found only $descriptors2ProcessCnt")
    }
    return retval.filter { it.second }.map { it.first }
  }


  private val _lastUsage = LocalDate.ofInstant(lastUsageTime.toInstant(), ZoneId.systemDefault())
  override val lastUsage: LocalDate = _lastUsage

}

open class JbSettingsCategory(
  val settingsCategory: SettingsCategory,
  override val icon: Icon,
  override val name: String,
  override val comment: String?,
) : BaseSetting {
  override val id: String
    get() = settingsCategory.name
}

class JbSettingsCategoryConfigurable(settingsCategory: SettingsCategory,
                                     override val icon: Icon,
                                     override val name: String,
                                     override val comment: String?,
                                     override val list: List<List<ChildSetting>>) :
  JbSettingsCategory(settingsCategory, icon, name, comment), Configurable

class JbChildSetting(override val id: String,
                     override val name: String) : ChildSetting {
  override val leftComment = null
  override val rightComment = null
}


@Service
class JbImportServiceImpl(private val coroutineScope: CoroutineScope) : JbService {

  private val products: ConcurrentMap<String, JbProductInfo> = ConcurrentHashMap()

  private val hasDataProcessed = CompletableDeferred<Boolean>()
  private val warmUpComplete = CompletableDeferred<Boolean>()

  override suspend fun hasDataToImport(): Boolean {
    return hasDataProcessed.await()
  }

  override fun getOldProducts(): List<Product> {
    return filterProducts(old = true)
  }

  override fun importFromCustomFolder(folderPath: Path) {
    val modalityState = ModalityState.current()
    coroutineScope.async(modalityState.asContextElement()) {
      val importer = JbSettingsImporter(folderPath, folderPath, null)
      importer.importRaw()
      LOG.info("Performing raw import from '$folderPath'")
      withContext(Dispatchers.EDT) {
        ApplicationManager.getApplication().invokeLater({
                                                          ApplicationManagerEx.getApplicationEx().restart(true)
                                                        }, modalityState)
      }
    }
  }

  override fun products(): List<Product> {
    @Suppress("RAW_RUN_BLOCKING")
    return runBlocking {
      try {
        withTimeout(2.seconds) {
          warmUpComplete.await()
        }
      }
      catch (tce: TimeoutCancellationException) {
        LOG.info("Timeout waiting for products warmUp. Will show what we have now: ${tce.message}")
      }
      filterProducts(old = false)
    }
  }

  private fun filterProducts(old: Boolean): List<Product> {
    val products = products.values.toList()
    val newProducts = hashMapOf<String, String>()
    for (product in products) {
      if (ConfigImportHelper.isConfigOld(product.lastUsageTime))
        continue
      val version = newProducts[product.codeName]
      if (version == null || version < product.version) {
        newProducts[product.codeName] = product.version
      }
    }
    if (old) {
      return products.filter {
        newProducts[it.codeName] != it.version
      }.sortedByDescending {
        it.lastUsageTime
      }
    }
    else {
      return products.filter { newProducts[it.codeName] == it.version }.sortedByDescending {
        it.lastUsageTime
      }
    }
  }

  override suspend fun warmUp() {
    val parentDir = Path.of(PathManager.getDefaultConfigPathFor(""))
    val context = DescriptorListLoadingContext(customDisabledPlugins = Collections.emptySet(),
                                               customBrokenPluginVersions = emptyMap(),
                                               productBuildNumber = { PluginManagerCore.buildNumber })
    val configDirectoriesCandidates = parentDir
      .listDirectoryEntries()
      .filter { it.isDirectory(LinkOption.NOFOLLOW_LINKS) }
      .sortedByDescending { it.getLastModifiedTime() }
    for (confDir in configDirectoriesCandidates) {
      LOG.info("Found ${confDir.name} under ${parentDir.pathString}")
      val pluginsDir = Path.of(PathManager.getDefaultPluginPathFor(confDir.name))
      val dirName = confDir.name
      val matcher = IDE_NAME_PATTERN.matcher(dirName)
      if (!matcher.matches()) {
        LOG.info("${confDir.name} doesn't match IDE_NAME_PATTERN, skipping it")
        continue
      }
      val optionsDir = confDir / PathManager.OPTIONS_DIRECTORY
      if (!optionsDir.isDirectory()) {
        LOG.info("${confDir.name} doesn't contain options directory, skipping it")
        continue
      }
      var lastModified: FileTime? = null
      for (fileName in DEFAULT_SETTINGS_FILES) {
        val optionXml = (optionsDir / fileName)
        if (!optionXml.isRegularFile())
          continue
        if (lastModified == null || optionXml.getLastModifiedTime() > lastModified) {
          lastModified = optionXml.getLastModifiedTime()
        }
      }
      if (lastModified == null) {
        LOG.info("${confDir.name}/options has no xml files, skipping it")
        continue
      }

      LOG.info("${optionsDir}' newest file is dated $lastModified")
      val ideName = matcher.group(1)
      val ideVersion = matcher.group(2)
      val fullName = NameMappings.getFullName(ideName)
      if (fullName == null) {
        continue
      }
      val fullNameWithVersion = "$fullName $ideVersion"
      val jbProductInfo = JbProductInfo(ideVersion, lastModified, dirName, fullNameWithVersion, ideName,
                                        confDir, pluginsDir)
      jbProductInfo.prefetchData(coroutineScope, context)
      products[dirName] = jbProductInfo
      hasDataProcessed.completeWith(Result.success(true))
    }
    warmUpComplete.completeWith(Result.success(true))
    if (!hasDataProcessed.isCompleted) {
      hasDataProcessed.completeWith(Result.success(products.isNotEmpty()))
    }
  }

  override fun getSettings(itemId: String): List<JbSettingsCategory> {
    LOG.info("User has selected $itemId")
    val productInfo = products[itemId] ?: error("Can't find product")
    val plugins = arrayListOf<ChildSetting>()
    val pluginNames = arrayListOf<String>()
    for (descriptor in productInfo.getPluginsDescriptors()) {
      plugins.add(JbChildSetting(descriptor.pluginId.idString, descriptor.name))
      pluginNames.add(descriptor.name)
    }
    LOG.info("Found ${pluginNames.size} custom plugins: ${pluginNames.joinToString()}")
    val pluginsCategory = JbSettingsCategoryConfigurable(SettingsCategory.PLUGINS, StartupImportIcons.Icons.Plugin,
                                                         ImportSettingsBundle.message("settings.category.plugins.name"),
                                                         ImportSettingsBundle.message("settings.category.plugins.description"),
                                                         listOf(
                                                           plugins
                                                         )
    )
    val activeKeymap = productInfo.activeKeymap
    val activeKeymapComment = if (activeKeymap == null) {
      null
    } else {
      ImportSettingsBundle.message("settings.category.keymap.description", activeKeymap)
    }
    val keymapsCategory = JbSettingsCategory(SettingsCategory.KEYMAP,
                                                      StartupImportIcons.Icons.Keyboard,
                                                      ImportSettingsBundle.message("settings.category.keymap.name"),
                                                      activeKeymapComment)
    return listOf(UI_CATEGORY,
                  keymapsCategory,
                  CODE_CATEGORY,
                  pluginsCategory,
                  TOOLS_CATEGORY,
                  SYSTEM_CATEGORY
    )
  }

  override fun getProductIcon(itemId: String, size: IconProductSize): Icon? {
    val productInfo = products[itemId] ?: error("Can't find product")
    return NameMappings.getIcon(productInfo.codeName, size)
  }

  override fun importSettings(productId: String, saveDataList: List<DataForSave>): DialogImportData {
    val productInfo = products[productId] ?: error("Can't find product")
    val filteredCategories = mutableSetOf<SettingsCategory>()
    var plugins2import: List<String>? = null
    var unselectedPlugins: List<String>? = null
    for (data in saveDataList) {
      if (data.id == SettingsCategory.PLUGINS.name) {
        // plugins category must be added as well, some PSC's use it, for instance KotlinNotebookApplicationOptionsProvider
        filteredCategories.add(SettingsCategory.PLUGINS)
        plugins2import = data.selectedChildIds
        unselectedPlugins = data.unselectedChildIds
        LOG.info("Will import ${data.selectedChildIds?.size} custom plugins: ${data.selectedChildIds?.joinToString()}\n" +
                 "${data.unselectedChildIds?.size} plugins will be skipped: ${data.unselectedChildIds?.joinToString()}")
      }
      else {
        val category = DEFAULT_SETTINGS_CATEGORIES[data.id] ?: continue
        filteredCategories.add(category)
      }
    }
    LOG.info("Will import the following categories: ${filteredCategories.joinToString()}")

    val allRoamableCategories = DEFAULT_SETTINGS_CATEGORIES.values
    val importEverything = filteredCategories.containsAll(allRoamableCategories)
                           && filteredCategories.contains(SettingsCategory.PLUGINS)
                           && unselectedPlugins.isNullOrEmpty()

    val importData = TransferSettingsProgress(productInfo)
    val importer = JbSettingsImporter(productInfo.configDir, productInfo.pluginDir, null)
    val progressIndicator = importData.createProgressIndicatorAdapter()
    val importLifetime = LifetimeDefinition()
    SettingsService.getInstance().importCancelled.advise(importLifetime) {
      progressIndicator.cancel()
    }
    val modalityState = ModalityState.current()

    val startTime = System.currentTimeMillis()
    coroutineScope.async(modalityState.asContextElement()) {
      suspend fun performImport(): Boolean {
        if (importEverything && NameMappings.canImportDirectly(productInfo.codeName)) {
          LOG.info("Started importing all...")
          progressIndicator.text2 = "Migrating options"
          //TODO support plugin list customization for raw import
          //storeImportConfig(productInfo.configDirPath, filteredCategories, plugins2Skip)
          importer.importRaw()
          LOG.info("Imported all completed in ${System.currentTimeMillis() - startTime} ms. ")
          LOG.info("Calling restart...")
          return true
        }
        else {
          try {
            progressIndicator.text2 = "Migrating options"
            LOG.info("Starting migration...")
            var restartRequired = false
            if (!plugins2import.isNullOrEmpty()) {
              LOG.info("Started importing plugins...")
              restartRequired = true
              importer.installPlugins(progressIndicator, plugins2import)
            }
            if (progressIndicator.isCanceled()) {
              LOG.info("Import cancelled after importing the plugins. ${if (restartRequired) "Will now restart." else ""}")
              return restartRequired
            }
            try {
              if (importer.importOptions(progressIndicator, filteredCategories)) {
                restartRequired = true
              }
            }
            catch (pce: ProcessCanceledException) {
              LOG.info("Import process cancelled. ${if (restartRequired) "Will now restart." else "Will proceed to the normal IDE startup."}")
              return restartRequired
            }
            LOG.info("Options migrated in ${System.currentTimeMillis() - startTime} ms.")
            progressIndicator.fraction = 0.99
            storeImportConfig(productInfo.configDir, filteredCategories, plugins2import)
            LOG.info("Plugins imported in ${System.currentTimeMillis() - startTime} ms. ")
            LOG.info("Calling restart...")
            // restart if we install plugins
            return restartRequired
          }
          catch (th: Throwable) {
            LOG.warn("An exception occurred during settings import", th)
            return true
          }
        }
      }

      fun restartIde() {
        ApplicationManager.getApplication().invokeLater({
                                                          ApplicationManagerEx.getApplicationEx().restart(true)
                                                        }, modalityState)
      }

      fun closeImportDialog() {
        SettingsService.getInstance().doClose.fire(Unit)
      }

      var shouldRestart = false
      try {
        shouldRestart = performImport()
      } catch (e: Throwable) {
        if (e is CancellationException || e is ProcessCanceledException) {
          LOG.info("Import cancellation detected. Proceeding normally without restart.")
        } else {
          LOG.error("Import error. Proceeding normally without restart.", e)
        }
      }

      LOG.info("Finishing the import process, shouldRestart=$shouldRestart")
      withContext(Dispatchers.EDT) {
        if (shouldRestart) {
          restartIde()
        }
        else {
          closeImportDialog()
        }
      }
    }
    return importData
  }

  companion object {
    internal val LOG = logger<JbImportServiceImpl>()
    private val IDE_NAME_PATTERN = Pattern.compile("""([a-zA-Z]+)(20\d\d\.\d)""")
    private val DEFAULT_SETTINGS_FILES = setOf(
      GeneralSettings.IDE_GENERAL_XML,
      StoragePathMacros.NON_ROAMABLE_FILE
    )
    fun getInstance(): JbImportServiceImpl = service()
    private val UI_CATEGORY = JbSettingsCategory(SettingsCategory.UI, StartupImportIcons.Icons.ColorPicker,
                                                 ImportSettingsBundle.message("settings.category.ui.name"),
                                                 ImportSettingsBundle.message("settings.category.ui.description")
    )
    private val CODE_CATEGORY = JbSettingsCategory(SettingsCategory.CODE, StartupImportIcons.Icons.Json,
                                                   ImportSettingsBundle.message("settings.category.code.name"),
                                                   ImportSettingsBundle.message("settings.category.code.description")
    )
    private val TOOLS_CATEGORY = JbSettingsCategory(SettingsCategory.TOOLS, StartupImportIcons.Icons.Build,
                                                    ImportSettingsBundle.message("settings.category.tools.name"),
                                                    ImportSettingsBundle.message("settings.category.tools.description")
    )
    private val SYSTEM_CATEGORY = JbSettingsCategory(SettingsCategory.SYSTEM, StartupImportIcons.Icons.Settings,
                                                     ImportSettingsBundle.message("settings.category.system.name"),
                                                     ImportSettingsBundle.message("settings.category.system.description")
    )
    val DEFAULT_SETTINGS_CATEGORIES = mapOf(
      SettingsCategory.UI.name to SettingsCategory.UI,
      SettingsCategory.KEYMAP.name to SettingsCategory.KEYMAP,
      SettingsCategory.CODE.name to SettingsCategory.CODE,
      SettingsCategory.TOOLS.name to SettingsCategory.TOOLS,
      SettingsCategory.SYSTEM.name to SettingsCategory.SYSTEM
    )
  }


}