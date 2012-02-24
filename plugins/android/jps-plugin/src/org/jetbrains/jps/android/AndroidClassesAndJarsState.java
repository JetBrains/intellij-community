package org.jetbrains.jps.android;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.storage.ValidityState;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
class AndroidClassesAndJarsState implements ValidityState {
  private Map<String, Long> myFiles;

  public AndroidClassesAndJarsState(@NotNull Collection<String> roots) {
    myFiles = new HashMap<String, Long>();

    for (String rootPath : roots) {
      AndroidJpsUtil.processClassFilesAndJarsRecursively(rootPath, new Processor<File>() {
        @Override
        public boolean process(File file) {
          myFiles.put(FileUtil.toSystemIndependentName(file.getPath()), file.lastModified());
          return true;
        }
      });
    }
  }

  public AndroidClassesAndJarsState(@NotNull DataInput in) throws IOException {
    myFiles = new HashMap<String, Long>();
    int size = in.readInt();

    while (size-- > 0) {
      final String path = in.readUTF();
      final long timestamp = in.readLong();
      myFiles.put(path, timestamp);
    }
  }

  public boolean equalsTo(ValidityState otherState) {
    return otherState instanceof AndroidClassesAndJarsState
           && myFiles.equals(((AndroidClassesAndJarsState)otherState).myFiles);
  }

  public void save(DataOutput out) throws IOException {
    out.writeInt(myFiles.size());

    for (String path : myFiles.keySet()) {
      out.writeUTF(path);
      out.writeLong(myFiles.get(path));
    }
  }
}
