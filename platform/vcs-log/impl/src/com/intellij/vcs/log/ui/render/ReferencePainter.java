/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.render;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogRefManager;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.data.VcsLogData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

public interface ReferencePainter {
  void customizePainter(@NotNull JComponent component,
                        @NotNull Collection<VcsRef> references,
                        @NotNull Color background,
                        @NotNull Color foreground,
                        boolean isSelected,
                        int availableWidth);

  void paint(@NotNull Graphics2D g2, int x, int y, int height);

  Dimension getSize();

  boolean isLeftAligned();

  default Font getReferenceFont() {
    return RectanglePainter.getFont();
  }

  @Nullable
  static VcsLogRefManager getRefManager(@NotNull VcsLogData logData, @NotNull Collection<VcsRef> references) {
    if (!references.isEmpty()) {
      VirtualFile root = ObjectUtils.assertNotNull(ContainerUtil.getFirstItem(references)).getRoot();
      return logData.getLogProvider(root).getReferenceManager();
    }
    else {
      return null;
    }
  }
}
