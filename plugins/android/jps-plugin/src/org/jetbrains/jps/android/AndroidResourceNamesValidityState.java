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
public class AndroidResourceNamesValidityState implements ValidityState {
  private final Set<ResourceEntry> myResourcesSet;
  private final String myPackageName;

  public AndroidResourceNamesValidityState(@NotNull Collection<ResourceEntry> resourcesSet, @NotNull String packageName) {
    myResourcesSet = new HashSet<ResourceEntry>(resourcesSet);
    myPackageName = packageName;
  }

  public AndroidResourceNamesValidityState(@NotNull DataInput in) throws IOException {
    myPackageName = in.readUTF();
    myResourcesSet = new HashSet<ResourceEntry>();

    final int resourceCount = in.readInt();

    for (int i = 0; i < resourceCount; i++) {
      final String resType = in.readUTF();
      final String resName = in.readUTF();
      myResourcesSet.add(new ResourceEntry(resType, resName));
    }
  }

  @Override
  public boolean equalsTo(ValidityState otherState) {
    if (!(otherState instanceof AndroidResourceNamesValidityState)) {
      return false;
    }
    final AndroidResourceNamesValidityState otherAndroidState = (AndroidResourceNamesValidityState)otherState;
    return otherAndroidState.myPackageName.equals(myPackageName) &&
           otherAndroidState.myResourcesSet.equals(myResourcesSet);
  }

  @Override
  public void save(DataOutput out) throws IOException {
    out.writeUTF(myPackageName);
    out.writeInt(myResourcesSet.size());

    for (ResourceEntry resourceEntry : myResourcesSet) {
      out.writeUTF(resourceEntry.getType());
      out.writeUTF(resourceEntry.getName());
    }
  }
}
