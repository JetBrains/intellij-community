import com.intellij.openapi.vfs.VirtualFile;

class UsePluginIdEquals {

  boolean any(VirtualFile file1, VirtualFile file2) {
    return !file1.equals(file2);
  }

}
