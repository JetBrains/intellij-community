// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.icons;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.thumbnail.ThumbnailView;
import org.intellij.images.thumbnail.actions.ThemeFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.devkit.DevKitBundle;

import java.util.function.Supplier;

public enum Theme implements ThemeFilter{
  WHITE(null, DevKitBundle.messagePointer("action.default.theme.text")) {
    @Override
    public boolean accepts(VirtualFile fileName) {
      String nameWithoutExtension = FileUtilRt.getNameWithoutExtension(fileName.getName());
      for (Theme theme : values()) {
        String extension = theme.getExtension();
        if (extension != null && nameWithoutExtension.endsWith(extension)) {
          return false;
        }
      }

      return true;
    }
  },
  HIGH_DPI_WHITE("@2x", DevKitBundle.messagePointer("action.default.hidpi.theme.text")) {
    @Override
    public boolean accepts(VirtualFile fileName) {
      return FileUtilRt.getNameWithoutExtension(fileName.getName()).endsWith(getExtension());
    }
  },
  DARK("_dark", DevKitBundle.messagePointer("action.darcula.theme.text")) {
    @Override
    public boolean accepts(VirtualFile file) {
      String name = FileUtilRt.getNameWithoutExtension(file.getName());
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
  HIGH_DPI_DARK("@2x_dark", DevKitBundle.messagePointer("action.darcula.hidpi.theme.text")) {
    @Override
    public boolean accepts(VirtualFile file) {
      String name = FileUtilRt.getNameWithoutExtension(file.getName());
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
  private final Supplier<@NlsActions.ActionText String> myDisplayNameSupplier;

  Theme(@NonNls String extension, Supplier<@NlsActions.ActionText String> displayNameSupplier) {
    myExtension = extension;
    myDisplayNameSupplier = displayNameSupplier;
  }

  public @NonNls String getExtension() {
    return myExtension;
  }

  @Override
  public String getDisplayName() {
    return myDisplayNameSupplier.get();
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
