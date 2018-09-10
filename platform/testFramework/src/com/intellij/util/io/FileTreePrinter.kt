// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io

import com.intellij.util.containers.nullize
import java.nio.file.Path
import java.util.*

@JvmOverloads
fun Path.getDirectoryTree(excluded: Set<String> = emptySet()): String {
  val sb = StringBuilder()
  getDirectoryTree(this, 0, sb, excluded)
  return sb.toString()
}

private fun getDirectoryTree(dir: Path, indent: Int, sb: StringBuilder, excluded: Set<String>) {
  val fileList = sortedFileList(dir)?.filter { !excluded.contains(it.fileName.toString()) }.nullize() ?: return

  getIndentString(indent, sb)
  sb.append("\u251c\u2500\u2500")
  sb.append(dir.fileName.toString())
  sb.append("/")
  sb.append("\n")
  for (file in fileList) {
    if (file.isDirectory()) {
      getDirectoryTree(file, indent + 1, sb, excluded)
    }
    else {
      printFile(file, indent + 1, sb)
    }
  }
}

private fun sortedFileList(dir: Path): List<Path>? {
  return dir.directoryStreamIfExists { stream ->
    val list = ArrayList<Path>()
    stream.mapTo(list) { it }
    list.sort()
    list
  }
}

private fun printFile(file: Path, indent: Int, sb: StringBuilder) {
  getIndentString(indent, sb)
  sb.append("\u251c\u2500\u2500")
  val fileName = file.fileName.toString()
  sb.append(fileName)
  sb.append("\n")
  if (!(fileName.endsWith(".zip") || fileName.endsWith(".jar"))) {
    sb.append(file.readChars()).append("\n\n")
  }
}

private fun getIndentString(indent: Int, sb: StringBuilder) {
  for (i in 0 until indent) {
    sb.append("  ")
  }
}