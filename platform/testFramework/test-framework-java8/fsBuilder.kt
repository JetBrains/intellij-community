package com.intellij.testFramework

import java.nio.file.FileSystem

fun FileSystem.file(path: String, data: ByteArray): FileSystem {
  getPath(path).write(data)
  return this
}

fun FileSystem.file(path: String, data: String) = file(path, data.toByteArray())