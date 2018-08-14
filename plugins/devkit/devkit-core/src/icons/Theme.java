// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.icons;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.thumbnail.ThumbnailView;
import org.intellij.images.thumbnail.actions.ThemeFilter;

public enum Theme implements ThemeFilter{
  WHITE(null, "Default") {
    @Override
    public boolean accepts(VirtualFile fileName) {
      String nameWithoutExtension = FileUtil.getNameWithoutExtension(fileName.getName());
      for (Theme theme : values()) {
        String extension = theme.getExtension();
        if (extension != null && nameWithoutExtension.endsWith(extension)) {
          return false;
        }
      }

      return true;
    }
  },
  HIGH_DPI_WHITE("@2x", "Default HiDPI") {
    @Override
    public boolean accepts(VirtualFile fileName) {
      return FileUtil.getNameWithoutExtension(fileName.getName()).endsWith(getExtension());
    }
  },
  DARK("_dark", "Darcula") {
    @Override
    public boolean accepts(VirtualFile file) {
      String name = FileUtil.getNameWithoutExtension(file.getName());
      if (name.endsWith(getExtension()) && !name.endsWith(HIGH_DPI_DARK.getExtension())) {
        return true;
      }
      VirtualFile parent = file.getParent();
      if (parent != null && parent.findChild(name + "_dark.png") != null) {
        return false;
      }
      return WHITE.accepts(file);
    }
  },
  HIGH_DPI_DARK("@2x_dark", "Darcula HiDPI") {
    @Override
    public boolean accepts(VirtualFile file) {
      String name = FileUtil.getNameWithoutExtension(file.getName());
      if (name.endsWith(getExtension())) {
        return true;
      }
      VirtualFile parent = file.getParent();
      if (parent != null && parent.findChild(name + "_dark.png") != null) {
        return false;
      }
      return HIGH_DPI_WHITE.accepts(file);
    }
  };
  private final String myExtension;
  private final String myDisplayName;

  Theme(String extension, String displayName) {
    myExtension = extension;
    myDisplayName = displayName;
  }

  public String getExtension() {
    return myExtension;
  }

  @Override
  public String getDisplayName() {
    return myDisplayName;
  }

  @Override
  public boolean isApplicableToProject(Project project) {
    return true;
  }

  @Override
  public void setFilter(ThumbnailView view) {
    view.setFilter(this);
  }

}
