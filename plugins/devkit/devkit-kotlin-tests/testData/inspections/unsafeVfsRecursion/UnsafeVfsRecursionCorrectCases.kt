import com.intellij.openapi.vfs.VirtualFile

class UnsafeVfsRecursionCorrectCases {
  // totally safe method:
  fun printChildren(dir: VirtualFile) {
    for (file in dir.getChildren()) { // it shouldn't be reported
      file.path
    }
  }

  fun processDirectoryNotRecursive(dir: VirtualFile) {
    for (file in dir.getChildren()) { // it shouldn't be reported
      if (!file.isDirectory()) {
        processFile(file)
      }
      else {
        processDirectoryRecursive(file, "any") // THIS IS NOT RECURSIVE CALL
      }
    }
  }

  // IT IS NOT CALLED RECURSIVELY (NOTICE ADDITIONAL PARAMETER)
  @Suppress("UNUSED_PARAMETER")
  fun processDirectoryRecursive(dir: VirtualFile, any: String) {
    // any
  }

  @Suppress("UNUSED_PARAMETER")
  fun processFile(file: VirtualFile) {
    // any
  }
}
