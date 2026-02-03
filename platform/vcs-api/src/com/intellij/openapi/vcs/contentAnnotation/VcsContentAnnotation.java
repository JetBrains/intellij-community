// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.contentAnnotation;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.RichTextItem;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface VcsContentAnnotation {
  @Nullable
  VcsRevisionNumber fileRecentlyChanged(final VirtualFile vf);

  boolean intervalRecentlyChanged(VirtualFile file, final TextRange lineInterval, VcsRevisionNumber currentRevisionNumber);

  class Details {
    private final boolean myLineChanged;
    // meaningful enclosing structure
    private final boolean myMethodChanged;
    private final boolean myFileChanged;
    private final @Nullable List<RichTextItem> myDetails;

    public Details(boolean lineChanged, boolean methodChanged, boolean fileChanged, List<RichTextItem> details) {
      myLineChanged = lineChanged;
      myMethodChanged = methodChanged;
      myFileChanged = fileChanged;
      myDetails = details;
    }

    public boolean isLineChanged() {
      return myLineChanged;
    }

    public boolean isMethodChanged() {
      return myMethodChanged;
    }

    public boolean isFileChanged() {
      return myFileChanged;
    }
  }
}
