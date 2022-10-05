import com.intellij.openapi.vfs.VirtualFile

class UnsafeVfsRecursion {

  fun processDirectoryRecursive(dir: VirtualFile) {
    for (file in dir.<warning descr="VirtualFile.getChildren() called from a recursive method">getChildren()</warning>) {
      if (!file.isDirectory()) {
        // process file
      }
      else {
        processDirectoryRecursive(file) // recursive call
      }
    }
  }
}

// in top level function:
fun processDirectoryRecursive(dir: VirtualFile) {
  for (file in dir.<warning descr="VirtualFile.getChildren() called from a recursive method">getChildren()</warning>) {
    if (!file.isDirectory()) {
      // process file
    }
    else {
      processDirectoryRecursive(file) // recursive call
    }
  }
}
