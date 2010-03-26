package com.intellij.appengine.enhancement;

import com.intellij.compiler.CompilerIOUtil;
import com.intellij.openapi.compiler.ValidityState;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
* @author nik
*/
public class ClassFileItemDependenciesState implements ValidityState {
  private Map<String, Long> myDependencies;

  public ClassFileItemDependenciesState(List<VirtualFile> dependencies) {
    myDependencies = new HashMap<String, Long>();
    for (VirtualFile dependency : dependencies) {
      myDependencies.put(dependency.getPath(), dependency.getTimeStamp());
    }
  }

  public ClassFileItemDependenciesState(Map<String, Long> dependencies) {
    myDependencies = dependencies;
  }

  public boolean equalsTo(ValidityState otherState) {
    return otherState instanceof ClassFileItemDependenciesState
           && myDependencies.equals(((ClassFileItemDependenciesState)otherState).myDependencies);
  }

  public void save(DataOutput out) throws IOException {
    out.writeInt(myDependencies.size());
    for (Map.Entry<String, Long> entry : myDependencies.entrySet()) {
      CompilerIOUtil.writeString(entry.getKey(), out);
      out.writeLong(entry.getValue());
    }
  }

  public static ValidityState load(DataInput in) throws IOException {
    final HashMap<String, Long> map = new HashMap<String, Long>();
    int size = in.readInt();
    while (size-- > 0) {
      final String path = CompilerIOUtil.readString(in);
      final long timestamp = in.readLong();
      map.put(path, timestamp);
    }
    return new ClassFileItemDependenciesState(map);
  }
}
