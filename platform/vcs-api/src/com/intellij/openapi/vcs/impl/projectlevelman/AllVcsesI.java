// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl.projectlevelman;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.impl.VcsDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Irina.Chernushina
 */
public interface AllVcsesI {
  void registerManually(@NotNull AbstractVcs vcs);
  void unregisterManually(@NotNull AbstractVcs vcs);
  AbstractVcs getByName(String name);
  @Nullable
  VcsDescriptor getDescriptor(final String name);
  VcsDescriptor[] getAll();
  boolean isEmpty();

  static AllVcsesI getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, AllVcsesI.class);
  }
}
