// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.rules

import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.util.system.OS
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.rules.ExternalResource
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.net.URLEncoder
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import kotlin.properties.Delegates

class InMemoryFsRule(private val os: OS = OS.Linux) : ExternalResource() {
  private var _fs: FileSystem? = null
  private var name: String by Delegates.notNull()

  override fun apply(base: Statement, description: Description): Statement {
    name = description.methodName
    return super.apply(base, description)
  }

  val fs: FileSystem
    get() {
      if (_fs == null) {
        _fs = fs(os, name)
      }
      return _fs!!
    }

  override fun after() {
    _fs?.close()
    _fs = null
  }
}

class InMemoryFsExtension(private val os: OS = OS.Linux) : BeforeEachCallback, AfterEachCallback {
  private var _fs: FileSystem? = null
  private var name: String by Delegates.notNull()

  val fs: FileSystem
    get() {
      if (_fs == null) {
        _fs = fs(os, name)
      }
      return _fs!!
    }

  override fun beforeEach(context: ExtensionContext) {
    name = context.displayName
  }

  override fun afterEach(context: ExtensionContext) {
    _fs?.close()
    _fs = null
  }
}

private fun fs(os: OS, name: String): FileSystem {
  val builder = when (os) {
    OS.Windows -> MemoryFileSystemBuilder.newWindows().setCurrentWorkingDirectory("C:\\").apply {
      if (OS.CURRENT == OS.Windows) {
        FileSystems.getDefault().rootDirectories.asSequence()
          .map { it.toString() }
          .filter { OSAgnosticPathUtil.isAbsoluteDosPath(it) }
          .forEach { addRoot(it) }
      }
    }
    OS.macOS -> MemoryFileSystemBuilder.newMacOs().setCurrentWorkingDirectory("/")
    OS.Linux -> MemoryFileSystemBuilder.newLinux().setCurrentWorkingDirectory("/")
    else -> throw UnsupportedOperationException("Unsupported: ${os}")
  }

  val sanitizedName = URLEncoder.encode(name, Charsets.UTF_8)
  return builder.build(sanitizedName)
}
