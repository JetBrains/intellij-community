// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public abstract class SdkHomeSettings implements PersistentStateComponent<SdkHomeBean> {
  private final PsiModificationTrackerImpl myTracker;
  private SdkHomeBean mySdkHome;

  protected SdkHomeSettings(Project project) {
    myTracker = (PsiModificationTrackerImpl)PsiManager.getInstance(project).getModificationTracker();
  }

  @Override
  public SdkHomeBean getState() {
    return mySdkHome;
  }

  @Override
  public void loadState(@NotNull SdkHomeBean state) {
    SdkHomeBean oldState = mySdkHome;
    mySdkHome = state;
    if (oldState != null) {
      myTracker.incCounter();
    }
  }

  @Nullable
  private static VirtualFile calcHome(final SdkHomeBean state) {
    if (state == null) {
      return null;
    }

    @SuppressWarnings({"NonPrivateFieldAccessedInSynchronizedContext"}) final String sdk_home = state.SDK_HOME;
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