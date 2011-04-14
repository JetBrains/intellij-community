/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.compiler;

import com.android.sdklib.IAndroidTarget;
import com.intellij.openapi.compiler.ValidityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.Nullable;

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

    VirtualFile manifestFile = AndroidRootUtil.getManifestFileForCompiler(facet);
    if (manifestFile != null) {
      myResourceTimestamps.put(manifestFile.getPath(), manifestFile.getTimeStamp());
    }
    VirtualFile resourcesDir = getResourcesDir(module, facet);
    if (resourcesDir != null) {
      collectFiles(resourcesDir);
    }
    for (AndroidFacet depFacet : AndroidUtils.getAllAndroidDependencies(module, true)) {
      VirtualFile depManifest = AndroidRootUtil.getManifestFile(depFacet.getModule());
      if (depManifest != null) {
        myResourceTimestamps.put(depManifest.getPath(), depManifest.getTimeStamp());
      }
      VirtualFile depResDir = getResourcesDir(depFacet.getModule(), depFacet);
      if (depResDir != null) {
        collectFiles(depResDir);
      }
    }
    VirtualFile assetsDir = AndroidRootUtil.getAssetsDir(module);
    if (assetsDir != null) {
      collectFiles(assetsDir);
    }
  }

  @Nullable
  private static VirtualFile getResourcesDir(Module module, AndroidFacet facet) {
    VirtualFile dir = AndroidAptCompiler.getResourceDirForApkCompiler(module, facet);
    if (dir != null) {
      VirtualFile parent = dir.getParent();
      if ("combined-resources".equals(parent.getName())) {
        return dir;
      }
    }
    return AndroidRootUtil.getResourceDir(module);
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
