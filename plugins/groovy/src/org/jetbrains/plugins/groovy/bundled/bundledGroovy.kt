// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("BundledGroovy")

package org.jetbrains.plugins.groovy.bundled

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.JarUtil.getJarAttribute
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFsConnectionListener
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.util.concurrency.SynchronizedClearableLazy
import groovy.lang.GroovyObject
import org.jetbrains.plugins.groovy.config.AbstractConfigUtils.UNDEFINED_VERSION
import java.io.File
import java.util.jar.Attributes.Name.IMPLEMENTATION_VERSION

val bundledGroovyVersion: SynchronizedClearableLazy<@NlsSafe String> = SynchronizedClearableLazy(::doGetBundledGroovyVersion)

private fun doGetBundledGroovyVersion(): String = getJarAttribute(bundledGroovyFile.get(), IMPLEMENTATION_VERSION) ?: UNDEFINED_VERSION

val bundledGroovyFile: SynchronizedClearableLazy<File> = SynchronizedClearableLazy(::doGetBundledGroovyFile)

private fun doGetBundledGroovyFile(): File {
  val jarPath = PathManager.getJarPathForClass(GroovyObject::class.java) ?: error("Cannot find JAR containing groovy classes")
  return File(jarPath)
}

val bundledGroovyJarRoot: SynchronizedClearableLazy<VirtualFile?> = SynchronizedClearableLazy(::doGetBundledGroovyRoot)

private fun doGetBundledGroovyRoot(): VirtualFile? {
  val jar = bundledGroovyFile.get()
  val jarFile = VfsUtil.findFileByIoFile(jar, false) ?: return null
  return JarFileSystem.getInstance().getJarRootForLocalFile(jarFile)
}

fun createBundledGroovyScope(project: Project): GlobalSearchScope? {
  val root = bundledGroovyJarRoot.get() ?: return null
  return GlobalSearchScopesCore.directoryScope(project, root, true)
}

internal class BundledGroovyPersistentFsConnectionListener: PersistentFsConnectionListener {
  override fun beforeConnectionClosed() {
    bundledGroovyJarRoot.drop()
  }
}
