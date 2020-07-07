// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util;

import com.intellij.openapi.components.PersistentStateComponentWithModificationTracker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public abstract class SdkHomeSettings implements PersistentStateComponentWithModificationTracker<SdkHomeBean> {
  private final PsiManager myManager;
  private SdkHomeBean mySdkHome = null;

  protected SdkHomeSettings(@NotNull Project project) {
    myManager = PsiManager.getInstance(project);
  }

  @Override
  public long getStateModificationCount() {
    SdkHomeBean sdkHome = mySdkHome;
    return sdkHome == null ? 0 : sdkHome.getModificationCount();
  }

  @Override
  public SdkHomeBean getState() {
    return mySdkHome;
  }

  @Override
  public void loadState(@NotNull SdkHomeBean state) {
    SdkHomeBean oldState = mySdkHome;
    mySdkHome = state;
    // do not increment on a first load
    if (oldState != null && !StringUtil.equals(oldState.getSdkHome(), state.getSdkHome())) {
      myManager.dropPsiCaches();
    }
  }

  @Nullable
  private static VirtualFile calcHome(@Nullable SdkHomeBean state) {
    if (state == null) {
      return null;
    }

    final String sdk_home = state.getSdkHome();
    if (StringUtil.isEmpty(sdk_home)) {
      return null;
    }

    return StandardFileSystems.local().findFileByPath(sdk_home);
  }

  @Nullable
  public VirtualFile getSdkHome() {
    return calcHome(mySdkHome);
  }

  public List<VirtualFile> getClassRoots() {
    return calcRoots(getSdkHome());
  }

  private static List<VirtualFile> calcRoots(@Nullable VirtualFile home) {
    if (home == null) return Collections.emptyList();

    VirtualFile lib = home.findChild("lib");
    if (lib == null) return Collections.emptyList();

    List<VirtualFile> result = new ArrayList<>();
    for (VirtualFile file : lib.getChildren()) {
      if ("jar".equals(file.getExtension())) {
        ContainerUtil.addIfNotNull(result, JarFileSystem.getInstance().getRootByLocal(file));
      }
    }
    return result;
  }
}