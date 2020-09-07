// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.plugins.groovy.util.LibrariesUtil.SOME_GROOVY_CLASS
import org.jetbrains.plugins.groovy.util.LibrariesUtil.findJarWithClass
import java.util.jar.Attributes

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
