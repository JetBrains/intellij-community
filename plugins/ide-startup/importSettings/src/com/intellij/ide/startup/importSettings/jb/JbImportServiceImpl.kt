// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.jb

import com.intellij.ide.plugins.*
import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.StartupImportIcons
import com.intellij.ide.startup.importSettings.data.*
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.OptProperty
import com.jetbrains.rd.util.reactive.Property
import com.jetbrains.rd.util.threading.coroutines.launch
import kotlinx.coroutines.*
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
                         override val lastUsage: LocalDate,
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
    val descriptorDefferreds = loadCustomDescriptorsFromDir(coroutineScope, pluginsDirPath, context, null)
    descriptors2ProcessCnt = descriptorDefferreds.size
    JbImportServiceImpl.LOG.debug("There are ${descriptorDefferreds.size} plugins in $pluginsDirPath")
    for (def in descriptorDefferreds) {
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

}

class JbSettingsCategory(override val id: String,
                         override val icon: Icon,
                         override val name: String,
                         override val comment: String?) : BaseSetting {}

class JbSettingsCategoryConfigurable(override val id: String,
                                     override val icon: Icon,
                                     override val name: String,
                                     override val comment: String?,
                                     override val list: List<List<ChildSetting>>) : Configurable {

}

class JbChildSetting(override val id: String,
                     override val name: String) : ChildSetting {
  override val leftComment = null
  override val rightComment = null
}


@Service
class JbImportServiceImpl(private val coroutineScope: CoroutineScope) : JbService {


  private val productsLazy: Lazy<Map<String, JbProductInfo>> = lazy {
    doListProducts()
  }

  override fun getOldProducts(): List<Product> {
    return emptyList()
  }

  override fun products(): List<Product> {
    return productsLazy.value.values.toList()
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
      val pluginsDir = Path.of(PathManager.getDefaultPluginPathFor(confDir.name))
      val dirName = confDir.name
      val matcher = IDE_NAME_PATTERN.matcher(dirName)
      if (!matcher.matches()) {
        continue
      }
      val optionsDir = confDir / "options"
      if (!optionsDir.isDirectory()) {
        continue
      }
      val optionsEntries = optionsDir.listDirectoryEntries("*.xml")
      var lastModified = FileTime.fromMillis(0)
      for (optionXml in optionsEntries) {
        if (optionXml.getLastModifiedTime() > lastModified) {
          lastModified = optionXml.getLastModifiedTime()
        }
      }
      val ideName = matcher.group(1)
      val ideVersion = matcher.group(2)
      val fullName = NameMappings.getFullName(ideName)
      val lastUsageLocalDate = LocalDate.ofInstant(lastModified.toInstant(), ZoneId.systemDefault())
      val jbProductInfo = JbProductInfo(ideVersion, lastUsageLocalDate, dirName, fullName, ideName,
                                        confDir, pluginsDir)
      jbProductInfo.prefetchPluginDescriptors(coroutineScope, context)
      retval[dirName] = jbProductInfo
    }
    return retval
  }

  override fun getSettings(itemId: String): List<BaseSetting> {
    val productInfo = productsLazy.value[itemId] ?: error("Can't find product")
    val pluginNames = arrayListOf<ChildSetting>()
    for (descriptor in productInfo.getPluginsDescriptors()) {
      pluginNames.add(JbChildSetting(descriptor.pluginId.idString, descriptor.name))
    }

    val pluginsCategory = JbSettingsCategoryConfigurable(SettingsCategory.PLUGINS.name, StartupImportIcons.Icons.Plugin,
                                                         ImportSettingsBundle.message("settings.category.plugins.name"),
                                                         ImportSettingsBundle.message("settings.category.plugins.description"),
                                                         listOf(
                                                           pluginNames
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

  override fun importSettings(productId: String, data: List<DataForSave>): DialogImportData {
    val productInfo = productsLazy.value[productId] ?: error("Can't find product")
    val progressProperty = OptProperty<Int>()
    val progressMessageProperty = Property("Initializing import")
    val importProgress = object : ImportProgress {
      override val progressMessage = progressMessageProperty
      override val progress = progressProperty
    }
    val appInfo = ApplicationInfo.getInstance()
    return object : DialogImportData {
      override val message: String = "Importing from ${productInfo.name} to ${appInfo.versionName}..."
      override val progress: ImportProgress = importProgress
      private var progressCnt = 0

      init {
        Lifetime.Eternal.launch {
          launch(Dispatchers.Default) {
            val importer = JbSettingsImporter(productInfo.configDirPath, productInfo.pluginsDirPath)
            importer.importOptions()
            progressCnt = 50
            progressProperty.set(progressCnt)
            importer.installPlugins()
            progressCnt = 100
            progressProperty.set(progressCnt)
            delay(200)
            ApplicationManager.getApplication().restart()
          }
        }
      }
    }
  }

  companion object {
    internal val LOG = logger<JbImportServiceImpl>()
    private val IDE_NAME_PATTERN = Pattern.compile("""([a-zA-Z]+)(20\d\d\.\d)""")
    fun getInstance(): JbImportServiceImpl = service()
    private val UI_CATEGORY = JbSettingsCategory(SettingsCategory.UI.name, StartupImportIcons.Icons.ColorPicker,
                                                 ImportSettingsBundle.message("settings.category.ui.name"),
                                                 ImportSettingsBundle.message("settings.category.ui.description")
    )
    private val KEYMAP_CATEGORY = JbSettingsCategory(SettingsCategory.KEYMAP.name, StartupImportIcons.Icons.Keyboard,
                                                     ImportSettingsBundle.message("settings.category.keymap.name"),
                                                     ImportSettingsBundle.message("settings.category.keymap.description")
    )
    private val CODE_CATEGORY = JbSettingsCategory(SettingsCategory.CODE.name, StartupImportIcons.Icons.Json,
                                                   ImportSettingsBundle.message("settings.category.code.name"),
                                                   ImportSettingsBundle.message("settings.category.code.description")
    )
    private val SYSTEM_CATEGORY = JbSettingsCategory(SettingsCategory.SYSTEM.name, StartupImportIcons.Icons.Settings,
                                                     ImportSettingsBundle.message("settings.category.system.name"),
                                                     ImportSettingsBundle.message("settings.category.system.description")
    )
    private val TOOLS_CATEGORY = JbSettingsCategory(SettingsCategory.TOOLS.name, StartupImportIcons.Icons.Build,
                                                    ImportSettingsBundle.message("settings.category.tools.name"),
                                                    ImportSettingsBundle.message("settings.category.tools.description")
    )
  }


}