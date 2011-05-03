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
package com.intellij.openapi.diff.impl.dir;

import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.util.ui.EmptyIcon;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public enum DirDiffOperation {
  COPY_TO, COPY_FROM, MERGE, EQUAL, NONE;

  public Icon getIcon() {
    switch (this) {
      case COPY_TO:   return IconLoader.getIcon("/vcs/arrow_right.png");
      case COPY_FROM: return IconLoader.getIcon("/vcs/arrow_left.png");
      case MERGE:     return IconLoader.getIcon("/vcs/not_equal.png");
      case EQUAL:     return IconLoader.getIcon("/vcs/equal.png");
      case NONE:
    }
    return EmptyIcon.create(16);
  }

  public Color getTextColor() {
    switch (this) {
      case COPY_TO:
      case COPY_FROM:
        return FileStatus.COLOR_ADDED;
      case MERGE:
        return FileStatus.COLOR_MODIFIED;
      case EQUAL:
      case NONE:
    }
    return Color.BLACK;
  }
}
