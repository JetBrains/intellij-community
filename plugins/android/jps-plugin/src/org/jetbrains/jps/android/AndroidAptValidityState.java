package org.jetbrains.jps.android;

import com.intellij.util.containers.HashSet;
import org.jetbrains.android.util.ResourceEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.storage.ValidityState;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidAptValidityState implements ValidityState {
  private final Set<ResourceEntry> myResources;
  private final Set<ResourceEntry> myManifestElements;
  private final String myPackageName;
  private final Set<String> myLibPackages;

  public AndroidAptValidityState(@NotNull Collection<ResourceEntry> resources,
                                 @NotNull Collection<ResourceEntry> manifestElements,
                                 @NotNull Collection<String> libPackages,
                                 @NotNull String packageName) {
    myResources = new HashSet<ResourceEntry>(resources);
    myManifestElements = new HashSet<ResourceEntry>(manifestElements);
    myLibPackages = new HashSet<String>(libPackages);
    myPackageName = packageName;
  }

  public AndroidAptValidityState(@NotNull DataInput in) throws IOException {
    myPackageName = in.readUTF();

    final int resourceCount = in.readInt();
    myResources = new HashSet<ResourceEntry>(resourceCount);

    for (int i = 0; i < resourceCount; i++) {
      final String resType = in.readUTF();
      final String resName = in.readUTF();
      myResources.add(new ResourceEntry(resType, resName));
    }

    final int manifestElementCount = in.readInt();
    myManifestElements = new HashSet<ResourceEntry>(manifestElementCount);

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

    for (ResourceEntry resourceEntry : myResources) {
      out.writeUTF(resourceEntry.getType());
      out.writeUTF(resourceEntry.getName());
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
