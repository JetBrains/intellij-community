// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.jb

import com.intellij.ide.plugins.DescriptorListLoadingContext
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.loadCustomDescriptorsFromDir
import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.StartupImportIcons
import com.intellij.ide.startup.importSettings.data.*
import com.intellij.ide.startup.importSettings.transfer.TransferSettingsProgress
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.regex.Pattern
import javax.swing.Icon
import kotlin.io.path.*

data class JbProductInfo(override val version: String,
                         val lastUsageTime: FileTime,
                         override val id: String,
                         override val name: String,
                         internal val codeName: String,
                         val configDirPath: Path,
                         val pluginsDirPath: Path
) : Product {
  private val descriptors = CopyOnWriteArrayList<IdeaPluginDescriptorImpl>()
  private var descriptors2ProcessCnt: Int = 0

  internal fun prefetchPluginDescriptors(coroutineScope: CoroutineScope, context: DescriptorListLoadingContext) {
    JbImportServiceImpl.LOG.debug("Prefetching plugin descriptors from $pluginsDirPath")
    val descriptorDeferreds = loadCustomDescriptorsFromDir(coroutineScope, pluginsDirPath, context, null)
    descriptors2ProcessCnt = descriptorDeferreds.size
    JbImportServiceImpl.LOG.debug("There are ${descriptorDeferreds.size} plugins in $pluginsDirPath")
    for (def in descriptorDeferreds) {
      def.invokeOnCompletion {
        coroutineScope.async {
          val descr = def.await() ?: return@async
          descriptors.add(descr)
        }
      }
    }
  }

  fun getPluginsDescriptors(): List<IdeaPluginDescriptorImpl> {
    val retval = descriptors.toList()
    if (retval.size != descriptors2ProcessCnt) {
      JbImportServiceImpl.LOG.warn("found ${retval.size} custom plugins, but found only $descriptors2ProcessCnt")
    }
    return retval
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

  override fun hasDataToImport() = products().any()

  private val productsLazy: Lazy<Map<String, JbProductInfo>> = lazy {
    doListProducts()
  }

  override fun getOldProducts(): List<Product> {
    return filterProducts(old = true)
  }

  override fun products(): List<Product> {
    return filterProducts(old = false)
  }

  private fun filterProducts(old: Boolean): List<Product> {
    val products = productsLazy.value.values.toList()
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
      }
    }
    else {
      return products.filter { newProducts[it.codeName] == it.version }
    }
  }

  private fun doListProducts(): Map<String, JbProductInfo> {
    val retval = HashMap<String, JbProductInfo>()
    val parentDir = Path.of(PathManager.getDefaultConfigPathFor(""))
    val context = DescriptorListLoadingContext(customDisabledPlugins = Collections.emptySet(),
                                               customBrokenPluginVersions = emptyMap(),
                                               productBuildNumber = { PluginManagerCore.buildNumber })
    for (confDir in parentDir.listDirectoryEntries()) {
      if (!confDir.isDirectory()) {
        continue
      }
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
      val optionsEntries = optionsDir.listDirectoryEntries("*.xml")
      if (optionsEntries.isEmpty()) {
        LOG.info("${confDir.name}/options has no xml files, skipping it")
        continue
      }
      var lastModified = FileTime.fromMillis(0)
      for (optionXml in optionsEntries) {
        if (optionXml.getLastModifiedTime() > lastModified) {
          lastModified = optionXml.getLastModifiedTime()
        }
      }

      LOG.info("${optionsDir}' newest file is dated $lastModified")
      val ideName = matcher.group(1)
      val ideVersion = matcher.group(2)
      val fullName = "${NameMappings.getFullName(ideName)} $ideVersion"
      val jbProductInfo = JbProductInfo(ideVersion, lastModified, dirName, fullName, ideName,
                                        confDir, pluginsDir)
      jbProductInfo.prefetchPluginDescriptors(coroutineScope, context)
      retval[dirName] = jbProductInfo
    }
    return retval
  }

  override fun getSettings(itemId: String): List<JbSettingsCategory> {
    LOG.info("User has selected $itemId")
    val productInfo = productsLazy.value[itemId] ?: error("Can't find product")
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
    return listOf(UI_CATEGORY,
                  KEYMAP_CATEGORY,
                  CODE_CATEGORY,
                  pluginsCategory,
                  TOOLS_CATEGORY,
                  SYSTEM_CATEGORY
    )
  }

  override fun getProductIcon(itemId: String, size: IconProductSize): Icon? {
    val productInfo = productsLazy.value[itemId] ?: error("Can't find product")
    return NameMappings.getIcon(productInfo.codeName, size)
  }

  override fun importSettings(productId: String, saveDataList: List<DataForSave>): DialogImportData {
    val productInfo = productsLazy.value[productId] ?: error("Can't find product")
    val filteredCategories = mutableSetOf<SettingsCategory>()
    var plugins2import: List<String>? = null
    for (data in saveDataList) {
      if (data.id == SettingsCategory.PLUGINS.name) {
        // plugins category must be added as well, some PSC's use it, for instance KotlinNotebookApplicationOptionsProvider
        filteredCategories.add(SettingsCategory.PLUGINS)
        plugins2import = data.childIds
        LOG.info("Will import ${plugins2import?.size} custom plugins: ${plugins2import?.joinToString()}")
      }
      else {
        val category = DEFAULT_SETTINGS_CATEGORIES[data.id] ?: continue
        filteredCategories.add(category.settingsCategory)
      }
    }
    LOG.info("Will import the following categories: ${filteredCategories.joinToString()}")

    val allRoamableCategories = DEFAULT_SETTINGS_CATEGORIES.values.map { it.settingsCategory }
    val importEverything = filteredCategories.containsAll(allRoamableCategories)

    val importData = TransferSettingsProgress(productInfo)
    val importer = JbSettingsImporter(productInfo.configDirPath, productInfo.pluginsDirPath, null)
    val progressIndicator = importData.createProgressIndicatorAdapter()
    val modalityState = ModalityState.current()

    val startTime = System.currentTimeMillis()
    coroutineScope.async(modalityState.asContextElement()) {
      if (importEverything && NameMappings.canImportDirectly(productInfo.codeName)) {
        LOG.info("Started importing all...")
        progressIndicator.text2 = "Migrating options"
        importer.importRaw(progressIndicator, plugins2import ?: emptyList())
        LOG.info("Imported all completed in ${System.currentTimeMillis() - startTime} ms. ")
        LOG.info("Calling restart...")
        withContext(Dispatchers.EDT) {
          ApplicationManager.getApplication().invokeLater({
                                                            ApplicationManagerEx.getApplicationEx().restart(true)
                                                          }, modalityState)
        }
      } else{
        progressIndicator.text2 = "Migrating options"
        LOG.info("Starting migration...")
        var restartRequired = false
        if (!plugins2import.isNullOrEmpty()) {
          LOG.info("Started importing plugins...")
          importer.installPlugins(progressIndicator, plugins2import)
          restartRequired = true
        }
        if (importer.importOptions(filteredCategories)) {
          restartRequired = true
        }
        LOG.info("Options migrated in ${System.currentTimeMillis() - startTime} ms.")
        progressIndicator.fraction = 0.1
        storeImportConfig(productInfo.configDirPath, filteredCategories)
        LOG.info("Plugins imported in ${System.currentTimeMillis() - startTime} ms. ")
        LOG.info("Calling restart...")
        // restart if we install plugins
        withContext(Dispatchers.EDT) {
          if (restartRequired) {
            ApplicationManager.getApplication().invokeLater({
                                                              ApplicationManagerEx.getApplicationEx().restart(true)
                                                            }, modalityState)
          } else {
            SettingsService.getInstance().doClose.fire(Unit)
          }
        }
      }
    }
    return importData
  }

  companion object {
    internal val LOG = logger<JbImportServiceImpl>()
    private val IDE_NAME_PATTERN = Pattern.compile("""([a-zA-Z]+)(20\d\d\.\d)""")
    fun getInstance(): JbImportServiceImpl = service()
    private val UI_CATEGORY = JbSettingsCategory(SettingsCategory.UI, StartupImportIcons.Icons.ColorPicker,
                                                 ImportSettingsBundle.message("settings.category.ui.name"),
                                                 ImportSettingsBundle.message("settings.category.ui.description")
    )
    private val KEYMAP_CATEGORY = JbSettingsCategory(SettingsCategory.KEYMAP, StartupImportIcons.Icons.Keyboard,
                                                     ImportSettingsBundle.message("settings.category.keymap.name"),
                                                     ImportSettingsBundle.message("settings.category.keymap.description")
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
      SettingsCategory.UI.name to UI_CATEGORY,
      SettingsCategory.KEYMAP.name to KEYMAP_CATEGORY,
      SettingsCategory.CODE.name to CODE_CATEGORY,
      SettingsCategory.TOOLS.name to TOOLS_CATEGORY,
      SettingsCategory.SYSTEM.name to SYSTEM_CATEGORY
    )
  }


}