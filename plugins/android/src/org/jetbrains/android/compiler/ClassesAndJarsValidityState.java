package org.jetbrains.android.compiler;

import com.intellij.compiler.CompilerIOUtil;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.compiler.ValidityState;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
* @author Eugene.Kudelevsky
*/
class ClassesAndJarsValidityState implements ValidityState {
  private Map<String, Long> myFiles;

  private void fillMap(VirtualFile file, Set<VirtualFile> visited) {
    if (file.isDirectory() && visited.add(file)) {
      for (VirtualFile child : file.getChildren()) {
        fillMap(child, visited);
      }
    }
    else if (StdFileTypes.CLASS.equals(file.getFileType()) || file.getFileType() instanceof ArchiveFileType) {
      myFiles.put(file.getPath(), file.getTimeStamp());
    }
  }

  public ClassesAndJarsValidityState(@NotNull Collection<VirtualFile> files) {
    myFiles = new HashMap<String, Long>();
    Set<VirtualFile> visited = new HashSet<VirtualFile>();
    for (VirtualFile file : files) {
      fillMap(file, visited);
    }
  }

  public ClassesAndJarsValidityState(@NotNull DataInput in) throws IOException {
    myFiles = new HashMap<String, Long>();
    int size = in.readInt();
    while (size-- > 0) {
      final String path = CompilerIOUtil.readString(in);
      final long timestamp = in.readLong();
      myFiles.put(path, timestamp);
    }
  }

  public boolean equalsTo(ValidityState otherState) {
    return otherState instanceof ClassesAndJarsValidityState
           && myFiles.equals(((ClassesAndJarsValidityState)otherState).myFiles);
  }

  public void save(DataOutput out) throws IOException {
    out.writeInt(myFiles.size());
    for (String dependency : myFiles.keySet()) {
      CompilerIOUtil.writeString(dependency, out);
      out.writeLong(myFiles.get(dependency));
    }
  }
}
