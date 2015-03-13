package org.jetbrains.protocolReader

/**
 * Records a list of files in the root directory and deletes files that were not re-generated.
 */
class FileSet [throws(javaClass<IOException>())]
(private val rootDir: Path) {
  SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private val unusedFiles: THashSet<Path>

  {
    unusedFiles = THashSet<Path>()
    Files.walkFileTree(rootDir, object : SimpleFileVisitor<Path>() {
      throws(javaClass<IOException>())
      override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
        return if (Files.isHidden(dir)) FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE
      }

      throws(javaClass<IOException>())
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
    unusedFiles.forEach(object : TObjectProcedure<Path> {
      override fun execute(path: Path): Boolean {
        try {
          if (Files.deleteIfExists(path)) {
            val parent = path.getParent()
            Files.newDirectoryStream(parent).use { stream ->
              if (!stream.iterator().hasNext()) {
                Files.delete(parent)
              }
            }
          }
        }
        catch (e: IOException) {
          throw RuntimeException(e)
        }

        return true
      }
    })
  }
}
