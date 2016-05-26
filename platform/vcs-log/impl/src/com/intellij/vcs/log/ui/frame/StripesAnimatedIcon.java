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
package com.intellij.vcs.log.ui.frame;

import com.intellij.util.IconUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.JBUI;
import icons.VcsLogIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;

public class StripesAnimatedIcon implements Icon {
  private static final Icon STRIPES = VcsLogIcons.Stripes;
  private static final int TRANSLATE = 1;

  @NotNull
  private final JComponent myReferenceComponent;
  private final int myShift;
  @NotNull
  private final Icon myCropIcon;

  public StripesAnimatedIcon(@NotNull JComponent component, int shift) {
    myReferenceComponent = component;
    myShift = shift;

    myCropIcon = IconUtil.cropIcon(STRIPES, new Rectangle(STRIPES.getIconWidth() - myShift, 0, myShift, STRIPES.getIconHeight()));
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    myCropIcon.paintIcon(c, g, x, y);
    int shift = myShift;
    while (shift < getIconWidth()) {
      STRIPES.paintIcon(c, g, x + shift, y);
      shift += STRIPES.getIconWidth();
    }
  }

  @Override
  public int getIconWidth() {
    return myReferenceComponent.getWidth();
  }

  @Override
  public int getIconHeight() {
    return STRIPES.getIconHeight();
  }

  @NotNull
  public static AsyncProcessIcon generateIcon(@NotNull JComponent component) {
    List<Icon> result = ContainerUtil.newArrayList();
    for (int i = 0; i < STRIPES.getIconWidth(); i += JBUI.scale(TRANSLATE)) {
      result.add(new StripesAnimatedIcon(component, i));
    }
    AsyncProcessIcon icon = new AsyncProcessIcon("ProgressWithStripes", result.toArray(new Icon[result.size()]), result.get(0)) {
      @Override
      public Dimension getPreferredSize() {
        return new Dimension(component.getWidth(), STRIPES.getIconHeight());
      }
    };
    component.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        super.componentResized(e);
        icon.revalidate();
      }
    });
    return icon;
  }
}
