// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.images;

import com.intellij.openapi.project.IntelliJProjectUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.thumbnail.ThumbnailView;
import org.intellij.images.thumbnail.actions.ThemeFilter;
import org.jetbrains.idea.devkit.util.PsiUtil;

public abstract class AbstractThemeFilter implements ThemeFilter {
  private final Theme myTheme;

  protected AbstractThemeFilter(Theme theme) {
    myTheme = theme;
  }

  @Override
  public String getDisplayName() {
    return myTheme.getDisplayName();
  }

  @Override
  public boolean accepts(VirtualFile file) {
    return myTheme.accepts(file);
  }

  @Override
  public boolean isApplicableToProject(Project project) {
    return IntelliJProjectUtil.isIntelliJPlatformProject(project) || PsiUtil.isPluginProject(project);
  }

  @Override
  public void setFilter(ThumbnailView view) {
    view.setFilter(this);
  }
}
