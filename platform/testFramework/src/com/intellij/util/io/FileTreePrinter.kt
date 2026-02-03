// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io

import com.intellij.util.containers.nullize
import java.nio.charset.MalformedInputException
import java.nio.file.Path
import kotlin.io.path.isDirectory

@JvmOverloads
fun Path.getDirectoryTree(excluded: Set<String> = emptySet(), printContent: Boolean = true, printRootName: Boolean = true): String {
  val sb = StringBuilder()
  getDirectoryTree(this, 0, sb, excluded, printContent = printContent, printRootName = printRootName)
  return sb.toString()
}

private fun getDirectoryTree(dir: Path, indent: Int, sb: StringBuilder, excluded: Set<String>, printContent: Boolean, printRootName: Boolean) {
  val fileList = sortedFileList(dir, excluded).nullize() ?: return

  appendIndentString(indent, sb)
  if (printContent) {
    sb.append("\u251c\u2500\u2500")
  }
  if (printRootName) {
    sb.append(dir.fileName.toString())
  }
  sb.append("/")
  sb.append("\n")
  for (file in fileList) {
    if (file.isDirectory()) {
      getDirectoryTree(file, indent + 1, sb, excluded, printContent, printRootName)
    }
    else {
      printFile(file, indent + 1, sb, printContent)
    }
  }
}

private fun sortedFileList(dir: Path, excluded: Set<String>): List<Path>? {
  return dir.directoryStreamIfExists { stream ->
    var sequence = stream.asSequence()
    if (excluded.isNotEmpty()) {
      sequence = sequence.filter { !excluded.contains(it.fileName.toString()) }
    }
    val list = sequence.toMutableList()
    list.sort()
    list
  }
}

private fun printFile(file: Path, indent: Int, sb: StringBuilder, printContent: Boolean) {
  appendIndentString(indent, sb)
  if (printContent) {
    sb.append("\u251c\u2500\u2500")
  }
  val fileName = file.fileName.toString()
  sb.append(fileName)
  sb.append("\n")
  if (printContent && !(fileName.endsWith(".zip") || fileName.endsWith(".jar") || fileName.endsWith(".class"))) {
    try {
      sb.append(file.readChars()).append("\n\n")
    }
    catch (ignore: MalformedInputException) {
    }
  }
}

private fun appendIndentString(indent: Int, sb: StringBuilder) {
  for (i in 0 until indent) {
    sb.append("  ")
  }
}