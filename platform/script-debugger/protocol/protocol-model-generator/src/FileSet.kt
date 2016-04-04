package org.jetbrains.protocolModelGenerator

import gnu.trove.THashSet
import gnu.trove.TObjectProcedure
import org.jetbrains.protocolReader.FileUpdater
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Records a list of files in the root directory and deletes files that were not re-generated.
 */
class FileSet(private val rootDir: Path) {
  private val unusedFiles = THashSet<Path>()

  init {
    Files.walkFileTree(rootDir, object : SimpleFileVisitor<Path>() {
      override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
        return if (Files.isHidden(dir)) FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE
      }

      override fun visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult {
        if (!Files.isHidden(path)) {
          unusedFiles.add(path)
        }
        return FileVisitResult.CONTINUE
      }
    })
  }

  fun createFileUpdater(filePath: String): FileUpdater {
    val file = rootDir.resolve(filePath)
    unusedFiles.remove(file)
    return FileUpdater(file)
  }

  fun deleteOtherFiles() {
    unusedFiles.forEach(TObjectProcedure<Path> { it ->
      if (Files.deleteIfExists(it)) {
        val parent = it.parent
        Files.newDirectoryStream(parent).use { stream ->
          if (!stream.iterator().hasNext()) {
            Files.delete(parent)
          }
        }
      }
      true
    })
  }
}
