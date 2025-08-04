// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie

import com.intellij.DynamicBundle
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.remote.GrazieRemote.isJLangAvailableLocally
import com.intellij.grazie.remote.GrazieRemote.isValidBundleForLanguage
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.io.delete
import com.intellij.util.lang.UrlClassLoader
import org.jetbrains.annotations.ApiStatus
import org.languagetool.Language
import org.languagetool.Languages
import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

@ApiStatus.Internal
object GrazieDynamic : DynamicPluginListener {
  private val myDynClassLoaders by lazy {

    for (file in getOldFiles()) {
      file.delete(true)
    }

    ApplicationManager.getApplication().messageBus.connect()
      .subscribe(DynamicPluginListener.TOPIC, this)

    hashSetOf<ClassLoader>(
      UrlClassLoader.build()
        .parent(GraziePlugin.classLoader)
        .files(collectValidLocalBundles())
        .get()
    )
  }

  /**
   * Function that collects outdated directories that needs to be deleted.
   */
  private fun getOldFiles(): List<Path> {
    return Files.list(dynamicFolder)
      .filter { file -> Lang.entries.none { file.fileName.toString() == getStorageDescriptor(it) } }
      .toList()
  }

  private fun collectValidLocalBundles(): List<Path> {
    val languages = Lang.entries.filter { isJLangAvailableLocally(it) }
    val bundles = buildSet {
      for (language in languages) {
        val path = getLangDynamicFolder(language).resolve(language.ltRemote!!.file)
        if (language.isEnglish() || isValidBundleForLanguage(language, path)) {
          add(path)
        } else {
          thisLogger().error("""
          Skipping local bundle $path for language ${language.nativeName}. 
          Failed to verify integrity of local language bundle before adding it to class loader.
          """.trimIndent())
        }
      }
    }
    return bundles.toList()
  }

  override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
    if (pluginDescriptor.pluginId.idString == GraziePlugin.id) {
      myDynClassLoaders.clear()
    }
  }

  fun addDynClassLoader(classLoader: ClassLoader) = myDynClassLoaders.add(classLoader)

  private val dynClassLoaders: Set<ClassLoader>
    get() = myDynClassLoaders.toSet()

  private fun getDynamicFolderPath(): Path {
    val customFolder = System.getProperty("grazie.dynamic.customJarDirectory")
    if (customFolder != null) {
      return Path.of(customFolder)
    }
    return PathManager.getConfigDir().resolve("grazie")
  }

  fun getLangDynamicFolder(lang: Lang): Path = dynamicFolder.resolve(getStorageDescriptor(lang))

  /**
   * Creates a storage descriptor (directory name) for downloader.
   */
  private fun getStorageDescriptor(lang: Lang): String {
    if (lang.hunspellRemote != null) {
      return "${lang.iso}-LT${GraziePlugin.LanguageTool.version}-HN${GraziePlugin.Hunspell.version}"
    }
    return "${lang.iso}-LT${GraziePlugin.LanguageTool.version}"
  }

  val dynamicFolder: Path
    get() {
      val result = getDynamicFolderPath()
      Files.createDirectories(result)
      return result
    }

  fun loadLang(lang: Lang): Language? {
    val remote = lang.ltRemote ?: return null
    for (className in remote.langsClasses) {
      try {
        Languages.getOrAddLanguageByClassName("org.languagetool.language.$className")
      }
      catch (e: RuntimeException) {
        if (e.cause !is ClassNotFoundException) {
          throw e
        }
      }
    }
    return Languages.get().find { it::class.java.simpleName == lang.className }
  }

  fun loadClass(className: String): Class<*>? {
    return forClassLoader {
      try {
        Class.forName(className, true, it)
      }
      catch (e: ClassNotFoundException) {
        null
      }
    }
  }

  fun getResourceAsStream(path: String): InputStream? {
    return forClassLoader { it.getResourceAsStream(path.removePrefix("/")) }
  }

  fun getResource(path: String): URL? {
    return forClassLoader { it.getResource(path.removePrefix("/")) }
  }

  fun getResources(path: String): List<URL> {
    return forClassLoader { loader ->
      loader.getResources(path.removePrefix("/")).toList().takeIf<List<URL>> { it.isNotEmpty<URL?>() }
    }.orEmpty()
  }

  fun getResourceBundle(baseName: String, locale: Locale): ResourceBundle {
    return forClassLoader {
      try {
        DynamicBundle.getResourceBundle(GrazieBundle::class.java.classLoader, baseName)
      }
      catch (e: MissingResourceException) {
        null
      }
    } ?: throw MissingResourceException("Missing resource bundle for $baseName with locale $locale", GrazieDynamic.javaClass.name, baseName)
  }

  private inline fun <T : Any> forClassLoader(crossinline body: (ClassLoader) -> T?): T? {
    return body(GraziePlugin.classLoader) ?: dynClassLoaders
      .asSequence()
      .mapNotNull {
        body(it)
      }.firstOrNull()
  }
}
