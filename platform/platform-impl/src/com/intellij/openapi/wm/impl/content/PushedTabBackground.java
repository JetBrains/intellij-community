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

/*
 * @author max
 */
package com.intellij.openapi.wm.impl.content;

import com.intellij.icons.AllIcons;
import com.intellij.ui.Gray;

import javax.swing.*;
import java.awt.*;

public class PushedTabBackground {
  private static final Icon ourLeftRound = AllIcons.General.Tab_grey_left;
  private static final Icon ourRightRound = AllIcons.General.Tab_grey_right;

  private static final Icon ourLeftWhiteRound = AllIcons.General.Tab_white_left;
  private static final Icon ourRightWhiteRound = AllIcons.General.Tab_white_right;

  private static final Icon ourRightStraight = AllIcons.General.Tab_grey_right_inner;
  private static final Icon ourLeftStraight = AllIcons.General.Tab_grey_left_inner;
  
  private static final Icon ourMiddle = AllIcons.General.Tab_grey_bckgrnd;
  private static final Icon ourMiddleWhite = AllIcons.General.Tab_white_center;



  public static void paintPushed(Component c, Graphics2D g, int x, int width, int y, boolean isFirst, boolean isLast) {
    int cur = x;
    
    if (isFirst) {
      ourLeftRound.paintIcon(c, g, cur, y);
      cur += ourLeftRound.getIconWidth();
    }
    else {
      ourLeftStraight.paintIcon(c, g, cur, y);
      cur += ourLeftStraight.getIconWidth();
    }

    final int stopPlain = x + width - ourRightStraight.getIconWidth();
    while (cur < stopPlain) {
      ourMiddle.paintIcon(c, g, cur, y);
      cur += ourMiddle.getIconWidth();
    }

    g.setColor(Gray._110);

    if (isLast) {
      ourRightRound.paintIcon(c, g, cur, y);
    }
    else {
      ourRightStraight.paintIcon(c, g, cur, y);
    }
  }

  public static void paintPulled(Component c, Graphics2D g, int x, int width, int y, boolean isFirst, boolean isLast) {

    int cur = x;

    if (isFirst) {
      ourLeftWhiteRound.paintIcon(c, g, cur, y);
      cur += ourLeftRound.getIconWidth();
    }

    final int stopPlain = x + width - (isLast ? ourRightWhiteRound.getIconWidth() : 0);
    while (cur < stopPlain) {
      ourMiddleWhite.paintIcon(c, g, cur, y);
      cur += ourMiddleWhite.getIconWidth();
    }

    if (isLast) {
      ourRightWhiteRound.paintIcon(c, g, cur, y);
    }

  }

}
