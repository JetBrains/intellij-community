import com.intellij.openapi.vfs.VirtualFile;

class UnsafeVfsRecursion {

  void processDirectoryRecursive(VirtualFile dir) {
    for (VirtualFile file : <warning descr="VirtualFile.getChildren() called from a recursive method">dir.getChildren()</warning>) {
      if (!file.isDirectory()) {
        processFile(file);
      } else {
        processDirectoryRecursive(file); // recursive call
      }
    }
  }

  void processFile(VirtualFile file) {
    // do nothing
  }

}
