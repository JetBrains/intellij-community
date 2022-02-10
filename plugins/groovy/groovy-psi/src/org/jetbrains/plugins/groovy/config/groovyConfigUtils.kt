// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.config

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.libraries.JarVersionDetectionUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.plugins.groovy.config.AbstractConfigUtils.UNDEFINED_VERSION
import org.jetbrains.plugins.groovy.util.LibrariesUtil
import org.jetbrains.plugins.groovy.util.LibrariesUtil.*
import java.util.jar.Attributes
import java.util.regex.Pattern

fun getSdkVersion(module: Module): @NlsSafe String? {
  return CachedValuesManager.getManager(module.project).getCachedValue(module) {
    Result.create(doGetSdkVersion(module), ProjectRootManager.getInstance(module.project))
  }
}

private fun doGetSdkVersion(module: Module): String? {
  return fromJar(module)
         ?: getSdkVersionFromHome(module)
}

private fun fromJar(module: Module): String? {
  val jar = findJarWithClass(module, SOME_GROOVY_CLASS) ?: return null
  val jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(jar) ?: return null
  return JarVersionDetectionUtil.getMainAttribute(jarRoot, Attributes.Name.IMPLEMENTATION_VERSION)
}

private fun getSdkVersionFromHome(module: Module): String? {
  val path = LibrariesUtil.getGroovyHomePath(module) ?: return null
  return GroovyConfigUtils.getInstance().getSDKVersion(path).takeUnless {
    it == UNDEFINED_VERSION
  }
}

sealed class GroovyHomeKind private constructor(val jarsPath : String, val subPaths: List<String>, val pattern: Pattern) {

  class Jar(path: String) : GroovyHomeKind(path, listOf("*.jar"), GroovyConfigUtils.GROOVY_JAR_PATTERN)
  class Lib(path: String) : GroovyHomeKind(path + "/lib", listOf("*.jar", "*/*.jar"), GroovyConfigUtils.GROOVY_JAR_PATTERN)
  class Embeddable(path: String) : GroovyHomeKind(path + "/embeddable", listOf("*.jar", "*/*.jar"), GroovyConfigUtils.GROOVY_ALL_JAR_PATTERN)

  companion object {
    @JvmStatic
    fun fromString(path : String) : GroovyHomeKind? =
      if (getFilesInDirectoryByPattern(path + "/lib", GroovyConfigUtils.GROOVY_JAR_PATTERN).size > 0) {
        Lib(path)
      } else if (getFilesInDirectoryByPattern(path + "/embeddable", GroovyConfigUtils.GROOVY_ALL_JAR_PATTERN).size > 0) {
        Embeddable(path)
      } else if (getFilesInDirectoryByPattern(path, GroovyConfigUtils.GROOVY_JAR_PATTERN).size > 0) {
        Jar(path)
      } else {
        null
      }
  }
}
