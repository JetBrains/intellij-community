// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.jb

import com.intellij.configurationStore.*
import com.intellij.configurationStore.schemeManager.SchemeManagerFactoryBase
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager
import com.intellij.psi.codeStyle.CodeStyleSchemes
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.ui.ExperimentalUI
import com.intellij.util.io.systemIndependentPath
import io.github.classgraph.AnnotationInfo
import io.github.classgraph.ClassGraph
import io.github.classgraph.ScanResult
import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.collections.ArrayList
import kotlin.io.path.*

class JbSettingsImporter(private val configDirPath: Path,
                         private val pluginsPath: Path,
                         private val prevIdeHome: Path?
                         ) {
  private val componentStore = ApplicationManager.getApplication().stateStore as ComponentStoreImpl
  private val defaultNewUIValue = true
  private val IDE_GENERAL_XML = "ide.general.xml"

  suspend fun importOptions(categories: Set<SettingsCategory>) {
    val allFiles = mutableSetOf<String>()
    val loadedComponentNames = componentStore.getComponentNames()
    val notLoadedComponents = arrayListOf<String>()
    val unknownStorage = arrayListOf<String>()
    val optionsPath = configDirPath / PathManager.OPTIONS_DIRECTORY
    val storageManager = componentStore.storageManager as StateStorageManagerImpl
    val cachedFileStorages = storageManager.getCachedFileStorages().map { (it as FileBasedStorage).file.name }

    optionsPath.listDirectoryEntries("*.xml").map { it.name }.forEach { allFiles.add(it) }
    for (optionsEntry in optionsPath.listDirectoryEntries()) {
      if (optionsEntry.name.lowercase().endsWith(".xml")) {
        allFiles.add(optionsEntry.name)
        val element = JDOMUtil.load(optionsEntry)
        val children = element.getChildren("component")
        if (!cachedFileStorages.contains(optionsEntry.name)) {
          unknownStorage.add(optionsEntry.name)
        }
        for (componentElement in children) {
          val componentName = componentElement.getAttributeValue("name")
          LOG.info("Found $componentName in ${optionsEntry.name}")
          if (!loadedComponentNames.contains(componentName)) {
            notLoadedComponents.add(componentName)
          }
        }
      }
      else if (optionsEntry.isDirectory() && optionsEntry.name.lowercase() == getPerOsSettingsStorageFolderName()) {
        // i.e. mac/keymap.xml
        allFiles.addAll(filesFromFolder(optionsEntry, optionsEntry.name))
      }
    }
    // ensure CodeStyleSchemes manager is created
    // TODO check other scheme managers that needs to be created
    CodeStyleSchemes.getInstance()

    val schemeManagerFactory = SchemeManagerFactory.getInstance() as SchemeManagerFactoryBase
    schemeManagerFactory.process {
      val dirPath = configDirPath / it.fileSpec
      if (dirPath.isDirectory()) {
        allFiles.addAll(filesFromFolder(dirPath, it.fileSpec))
      }
    }

    //TODO: remove later
    LOG.info("Loaded components:\n${loadedComponentNames.joinToString()}")
    LOG.info("NOT loaded components(${notLoadedComponents.size}):\n${notLoadedComponents.joinToString()}")
    LOG.info("NOT loaded storages(${unknownStorage.size}):\n${unknownStorage.joinToString()}")

    loadNotLoadedComponents(notLoadedComponents)

    LOG.info("Detected ${allFiles.size} files to import: ${allFiles.joinToString()}")
    val files2process = filterFiles(allFiles, categories)

    LOG.info("After filtering we have ${files2process.size} files to import: ${files2process.joinToString()}")

    val provider = ImportStreamProvider(configDirPath)
    storageManager.addStreamProvider(provider)
    componentStore.reloadComponents(files2process, emptyList())
    storageManager.removeStreamProvider(provider::class.java)
    saveSettings(ApplicationManager.getApplication(), true)
  }

  private fun loadNotLoadedComponents(notLoadedComponents: List<String>) {
    val appServiceClasses = hashSetOf<Class<*>>()
    (ApplicationManager.getApplication() as ComponentManagerImpl).processAllImplementationClasses { componentClass, _ ->
      appServiceClasses.add(componentClass)
    }

    val pluginSet = PluginManagerCore.getPluginSet()
    for (mainDescriptor in pluginSet.enabledPlugins) {
      // we don't check classloader for sub descriptors because url set is the same
      val pluginClassLoader = mainDescriptor.pluginClassLoader as? PluginClassLoader
                              ?: continue
      scanClassLoader(pluginClassLoader).use { scanResult ->
        for (classInfo in scanResult.getClassesWithAnnotation(State::class.java.name)) {
          val stateAnnotation = classInfo.getAnnotationInfo(State::class.java.name) ?: continue
          val parameterValues = stateAnnotation.getParameterValues(false)
          val nameValue = parameterValues.find { it.name == "name" } ?: continue
          val storages = parameterValues.find { it.name == "storages" } ?: continue
          if (notLoadedComponents.contains(nameValue.value.toString())) {
            try {
              val clazz = pluginClassLoader.loadClass(classInfo.name)
              if (!appServiceClasses.contains(clazz) && !isAppLevelLightService(clazz))
                continue
              val psc = ApplicationManager.getApplication().instantiateClass(clazz, mainDescriptor.pluginId)
              componentStore.initComponent(psc, null, mainDescriptor.pluginId)
              val storage = (storages.value as Array<*>).find {
                val info = it as AnnotationInfo
                val deprecated = info.getParameterValues(false).find { pv -> pv.name == "deprecated" }
                deprecated == null || !(deprecated.value as Boolean)
              } as? AnnotationInfo ?: continue
              val file = storage.parameterValues.find { it.name == "value" }?.value
              LOG.info("Loaded unloaded component ${nameValue.value} from $file")
            }
            catch (th: Throwable) {
              LOG.warn("Cannot init ${nameValue} from ${classInfo.name}: ${th.message}", th)
            }
          }
        }
      }
    }
  }

  private fun isAppLevelLightService(clazz: Class<*>): Boolean {
    val serviceAnnotation = clazz.annotations.find { it.annotationClass == Service::class } as? Service ?: return false
    return serviceAnnotation.value.find { it == Service.Level.APP} != null
  }

  private fun scanClassLoader(pluginClassLoader: PluginClassLoader): ScanResult {
    return ClassGraph()
      .enableAnnotationInfo()
      .ignoreParentClassLoaders()
      .overrideClassLoaders(pluginClassLoader)
      .scan()
  }


  internal fun isNewUIValueChanged() : Boolean {
    val ideGeneralXmlFile = configDirPath / PathManager.OPTIONS_DIRECTORY / IDE_GENERAL_XML
    try {
      val ideGeneral = JDOMUtil.load(ideGeneralXmlFile.toFile())
      val registry = ideGeneral.getChildren("component").find {
        it.getAttributeValue("name") == "Registry"
      } ?: return false
      val newUIValue = registry.getChildren("entry").find {
        it.getAttributeValue("key") == ExperimentalUI.KEY
      }?.value ?: return false

      return newUIValue.toBoolean() != defaultNewUIValue
    } catch (e: Exception) {
      LOG.warn("An error occurred while checking new UI state", e)
      return false
    }
  }

  private fun filesFromFolder(dir: Path, prefix: String = dir.name): Collection<String> {
    val retval = ArrayList<String>()
    for (entry in dir.listDirectoryEntries()) {
      if (entry.isRegularFile()) {
        if (prefix.isNullOrEmpty()) {
          retval.add(entry.name)
        } else {
          retval.add("$prefix/${entry.name}")
        }
      }
    }
    return retval
  }


  private fun filterFiles(allFiles: Set<String>, categories: Set<SettingsCategory>): List<String> {
    val componentManager = ApplicationManager.getApplication() as ComponentManagerImpl
    val retval = hashSetOf<String>()
    val osFolderName = getPerOsSettingsStorageFolderName()
    componentManager.processAllImplementationClasses { aClass, _ ->
      if (PersistentStateComponent::class.java.isAssignableFrom(aClass)) {
        val state = aClass.getAnnotation(State::class.java) ?: return@processAllImplementationClasses
        if (!categories.contains(state.category) && !state.canBeMigrated)
          return@processAllImplementationClasses
        state.storages.forEach { storage ->
          if (storage.deprecated)
            return@forEach
          if (storage.roamingType == RoamingType.PER_OS && allFiles.contains("$osFolderName/${storage.value}")) {
            retval.add("$osFolderName/${storage.value}")
          } else if (storage.roamingType != RoamingType.DISABLED && allFiles.contains(storage.value)) {
            retval.add(storage.value)
          }
        }
      }
    }
    val schemeCategories = hashSetOf<String>()
    // fileSpec is e.g. keymaps/mykeymap.xml
    (SchemeManagerFactory.getInstance() as SchemeManagerFactoryBase).process {
      if (categories.contains(it.getSettingsCategory())) {
        schemeCategories.add(it.fileSpec)
      }
    }
    for (file in allFiles) {
      val split = file.split('/')
      if (split.size != 2)
        continue

      if (schemeCategories.contains(split[0])){
        retval.add(file)
      }
    }

    return retval.toList()
  }

  fun installPlugins(progressIndicator: ProgressIndicator, pluginIds: List<String>) {
    val importOptions = configImportOptions(progressIndicator, pluginIds)
    ConfigImportHelper.migratePlugins(
      pluginsPath,
      configDirPath,
      PathManager.getPluginsDir(),
      PathManager.getConfigDir(),
      importOptions
    ) { false }
  }

  private fun configImportOptions(progressIndicator: ProgressIndicator,
                                  pluginIds: List<String>): ConfigImportHelper.ConfigImportOptions {
    val importOptions = ConfigImportHelper.ConfigImportOptions(LOG)
    importOptions.isHeadless = true
    importOptions.headlessProgressIndicator = progressIndicator
    importOptions.importSettings = object : ConfigImportSettings {
      private val oldEarlyAccessRegistryTxt = configDirPath.resolve(EarlyAccessRegistryManager.fileName)
      override fun processPluginsToMigrate(newConfigDir: Path,
                                           oldConfigDir: Path,
                                           bundledPlugins: MutableList<IdeaPluginDescriptor>,
                                           nonBundledPlugins: MutableList<IdeaPluginDescriptor>) {
        nonBundledPlugins.removeIf { !pluginIds.contains(it.pluginId.idString) }
      }

      override fun shouldForceCopy(path: Path): Boolean {
        return path == oldEarlyAccessRegistryTxt
      }
    }
    return importOptions
  }

  fun importRaw(progressIndicator: ProgressIndicator, pluginIds: List<String>) {
    val storageManager = componentStore.storageManager as StateStorageManagerImpl
    val dummyProvider = DummyStreamProvider()
    // we add dummy provider to prevent IDE from saving files on shutdown
    // we also need to take care of EarlyAccessManager
    storageManager.addStreamProvider(dummyProvider)
    val importOptions = configImportOptions(progressIndicator, pluginIds)
    System.setProperty(EarlyAccessRegistryManager.DISABLE_SAVE_PROPERTY, "true")
    importOptions.isMergeVmOptions = true
    ConfigImportHelper.doImport(configDirPath, PathManager.getConfigDir(), prevIdeHome, LOG, importOptions)
  }

  internal class DummyStreamProvider : StreamProvider {
    override val isExclusive = true

    override fun write(fileSpec: String, content: ByteArray, roamingType: RoamingType) {}

    override fun read(fileSpec: String, roamingType: RoamingType, consumer: (InputStream?) -> Unit): Boolean {
      return false
    }

    override fun processChildren(path: String,
                                 roamingType: RoamingType,
                                 filter: (name: String) -> Boolean,
                                 processor: (name: String, input: InputStream, readOnly: Boolean) -> Boolean) = true

    override fun delete(fileSpec: String, roamingType: RoamingType): Boolean = true

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
            val fileSpec = configDirPath.relativize(file).systemIndependentPath
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

  companion object {
    val LOG = logger<JbSettingsImporter>()
  }
}