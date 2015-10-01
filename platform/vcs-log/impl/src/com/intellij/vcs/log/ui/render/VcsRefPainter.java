/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.VcsLogColorManagerImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class VcsRefPainter {
  @NotNull private final VcsLogColorManager myColorManager;
  @NotNull private final TextLabelPainter myTextLabelPainter;

  public VcsRefPainter(@NotNull VcsLogColorManager colorManager, boolean paintRoot) {
    myColorManager = colorManager;
    myTextLabelPainter = TextLabelPainter.createPainter(paintRoot && colorManager.isMultipleRoots());
  }

  public void paint(@NotNull VcsRef reference, @NotNull Graphics g, int paddingX, int paddingY) {
    myTextLabelPainter.paint((Graphics2D)g, reference.getName(), paddingX, paddingY, reference.getType().getBackgroundColor(),
                             VcsLogColorManagerImpl.getIndicatorColor(myColorManager.getRootColor(reference.getRoot())));
  }

  public Rectangle paint(@NotNull String text,
                         @NotNull Graphics g,
                         int paddingX,
                         int paddingY,
                         @NotNull Color color,
                         @NotNull Color flagColor) {
    return myTextLabelPainter.paint((Graphics2D)g, text, paddingX, paddingY, color, flagColor);
  }

  public Dimension getSize(@NotNull VcsRef reference, @NotNull JComponent component) {
    return getSize(reference.getName(), component);
  }

  public Dimension getSize(@NotNull String referenceName, @NotNull JComponent component) {
    return myTextLabelPainter.calculateSize(referenceName, component.getFontMetrics(TextLabelPainter.getFont()));
  }

  public int getHeight(@NotNull JComponent component) {
    return myTextLabelPainter.calculateSize("", component.getFontMetrics(TextLabelPainter.getFont())).height;
  }
}
