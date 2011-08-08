/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.contentAnnotation;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.RichTextItem;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 8/3/11
 * Time: 12:50 PM
 */
public interface VcsContentAnnotation {
  @Nullable
  VcsRevisionNumber fileRecentlyChanged(final VirtualFile vf);

  boolean intervalRecentlyChanged(VirtualFile file, final TextRange lineInterval, VcsRevisionNumber currentRevisionNumber);

  class Details {
    private final boolean myLineChanged;
    // meaningful enclosing structure
    private final boolean myMethodChanged;
    private final boolean myFileChanged;
    @Nullable
    private final List<RichTextItem> myDetails;

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
