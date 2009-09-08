/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public abstract class SdkHomeSettings implements PersistentStateComponent<SdkHomeConfigurable.SdkHomeBean> {
  private SdkHomeConfigurable.SdkHomeBean mySdkPath;
  private volatile VirtualFile mySdkHome;
  private volatile List<VirtualFile> myClassRoots;

  public SdkHomeConfigurable.SdkHomeBean getState() {
    return mySdkPath;
  }

  public void loadState(SdkHomeConfigurable.SdkHomeBean state) {
    mySdkPath = state;
    myClassRoots = null;
    mySdkHome = null;
  }

  private synchronized void calculateRoots() {
    if (myClassRoots != null) {
      return;
    }
    mySdkHome = calcHome(mySdkPath);
    myClassRoots = calcRoots(mySdkHome);
  }

  @Nullable
  private static VirtualFile calcHome(final SdkHomeConfigurable.SdkHomeBean state) {
    if (state == null) {
      return null;
    }

    @SuppressWarnings({"NonPrivateFieldAccessedInSynchronizedContext"}) final String sdk_home = state.SDK_HOME;
    if (StringUtil.isEmpty(sdk_home)) {
      return null;
    }

    return LocalFileSystem.getInstance().findFileByPath(sdk_home);
  }

  @Nullable
  public VirtualFile getSdkHome() {
    if (myClassRoots == null) {
      calculateRoots();
    }
    return mySdkHome;
  }

  public List<VirtualFile> getClassRoots() {
    if (myClassRoots == null) {
      calculateRoots();
    }
    return myClassRoots;
  }

  private static List<VirtualFile> calcRoots(@Nullable VirtualFile home) {
    if (home == null) {
      return Collections.emptyList();
    }

    final VirtualFile lib = home.findChild("lib");
    if (lib == null) {
      return Collections.emptyList();
    }

    final ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    for (VirtualFile file : lib.getChildren()) {
      if ("jar".equals(file.getExtension())) {
        ContainerUtil.addIfNotNull(JarFileSystem.getInstance().getJarRootForLocalFile(file), result);
      }
    }
    return result;
  }
}
