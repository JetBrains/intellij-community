// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.gant;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.NonClasspathClassFinder;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class GantClassFinder extends NonClasspathClassFinder {
  public GantClassFinder(@NotNull Project project) {
    super(project);
  }

  @Override
  protected List<VirtualFile> calcClassRoots() {
    return GantSettings.getInstance(myProject).getClassRoots();
  }
}
