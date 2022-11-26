import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;

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

  void processDirectoryRecursiveSubclassUsed(LightVirtualFile dir) {
    for (VirtualFile file : <warning descr="'VirtualFile.getChildren()' called from a recursive method">dir.getChildren()</warning>) {
      if (!file.isDirectory()) {
        // process file
      } else if (dir instanceof LightVirtualFile) {
        processDirectoryRecursiveSubclassUsed((LightVirtualFile)file); // recursive call
      }
    }
  }

}
