package org.jetbrains.jps.android;

import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.util.ResourceEntry;
import org.jetbrains.android.util.ResourceFileData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.storage.ValidityState;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidAptValidityState implements ValidityState {
  private final Map<String, ResourceFileData> myResources;
  private final List<ResourceEntry> myManifestElements;
  private final String myPackageName;
  private final Set<String> myLibPackages;

  public AndroidAptValidityState(@NotNull Map<String, ResourceFileData> resources,
                                 @NotNull List<ResourceEntry> manifestElements,
                                 @NotNull Collection<String> libPackages,
                                 @NotNull String packageName) {
    myResources = resources;
    myManifestElements = manifestElements;
    myLibPackages = new HashSet<String>(libPackages);
    myPackageName = packageName;
  }

  public AndroidAptValidityState(@NotNull DataInput in) throws IOException {
    myPackageName = in.readUTF();

    final int filesCount = in.readInt();
    myResources = new HashMap<String, ResourceFileData>(filesCount);

    for (int i = 0; i < filesCount; i++) {
      final String filePath = in.readUTF();

      final int entriesCount = in.readInt();
      final List<ResourceEntry> entries = new ArrayList<ResourceEntry>(entriesCount);

      for (int j = 0; j < entriesCount; j++) {
        final String resType = in.readUTF();
        final String resName = in.readUTF();
        entries.add(new ResourceEntry(resType, resName));
      }
      final long timestamp = in.readLong();
      myResources.put(filePath, new ResourceFileData(entries, timestamp));
    }

    final int manifestElementCount = in.readInt();
    myManifestElements = new ArrayList<ResourceEntry>(manifestElementCount);

    for (int i = 0; i < manifestElementCount; i++) {
      final String elementType = in.readUTF();
      final String elementName = in.readUTF();
      myManifestElements.add(new ResourceEntry(elementType, elementName));
    }

    final int libPackageCount = in.readInt();
    myLibPackages = new HashSet<String>(libPackageCount);

    for (int i = 0; i < libPackageCount; i++) {
      final String libPackage = in.readUTF();
      myLibPackages.add(libPackage);
    }
  }

  @Override
  public boolean equalsTo(ValidityState otherState) {
    if (!(otherState instanceof AndroidAptValidityState)) {
      return false;
    }
    final AndroidAptValidityState otherAndroidState = (AndroidAptValidityState)otherState;
    return otherAndroidState.myPackageName.equals(myPackageName) &&
           otherAndroidState.myResources.equals(myResources) &&
           otherAndroidState.myManifestElements.equals(myManifestElements) &&
           otherAndroidState.myLibPackages.equals(myLibPackages);
  }

  @Override
  public void save(DataOutput out) throws IOException {
    out.writeUTF(myPackageName);
    out.writeInt(myResources.size());

    for (Map.Entry<String, ResourceFileData> entry : myResources.entrySet()) {
      out.writeUTF(entry.getKey());

      final ResourceFileData fileData = entry.getValue();
      final List<ResourceEntry> resources = fileData.getValueResources();
      out.writeInt(resources.size());

      for (ResourceEntry resource : resources) {
        out.writeUTF(resource.getType());
        out.writeUTF(resource.getName());
      }
      out.writeLong(fileData.getTimestamp());
    }
    out.writeInt(myManifestElements.size());

    for (ResourceEntry manifestElement : myManifestElements) {
      out.writeUTF(manifestElement.getType());
      out.writeUTF(manifestElement.getName());
    }
    out.writeInt(myLibPackages.size());

    for (String libPackage : myLibPackages) {
      out.writeUTF(libPackage);
    }
  }
}
