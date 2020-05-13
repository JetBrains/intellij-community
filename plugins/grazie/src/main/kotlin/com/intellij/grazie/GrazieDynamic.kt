// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie

import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.remote.GrazieRemote
import com.intellij.openapi.application.PathManager
import com.intellij.util.io.delete
import com.intellij.util.io.isFile
import com.intellij.util.lang.UrlClassLoader
import org.languagetool.language.Language
import org.languagetool.language.Languages
import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

internal object GrazieDynamic {
  private val myDynClassLoaders by lazy {
    val oldFiles = Files.walk(dynamicFolder).filter { file ->
      file.isFile() && Lang.values().all { it.remote.file.toAbsolutePath() != file.toAbsolutePath() }
    }

    for (file in oldFiles) {
      file.delete()
    }

    hashSetOf<ClassLoader>(
      UrlClassLoader.build()
        .parent(GraziePlugin.classLoader)
        .urls(GrazieRemote.allAvailableLocally().map { it.remote.file.toUri().toURL() }).get()
    )
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
    lang.remote.langsClasses.forEach { className ->
      (loadClass("org.languagetool.language.$className")?.newInstance() as Language?)?.let { Languages.add(it) }
    }
    return Languages.get().find { it::class.java.simpleName == lang.className }
  }

  fun loadClass(className: String): Class<*>? = forClassLoader {
    try {
      Class.forName(className, true, it)
    }
    catch (e: ClassNotFoundException) {
      null
    }
  }

  fun getResourceAsStream(path: String): InputStream? {
    return forClassLoader { it.getResourceAsStream(path) }
           ?: if (path.startsWith("/")) forClassLoader { it.getResourceAsStream(path.drop(1)) } else null
  }

  fun getResource(path: String): URL? {
    return forClassLoader { it.getResource(path) }
           ?: if (path.startsWith("/")) forClassLoader { it.getResource(path.drop(1)) } else null
  }

  fun getResourceBundle(baseName: String, locale: Locale) = forClassLoader {
    try {
      ResourceBundle.getBundle(baseName, locale, it).takeIf { bundle -> bundle.locale.language == locale.language }
    }
    catch (e: MissingResourceException) {
      null
    }
  } ?: throw MissingResourceException("Missing resource bundle for $baseName with locale $locale", GrazieDynamic.javaClass.name, baseName)

  private fun <T : Any> forClassLoader(body: (ClassLoader) -> T?): T? = body(GraziePlugin.classLoader) ?: dynClassLoaders
    .asSequence()
    .mapNotNull {
      body(it)
    }.firstOrNull()
}
