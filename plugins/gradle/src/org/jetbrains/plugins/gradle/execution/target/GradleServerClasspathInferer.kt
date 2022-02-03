// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.io.URLUtil.urlToFile
import org.gradle.api.GradleException
import org.gradle.internal.classloader.ClassLoaderUtils
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.impldep.com.google.common.collect.MapMaker
import org.gradle.internal.impldep.com.google.common.io.ByteStreams
import org.gradle.internal.impldep.org.objectweb.asm.ClassReader
import org.gradle.internal.impldep.org.objectweb.asm.Type
import org.gradle.tooling.BuildAction
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.model.ProjectImportAction
import java.net.JarURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentMap

@ApiStatus.Internal
internal class GradleServerClasspathInferer {
  private val classesUsedInBuildAction = LinkedHashSet<Class<*>>()
  private val classesUsedByGradleProxyApp = LinkedHashSet<Class<*>>()
  fun add(buildAction: BuildAction<*>) {
    if (buildAction is ProjectImportAction) {
      classesUsedInBuildAction.addAll(buildAction.modelProvidersClasses)
    }
  }

  fun add(clazz: Class<*>) {
    classesUsedByGradleProxyApp.add(clazz)
  }

  fun getClasspath(): List<String> {
    val paths = LinkedHashSet<String>()
    classesUsedByGradleProxyApp.mapNotNullTo(paths) { PathManager.getJarPathForClass(it) }
    val classpathInferer = ClasspathInferer { className -> !className.startsWith("org.gradle.") } // skip traversing of Gradle api classes
    for (clazz in classesUsedInBuildAction) {
      val classpathUrls = classpathInferer.getClassPathFor(clazz)
      for (url in classpathUrls) {
        paths.add(urlToFile(url).path)
      }
    }
    return paths.toList()
  }

  fun getClassloaders(): Collection<ClassLoader> {
    return (classesUsedInBuildAction + classesUsedByGradleProxyApp)
      .map { it.classLoader }
      .toSet()
  }
}

/**
 * Based on [org.gradle.tooling.internal.provider.serialization.ClasspathInferer].
 * Only classes matching the given [classNamePredicate] will be processed.
 */
private class ClasspathInferer(private val classNamePredicate: (String) -> Boolean = { true }) {
  private val classPathCache: ConcurrentMap<Class<*>, Collection<URL>> = MapMaker().weakKeys().makeMap()

  /**
   * Returns a classpath URLs.
   */
  fun getClassPathFor(targetClass: Class<*>): Collection<URL> {
    if (!classNamePredicate(targetClass.name)) return emptyList()
    return classPathCache.computeIfAbsent(targetClass) { find(targetClass) }
  }

  private fun find(target: Class<*>,
                   visited: MutableCollection<Class<*>?> = hashSetOf(),
                   dest: MutableCollection<URL> = linkedSetOf()): Collection<URL> {
    val targetClassLoader = target.classLoader ?: return dest
    if (targetClassLoader === ClassLoaderUtils.getPlatformClassLoader()) return dest // A system class, skip it
    if (!visited.add(target)) return dest // Already seen this class, skip it

    val resourceName = target.name.replace('.', '/') + ".class"
    val resource = targetClassLoader.getResource(resourceName)
    if (resource == null) {
      log.warn("Could not determine classpath for $target")
      return dest
    }
    try {
      val classPathRoot = ClasspathUtil.getClasspathForClass(target)
      dest.add(classPathRoot.toURI().toURL())
      // To determine the dependencies of the class, load up the byte code and look for CONSTANT_Class entries in the constant pool
      val urlConnection = resource.openConnection()
      // Using the caches for these connections leaves the Jar files open. Don't use the cache, so that the Jar file is closed when the stream is closed below
      // There are other options for solving this that may be more performant. However a class is inspected this way once and the result reused, so this approach is probably fine
      (urlConnection as? JarURLConnection)?.useCaches = false
      val reader = urlConnection.getInputStream().use { ClassReader(ByteStreams.toByteArray(it)) }
      val charBuffer = CharArray(reader.maxStringLength)
      for (i in 1 until reader.itemCount) {
        val itemOffset = reader.getItem(i)
        if (itemOffset > 0 && reader.readByte(itemOffset - 1) == 7) {
          val classDescriptor = reader.readUTF8(itemOffset, charBuffer)
          var type = Type.getObjectType(classDescriptor)
          while (type.sort == Type.ARRAY) {
            type = type.elementType
          }
          if (type.sort != Type.OBJECT) continue  // A primitive type
          val className = type.className
          if (className == target.name) continue // A reference to this class
          if (!classNamePredicate(className)) continue
          val cl: Class<*> = try {
            Class.forName(className, false, targetClassLoader)
          }
          catch (e: ClassNotFoundException) {
            log.warn("Could not determine classpath for $target")
            continue
          }
          find(cl, visited, dest)
        }
      }
    }
    catch (e: Exception) {
      throw GradleException("Could not determine the class-path for $target.", e)
    }
    return dest
  }

  companion object {
    private val log = logger<GradleServerClasspathInferer>()
  }
}
