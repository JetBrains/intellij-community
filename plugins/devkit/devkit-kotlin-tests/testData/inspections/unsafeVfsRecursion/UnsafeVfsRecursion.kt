import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile


class UnsafeVfsRecursion {

  fun processDirectoryRecursive(dir: VirtualFile) {
    for (file in dir.<warning descr="'VirtualFile.getChildren()' called from a recursive method">getChildren()</warning>) {
      if (!file.isDirectory()) {
        // process file
      }
      else {
        processDirectoryRecursive(file) // recursive call
      }
    }
  }

  fun processDirectoryRecursiveWhenPropertyAccessSyntaxUsedForGetChildren(dir: VirtualFile) {
    for (file in <warning descr="'VirtualFile.getChildren()' called from a recursive method">dir.children</warning>) {
      if (!file.isDirectory()) {
        // process file
      }
      else {
        processDirectoryRecursiveWhenPropertyAccessSyntaxUsedForGetChildren(file) // recursive call
      }
    }
  }

  fun processDirectoryRecursiveWhenPropertyAccessSyntaxUsedForGetChildrenAndSublassUsed(dir: LightVirtualFile) {
    for (file in <warning descr="'VirtualFile.getChildren()' called from a recursive method">dir.children</warning>) {
      if (!file.isDirectory()) {
        // process file
      }
      else if (file is LightVirtualFile) {
        processDirectoryRecursiveWhenPropertyAccessSyntaxUsedForGetChildrenAndSublassUsed(file) // recursive call
      }
    }
  }
}

// in top level function:
fun processDirectoryRecursive(dir: VirtualFile) {
  for (file in dir.<warning descr="'VirtualFile.getChildren()' called from a recursive method">getChildren()</warning>) {
    if (!file.isDirectory()) {
      // process file
    }
    else {
      processDirectoryRecursive(file) // recursive call
    }
  }
}
