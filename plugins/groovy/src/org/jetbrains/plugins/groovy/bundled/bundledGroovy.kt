// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("BundledGroovy")

package org.jetbrains.plugins.groovy.bundled

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.JarUtil.getJarAttribute
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopesCore
import groovy.lang.GroovyObject
import org.jetbrains.plugins.groovy.config.AbstractConfigUtils.UNDEFINED_VERSION
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils
import java.io.File
import java.util.jar.Attributes.Name.IMPLEMENTATION_VERSION

val bundledGroovyVersion: String by lazy(::doGetBundledGroovyVersion)

private fun doGetBundledGroovyVersion(): String = getJarAttribute(bundledGroovyFile, IMPLEMENTATION_VERSION) ?: UNDEFINED_VERSION

val bundledGroovyFile: File by lazy(::doGetBundledGroovyFile)

private fun doGetBundledGroovyFile(): File {
  val jarPath = PathManager.getJarPathForClass(GroovyObject::class.java) ?: error("Cannot find JAR containing groovy classes")
  val jar = File(jarPath)
  assert(GroovyConfigUtils.GROOVY_ALL_JAR_PATTERN.matcher(jar.name).matches()) { "Incorrect path to groovy JAR: " + jarPath }
  return jar
}

val bundledGroovyJarRoot: VirtualFile? by lazy(::doGetBundledGroovyRoot)

private fun doGetBundledGroovyRoot(): VirtualFile? {
  val jar = bundledGroovyFile
  val jarFile = VfsUtil.findFileByIoFile(jar, false) ?: return null
  return JarFileSystem.getInstance().getJarRootForLocalFile(jarFile)
}

fun createBundledGroovyScope(project: Project): GlobalSearchScope? {
  val root = bundledGroovyJarRoot ?: return null
  return GlobalSearchScopesCore.directoryScope(project, root, true)
}
