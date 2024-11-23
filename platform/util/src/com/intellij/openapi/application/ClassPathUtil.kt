// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.fasterxml.aalto.`in`.ReaderConfig
import com.intellij.diagnostic.LoadingState
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.text.Strings
import com.intellij.util.lang.UrlClassLoader
import com.sun.jna.TypeMapper
import com.sun.jna.platform.FileUtils
import it.unimi.dsi.fastutil.objects.Object2IntMap
import kotlinx.serialization.json.JsonElement
import net.jpountz.lz4.LZ4Factory
import org.apache.log4j.Appender
import org.apache.oro.text.regex.PatternMatcher
import org.codehaus.stax2.XMLStreamReader2
import org.intellij.lang.annotations.Flow
import org.jdom.Document
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object ClassPathUtil {
  @JvmStatic
  fun getUtilClassPath(): Collection<String> {
    val classes = getUtilClasses()
    val result = classes.mapNotNullTo(HashSet(classes.size), PathManager::getJarPathForClass)
    addKotlinStdlib(result)
    return result
  }

  @JvmStatic
  fun addKotlinStdlib(classPath: MutableCollection<String>) {
    val classLoader = PathManager::class.java.classLoader
    PathManager.getResourceRoot(classLoader, "kotlin/jdk7/AutoCloseableKt.class")?.let(classPath::add) // kotlin-stdlib-jdk7
    PathManager.getResourceRoot(classLoader, "kotlin/streams/jdk8/StreamsKt.class")?.let(classPath::add) // kotlin-stdlib-jdk8
    PathManager.getResourceRoot(classLoader, "gnu/trove/THashSet.class")?.let(classPath::add) // Trove
  }

  @JvmStatic
  fun getUtilClasses(): Array<Class<*>> {
    val classLoader = ClassPathUtil::class.java.classLoader
    return arrayOf(
      LoadingState::class.java,  // module 'intellij.platform.diagnostic'
      PathManager::class.java,  // module 'intellij.platform.util'
      Strings::class.java,  // module 'intellij.platform.util.base'
      classLoader.loadClass("com.intellij.util.xml.dom.XmlDomReader"),  // module 'intellij.platform.util.xmlDom'
      SystemInfoRt::class.java,  // module 'intellij.platform.util.rt'
      UrlClassLoader::class.java,  // module 'intellij.platform.util.classLoader'
      classLoader.loadClass("com.intellij.util.lang.Xxh3Impl"),  // intellij.platform.util.rt.java8 (required for classLoader)
      Flow::class.java,  // jetbrains-annotations
      Document::class.java,  // JDOM
      Appender::class.java,  // Log4J
      Object2IntMap::class.java,  // fastutil
      TypeMapper::class.java,  // JNA
      FileUtils::class.java,  // JNA (jna-platform)
      PatternMatcher::class.java,  // OROMatcher
      LZ4Factory::class.java,  // LZ4-Java
      ReaderConfig::class.java,  // Aalto XML
      XMLStreamReader2::class.java,  // Aalto XML
      JsonElement::class.java,  // kotlinx-serialization
      Pair::class.java // Kotlin stdlib
    )
  }
}