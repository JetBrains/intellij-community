package org.jetbrains.android.compiler;

import com.android.sdklib.IAndroidTarget;
import com.intellij.openapi.compiler.ValidityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidUtils;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author yole
 */
public class ResourcesValidityState implements ValidityState {
  private final Map<String, Long> myResourceTimestamps = new HashMap<String, Long>();
  private final String myAndroidTargetName;

  public ResourcesValidityState(Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      myAndroidTargetName = "";
      return;
    }

    AndroidPlatform platform = facet.getConfiguration().getAndroidPlatform();
    IAndroidTarget target = platform != null ? platform.getTarget() : null;
    myAndroidTargetName = target != null ? target.getFullName() : "";

    VirtualFile manifestFile = AndroidRootUtil.getManifestFile(module);
    if (manifestFile != null) {
      myResourceTimestamps.put(manifestFile.getPath(), manifestFile.getTimeStamp());
    }
    VirtualFile resourcesDir = AndroidAptCompiler.getResourceDirForApkCompiler(module, facet);
    if (resourcesDir != null) {
      collectFiles(resourcesDir);
    }
    for (AndroidFacet depFacet : AndroidUtils.getAndroidDependencies(module, true)) {
      VirtualFile depManifest = AndroidRootUtil.getManifestFile(depFacet.getModule());
      if (depManifest != null) {
        myResourceTimestamps.put(depManifest.getPath(), depManifest.getTimeStamp());
      }
      VirtualFile depResDir = AndroidAptCompiler.getResourceDirForApkCompiler(depFacet.getModule(), depFacet);
      if (depResDir != null) {
        collectFiles(depResDir);
      }
    }
    VirtualFile assetsDir = AndroidRootUtil.getAssetsDir(module);
    if (assetsDir != null) {
      collectFiles(assetsDir);
    }
  }

  private void collectFiles(VirtualFile resourcesDir) {
    for (VirtualFile child : resourcesDir.getChildren()) {
      if (child.isDirectory()) {
        collectFiles(child);
      }
      else {
        myResourceTimestamps.put(child.getPath(), child.getTimeStamp());
      }
    }
  }

  public ResourcesValidityState(DataInput is) throws IOException {
    int count = is.readInt();
    for (int i = 0; i < count; i++) {
      String path = is.readUTF();
      long timestamp = is.readLong();
      myResourceTimestamps.put(path, timestamp);
    }
    myAndroidTargetName = is.readUTF();
  }

  public boolean equalsTo(ValidityState otherState) {
    if (!(otherState instanceof ResourcesValidityState)) {
      return false;
    }
    ResourcesValidityState rhs = (ResourcesValidityState)otherState;
    if (!myResourceTimestamps.equals(rhs.myResourceTimestamps)) {
      return false;
    }
    return true;
  }

  public void save(DataOutput os) throws IOException {
    os.writeInt(myResourceTimestamps.size());
    for (Map.Entry<String, Long> e : myResourceTimestamps.entrySet()) {
      os.writeUTF(e.getKey());
      os.writeLong(e.getValue());
    }
    os.writeUTF(myAndroidTargetName);
  }
}
