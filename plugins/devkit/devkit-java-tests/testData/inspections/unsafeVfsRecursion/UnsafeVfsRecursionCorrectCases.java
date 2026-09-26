import com.intellij.openapi.vfs.VirtualFile;

class UnsafeVfsRecursionCorrectCases {

  // totally safe method:
  void printChildren(VirtualFile dir) {
    for (VirtualFile file : dir.getChildren()) { // it shouldn't be reported
      System.out.println(file.getPath());
    }
  }

  void processDirectoryNotRecursive(VirtualFile dir) {
    for (VirtualFile file : dir.getChildren()) { // it shouldn't be reported
      if (!file.isDirectory()) {
        processFile(file);
      } else {
        processDirectoryRecursive(file, "any"); // THIS IS NOT RECURSIVE CALL
      }
    }
  }

  // IT IS NOT CALLED RECURSIVELY (NOTICE ADDITIONAL PARAMETER)
  void processDirectoryRecursive(VirtualFile dir, String any) {
    // do nothing
  }

  void processFile(VirtualFile file) {
    // do nothing
  }

}
