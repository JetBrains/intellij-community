// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.jb

import com.intellij.configurationStore.*
import com.intellij.configurationStore.schemeManager.SchemeManagerFactoryBase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.psi.codeStyle.CodeStyleSchemes
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.util.io.systemIndependentPath
import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.*

class JbSettingsImporter(private val configDirPath: Path, private val pluginsPath: Path) {
  private val componentStore = ApplicationManager.getApplication().stateStore as ComponentStoreImpl

  fun importOptions() {
    val files2load = arrayListOf<String>()
    val optionsPath = configDirPath / PathManager.OPTIONS_DIRECTORY
    optionsPath.listDirectoryEntries("*.xml").map { it.name }.forEach { files2load.add(it) }
    for (optionsEntry in optionsPath.listDirectoryEntries()) {
      if (optionsEntry.name.lowercase().endsWith(".xml") && optionsEntry.name.lowercase() != StoragePathMacros.NON_ROAMABLE_FILE) {
        files2load.add(optionsEntry.name)
      } else if (optionsEntry.isDirectory()) {
        files2load.addAll(filesFromFolder(optionsEntry))
      }
    }
    // ensure CodeStyleSchemes manager is created
    // TODO check other scheme managers that needs to be created
    CodeStyleSchemes.getInstance()

    val schemeManagerFactory = SchemeManagerFactory.getInstance() as SchemeManagerFactoryBase
    schemeManagerFactory.process {
      val dirPath = configDirPath / it.fileSpec
      if (dirPath.isDirectory()){
        files2load.addAll(filesFromFolder(dirPath))
      }
    }
    val storageManager = componentStore.storageManager as StateStorageManagerImpl
    val provider = ImportStreamProvider(configDirPath)
    storageManager.addStreamProvider(provider)
    componentStore.reloadComponents(files2load, emptyList())
  }

  private fun filesFromFolder(dir: Path, prefix: String = dir.name) : Collection<String> {
    val retval = ArrayList<String>()
    for (entry in dir.listDirectoryEntries()) {
      if (entry.isRegularFile()) {
        retval.add("$prefix/${entry.name}")
      }
    }
    return retval
  }

  private fun findComponentClasses(fileSpec: Set<String>): List<Class<PersistentStateComponent<Any>>> {
    val componentManager = ApplicationManager.getApplication() as ComponentManagerImpl
    val componentClasses = ArrayList<Class<PersistentStateComponent<Any>>>()
    componentManager.processAllImplementationClasses { aClass, _ ->
      if (PersistentStateComponent::class.java.isAssignableFrom(aClass)) {
        val state = aClass.getAnnotation(State::class.java)
        state?.storages?.forEach { storage ->
          if (!storage.deprecated && fileSpec.contains(storage.value)) {
            @Suppress("UNCHECKED_CAST")
            componentClasses.add(aClass as Class<PersistentStateComponent<Any>>)
          }
        }
      }
    }
    return componentClasses
  }

  fun installPlugins() {
    val importOptions = ConfigImportHelper.ConfigImportOptions(LOG)
    importOptions.isHeadless = true
    ConfigImportHelper.migratePlugins(
      pluginsPath,
      configDirPath,
      PathManager.getPluginsDir(),
      PathManager.getConfigDir(),
      importOptions
    ) { false }
  }

  internal class ImportStreamProvider(private val configDirPath: Path) : StreamProvider {
    override val isExclusive = true

    override fun write(fileSpec: String, content: ByteArray, roamingType: RoamingType) {
      LOG.warn("Writing to $fileSpec (Will do nothing)")
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
      LOG.warn("Process Children $path")
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
      LOG.warn("Deleting $fileSpec")
      return false
    }

  }
  companion object{
      val LOG = logger<JbSettingsImporter>()
  }
}