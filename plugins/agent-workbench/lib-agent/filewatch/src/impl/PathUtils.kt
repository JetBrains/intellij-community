// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.filewatch.impl

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.WatchEvent

@Suppress("UNCHECKED_CAST")
internal fun WatchEvent<*>.pathContext(): Path? = (this as WatchEvent<Path>).context()

internal fun recursiveListFiles(fileTreeVisitor: FileTreeVisitor, root: Path): Set<Path> {
  val files = HashSet<Path>()
  when {
    Files.isDirectory(root) -> fileTreeVisitor.recursiveVisitFiles(root, files::add, files::add)
    Files.exists(root) -> files.add(root)
  }
  return files
}
