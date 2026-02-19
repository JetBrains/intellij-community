import com.intellij.openapi.vfs.VirtualFile;

class UsePluginIdEquals {

  boolean any(VirtualFile file1, VirtualFile file2) {
    return <warning descr="'VirtualFile' instances should be compared for equality, not identity">file1<caret> != file2</warning>;
  }

}
