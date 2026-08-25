// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.testFramework.fixtures.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.findOrCreateDirectory
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.utils.vfs.refreshAndGetVirtualDirectory
import com.intellij.util.xmlb.XmlSerializer
import java.nio.file.Path

internal const val STATE_FILE_NAME = "_FileTestFixture.xml"

/**
 * The state of a fixture root, which survives the JVM in [STATE_FILE_NAME].
 */
internal data class State(
  var isInitialized: Boolean? = null,
  var errors: List<String>? = null,
  var snapshots: Map<String, String?>? = null,
)

/**
 * @return the empty state when [stateFile] does not exist or is not readable.
 */
internal fun readFixtureState(stateFile: Path): State {
  return runCatching {
    runReadActionBlocking {
      val element = JDOMUtil.load(stateFile)
      XmlSerializer.deserialize(element, State::class.java)
    }
  }.getOrElse { State() }
}

internal fun writeFixtureState(stateFile: Path, state: State) {
  val element = XmlSerializer.serialize(state)
  JDOMUtil.write(element, stateFile)
}

/**
 * Creates the directory `FileTestFixture/<relativePath>` in the system directory.
 * The VFS can access it until [parentDisposable] is disposed.
 */
internal fun createFixtureRoot(relativePath: String, parentDisposable: Disposable): VirtualFile {
  // The Gradle sync stores canonical paths, so the fixture root must not contain a symbolic link.
  val fixtureRoot = PathManager.getSystemDir().findOrCreateDirectory().toRealPath().resolve("FileTestFixture/$relativePath")
  VfsRootAccess.allowRootAccess(parentDisposable, fixtureRoot.toCanonicalPath())
  // The VFS survives the JVM. It can have an entry for a directory that is no longer on the disk.
  // A directory that the VFS creates from such an entry does not exist, and the next refresh invalidates it.
  return fixtureRoot.findOrCreateDirectory().refreshAndGetVirtualDirectory()
}
