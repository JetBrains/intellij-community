/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.icons;

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
    return PsiUtil.isIdeaProject(project) || PsiUtil.isPluginProject(project);
  }

  @Override
  public void setFilter(ThumbnailView view) {
    view.setFilter(this);
  }
}
