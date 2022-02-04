// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie

import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.remote.GrazieRemote
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.util.io.delete
import com.intellij.util.io.isFile
import com.intellij.util.lang.UrlClassLoader
import org.languagetool.Language
import org.languagetool.Languages
import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

internal object GrazieDynamic : DynamicPluginListener {
  private val myDynClassLoaders by lazy {
    val oldFiles = Files.walk(dynamicFolder).filter { file ->
      file.isFile() && Lang.values().all { it.remote.file.toAbsolutePath() != file.toAbsolutePath() }
    }

    for (file in oldFiles) {
      file.delete()
    }

    ApplicationManager.getApplication().messageBus.connect()
      .subscribe(DynamicPluginListener.TOPIC, this)

    hashSetOf<ClassLoader>(
      UrlClassLoader.build()
        .parent(GraziePlugin.classLoader)
        .files(GrazieRemote.allAvailableLocally().map { it.remote.file }).get()
    )
  }

  override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
    if (pluginDescriptor.pluginId?.idString == GraziePlugin.id) {
      myDynClassLoaders.clear()
    }
  }

  fun addDynClassLoader(classLoader: ClassLoader) = myDynClassLoaders.add(classLoader)

  private val dynClassLoaders: Set<ClassLoader>
    get() = myDynClassLoaders.toSet()

  val dynamicFolder: Path
    get() {
      val result = Paths.get(PathManager.getSystemPath(), "grazie")
      Files.createDirectories(result)
      return result
    }

  fun loadLang(lang: Lang): Language? {
    for (className in lang.remote.langsClasses) {
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
        Class.forName(className, false, it)
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
        ResourceBundle.getBundle(baseName, locale, it).takeIf { bundle -> bundle.locale.language == locale.language }
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
