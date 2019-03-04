// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.config

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.libraries.JarVersionDetectionUtil
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.plugins.groovy.util.LibrariesUtil
import org.jetbrains.plugins.groovy.util.LibrariesUtil.SOME_GROOVY_CLASS

fun getSdkVersion(module: Module): String? {
  return CachedValuesManager.getManager(module.project).getCachedValue(module) {
    Result.create(doGetSdkVersion(module), ProjectRootManager.getInstance(module.project))
  }
}

private fun doGetSdkVersion(module: Module): String? {
  return JarVersionDetectionUtil.detectJarVersion(SOME_GROOVY_CLASS, module)
         ?: getSdkVersionFromHome(module)
}

private fun getSdkVersionFromHome(module: Module): String? {
  val path = LibrariesUtil.getGroovyHomePath(module)
  return if (path == null) null else GroovyConfigUtils.getInstance().getSDKVersion(path)
}
