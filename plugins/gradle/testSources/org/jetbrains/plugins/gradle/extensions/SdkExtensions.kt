// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.extensions

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.VfsTestUtil
import java.io.File
import java.io.FileNotFoundException

val Sdk.rootsFiles
  get() = OrderRootType.getAllTypes().flatMap { rootType ->
    rootProvider.getFiles(rootType).map { it to rootType }
  }

val Sdk.rootsUrls
  get() = OrderRootType.getAllTypes().flatMap { rootType ->
    rootProvider.getUrls(rootType).map { it to rootType }
  }

fun Sdk.cloneWithCorruptedRoots(tempDir: File): Sdk {
  val sdkClone = clone() as Sdk
  val corruptedRoots = generateCorruptedJdkRoots(sdkClone, tempDir)
  sdkClone.sdkModificator.run {
    name = "${name}_clone"
    removeAllRoots()
    corruptedRoots.forEach { (type, file) ->
      addRoot(type, file)
    }
    commitChanges()
  }
  return sdkClone
}

private fun generateCorruptedJdkRoots(
  jdk: Sdk,
  tempDir: File,
): List<Pair<VirtualFile, OrderRootType>> {
  val vfsTempDir = VfsUtil.findFile(tempDir.toPath(), true) ?: throw FileNotFoundException("Unable to find ${tempDir.path}")
  val rootsTempDir = VfsTestUtil.createDir(vfsTempDir, "corrupted-roots")
  return jdk.rootsFiles.map { (file, type) ->
    val rootFile = VfsTestUtil.createFile(rootsTempDir, file.name)
    Pair(rootFile, type).also {
      rootFile.delete(Sdk::class)
    }
  }
}