package org.jetbrains.android.compiler;

import com.android.sdklib.IAndroidTarget;
import com.intellij.openapi.compiler.ValidityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class ResourceNamesValidityState implements ValidityState {
  private final Set<ResourceEntry> myResourcesSet = new HashSet<ResourceEntry>();
  private final String myAndroidTargetHashString;
  private final long myManifestTimestamp;
  
  public ResourceNamesValidityState(@NotNull Module module) {
    final AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null;

    final AndroidPlatform platform = facet.getConfiguration().getAndroidPlatform();
    final IAndroidTarget target = platform != null ? platform.getTarget() : null;
    myAndroidTargetHashString = target != null ? target.hashString() : "";

    final VirtualFile manifestFile = AndroidRootUtil.getManifestFile(facet.getModule());
    myManifestTimestamp = manifestFile != null ? manifestFile.getModificationStamp() : -1;

    AndroidCompileUtil.collectAllResources(facet, myResourcesSet);
  }
  
  public ResourceNamesValidityState(@NotNull DataInput in) throws IOException {
    myAndroidTargetHashString = in.readUTF();
    myManifestTimestamp = in.readLong();
    
    final int resourcesCount = in.readInt();
    
    for (int i = 0; i < resourcesCount; i++) {
      final String type = in.readUTF();
      final String name = in.readUTF();
      myResourcesSet.add(new ResourceEntry(type, name));
    }
  }

  @Override
  public boolean equalsTo(ValidityState otherState) {
    if (!(otherState instanceof ResourceNamesValidityState)) {
      return false;
    }
    
    final ResourceNamesValidityState other = (ResourceNamesValidityState)otherState;

    return other.myAndroidTargetHashString.equals(myAndroidTargetHashString) &&
           other.myManifestTimestamp == myManifestTimestamp &&
           other.myResourcesSet.equals(myResourcesSet);
  }

  @Override
  public void save(DataOutput out) throws IOException {
    out.writeUTF(myAndroidTargetHashString);
    out.writeLong(myManifestTimestamp);
    
    out.writeInt(myResourcesSet.size());
    
    for (ResourceEntry resourceEntry : myResourcesSet) {
      out.writeUTF(resourceEntry.getType());
      out.writeUTF(resourceEntry.getName());
    }
  }
}
