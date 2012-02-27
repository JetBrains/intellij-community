package org.jetbrains.jps.android;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.storage.ValidityState;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidResourcesPackagingState implements ValidityState {
  private final Map<String, Long> myResourceFilesMap;
  private final Map<String, Long> myAssetFilesMap;

  public AndroidResourcesPackagingState(@NotNull Collection<String> resourceDirs, @NotNull Collection<String> assetDirs) {
    myResourceFilesMap = new HashMap<String, Long>();
    myAssetFilesMap = new HashMap<String, Long>();

    collectFiles(resourceDirs, myResourceFilesMap);
    collectFiles(resourceDirs, myAssetFilesMap);
  }

  private static void collectFiles(Collection<String> resourceDirs, final Map<String, Long> map) {
    for (String resourceDir : resourceDirs) {
      FileUtil.processFilesRecursively(new File(resourceDir), new Processor<File>() {
        @Override
        public boolean process(File file) {
          map.put(FileUtil.toSystemIndependentName(file.getPath()), file.lastModified());
          return true;
        }
      });
    }
  }


  public AndroidResourcesPackagingState(DataInput in) throws IOException {
    final int resourcesCount = in.readInt();
    myResourceFilesMap = new HashMap<String, Long>(resourcesCount);

    for (int i = 0; i < resourcesCount; i++) {
      final String filePath = in.readUTF();
      final long timestamp = in.readLong();

      myResourceFilesMap.put(filePath, timestamp);
    }
    final int assetsCount = in.readInt();
    myAssetFilesMap = new HashMap<String, Long>(assetsCount);

    for (int i = 0; i < assetsCount; i++) {
      final String filePath = in.readUTF();
      final long timestamp = in.readLong();

      myAssetFilesMap.put(filePath, timestamp);
    }
  }

  @Override
  public boolean equalsTo(ValidityState otherState) {
    if (!(otherState instanceof AndroidResourcesPackagingState)) {
      return false;
    }

    final AndroidResourcesPackagingState otherState1 = (AndroidResourcesPackagingState)otherState;
    return otherState1.myResourceFilesMap.equals(myResourceFilesMap) &&
           otherState1.myAssetFilesMap.equals(myAssetFilesMap);
  }

  @Override
  public void save(DataOutput out) throws IOException {
    out.writeInt(myResourceFilesMap.size());

    for (Map.Entry<String, Long> entry : myResourceFilesMap.entrySet()) {
      out.writeUTF(entry.getKey());
      out.writeLong(entry.getValue());
    }
    out.writeInt(myAssetFilesMap.size());

    for (Map.Entry<String, Long> entry : myAssetFilesMap.entrySet()) {
      out.writeUTF(entry.getKey());
      out.writeLong(entry.getValue());
    }
  }
}
