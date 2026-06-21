// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.filewatch.impl

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

internal interface FileTreeVisitor {
  fun recursiveVisitFiles(root: Path, directoryConsumer: (Path) -> Unit, fileConsumer: (Path) -> Unit)
}

internal object DefaultFileTreeVisitor : FileTreeVisitor {
  override fun recursiveVisitFiles(root: Path, directoryConsumer: (Path) -> Unit, fileConsumer: (Path) -> Unit) {
    Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
      override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
        directoryConsumer(dir)
        return FileVisitResult.CONTINUE
      }

      override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
        fileConsumer(file)
        return FileVisitResult.CONTINUE
      }
    })
  }
}
