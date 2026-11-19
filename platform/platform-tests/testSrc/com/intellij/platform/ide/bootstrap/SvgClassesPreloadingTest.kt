// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap

import com.intellij.ui.svg.renderSvg
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.URL
import java.net.URLClassLoader

class SvgClassesPreloadingTest {
  @Test
  fun `the preloading document renders visible pixels`() {
    val image = renderSvg(data = PRELOADING_SVG.encodeToByteArray(), scale = 1f)

    assertThat(image.width).isEqualTo(16)
    assertThat(image.height).isEqualTo(16)
    val visiblePixelCount = (0 until image.height).sumOf { y -> (0 until image.width).count { x -> image.getRGB(x, y) ushr 24 != 0 } }
    assertThat(visiblePixelCount).isGreaterThan(0)
  }

  @Test
  fun `every renderer class of the preloading exists`() {
    val classLoader = javaClass.classLoader
    val missing = SVG_RENDERER_CLASS_NAMES.filter { classLoader.getResource(it.replace('.', '/') + ".class") == null }
    assertThat(missing).isEmpty()
  }

  /**
   * A new [RecordingClassLoader] loads the SVG classes, so the classes that other tests loaded do not hide a missing name.
   */
  @Test
  fun `the rendering after the preloading loads no class with a name`() {
    RecordingClassLoader(javaClass.classLoader).use { recordingClassLoader ->
      val preloading = recordingClassLoader.loadClass(PRELOADING_CLASS_NAME)
      assertThat(preloading.classLoader).isSameAs(recordingClassLoader)

      val classLoader = preloading.getMethod("preloadSvgParserClasses").invoke(null) as ClassLoader
      assertThat(classLoader).isSameAs(recordingClassLoader)
      preloading.getMethod("preloadSvgRendererClasses", ClassLoader::class.java).invoke(null, classLoader)
      assertThat(recordingClassLoader.takeLoadedClassNames()).isNotEmpty()

      val renderSvg = recordingClassLoader.loadClass("com.intellij.ui.svg.SvgKt")
        .getMethod("renderSvg", ByteArray::class.java, Float::class.javaPrimitiveType)
      // the lookup loads the parameter types of all public methods of the class, which the rendering does not need
      recordingClassLoader.takeLoadedClassNames()

      renderSvg.invoke(null, PRELOADING_SVG.encodeToByteArray(), 1f)
      assertThat(recordingClassLoader.takeLoadedClassNames()).isEmpty()
    }
  }
}

private const val PRELOADING_CLASS_NAME = "com.intellij.platform.ide.bootstrap.SvgClassesPreloadingKt"

private val RECORDED_PACKAGE_PREFIXES = listOf("com.github.weisj.jsvg.", "com.intellij.ui.svg.")

/**
 * Loads the preloading, the jsvg classes and the `com.intellij.ui.svg` classes itself, and records their names.
 * Other classes come from [parent].
 */
private class RecordingClassLoader(parent: ClassLoader) : URLClassLoader(codeSourcesOfSvgClasses(parent), parent) {
  private val loadedClassNames = ArrayList<String>()

  fun takeLoadedClassNames(): List<String> = synchronized(loadedClassNames) {
    val result = loadedClassNames.toList()
    loadedClassNames.clear()
    result
  }

  override fun loadClass(name: String, resolve: Boolean): Class<*> {
    if (name != PRELOADING_CLASS_NAME && RECORDED_PACKAGE_PREFIXES.none { name.startsWith(it) }) {
      return super.loadClass(name, resolve)
    }

    synchronized(getClassLoadingLock(name)) {
      findLoadedClass(name)?.let { return it }
      val aClass = try {
        findClass(name)
      }
      catch (_: ClassNotFoundException) {
        // the package is split over several modules, and the code sources hold only some of them
        return super.loadClass(name, resolve)
      }
      synchronized(loadedClassNames) {
        loadedClassNames.add(name)
      }
      if (resolve) {
        resolveClass(aClass)
      }
      return aClass
    }
  }
}

private fun codeSourcesOfSvgClasses(classLoader: ClassLoader): Array<URL> {
  return listOf(PRELOADING_CLASS_NAME, "com.intellij.ui.svg.JSvgDocumentFactoryKt", "com.github.weisj.jsvg.SVGDocument")
    .map { className ->
      val path = className.replace('.', '/') + ".class"
      val url = requireNotNull(classLoader.getResource(path)) { "Cannot find $path" }.toExternalForm()
      // the root of the jar file or of the directory that holds the class
      URI(if (url.startsWith("jar:")) url.substring("jar:".length, url.indexOf("!/")) else url.removeSuffix(path)).toURL()
    }
    .distinct()
    .toTypedArray()
}
