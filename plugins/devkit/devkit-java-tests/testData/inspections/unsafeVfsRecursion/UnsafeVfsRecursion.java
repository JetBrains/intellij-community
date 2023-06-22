import com.intellij.openapi.vfs.VirtualFile;
import com.example.CustomVirtualFile;

class UnsafeVfsRecursion {

  void processDirectoryRecursive(VirtualFile dir) {
    for (VirtualFile file : <warning descr="'VirtualFile.getChildren()' called from a recursive method">dir.getChildren()</warning>) {
      if (!file.isDirectory()) {
        // process file
      } else {
        processDirectoryRecursive(file); // recursive call
      }
    }
  }

  void processDirectoryRecursiveSubclassUsed(CustomVirtualFile dir) {
    for (VirtualFile file : <warning descr="'VirtualFile.getChildren()' called from a recursive method">dir.getChildren()</warning>) {
      if (!file.isDirectory()) {
        // process file
      } else if (dir instanceof CustomVirtualFile) {
        processDirectoryRecursiveSubclassUsed((CustomVirtualFile)file); // recursive call
      }
    }
  }

}
