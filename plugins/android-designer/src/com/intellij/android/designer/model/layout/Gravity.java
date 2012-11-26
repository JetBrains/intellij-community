/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.android.designer.model.layout;

import com.android.SdkConstants;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import icons.AndroidDesignerIcons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public enum Gravity {
  left, right, center, top, bottom;

  public static Icon ICON = AndroidDesignerIcons.Gravity;

  public static final int NONE = 0;
  public static final int TOP = 1 << 0;
  public static final int BOTTOM = 1 << 1;
  public static final int LEFT = 1 << 2;
  public static final int RIGHT = 1 << 3;
  public static final int CENTER_VERTICAL = 1 << 4;
  public static final int FILL_VERTICAL = 1 << 5;
  public static final int CENTER_HORIZONTAL = 1 << 6;
  public static final int FILL_HORIZONTAL = 1 << 7;
  public static final int CENTER = CENTER_HORIZONTAL | CENTER_VERTICAL;
  public static final int FILL = FILL_HORIZONTAL | FILL_VERTICAL;
  public static final int CLIP_VERTICAL = 1 << 8;
  public static final int CLIP_HORIZONTAL = 1 << 9;
  public static final int START = (1 << 10) | LEFT;
  public static final int END = (1 << 11) | RIGHT;

  @Nullable
  public static String getValue(@Nullable Gravity horizontal, @Nullable Gravity vertical) {
    StringBuilder gravity = new StringBuilder();

    if (horizontal == center && vertical == center) {
      gravity.append("center");
    }
    else {
      if (horizontal == left) {
        gravity.append("left");
      }
      else if (horizontal == center) {
        gravity.append("center_horizontal");
      }
      else if (horizontal == right) {
        gravity.append("right");
      }

      if (vertical == top) {
        if (gravity.length() > 0) {
          gravity.append("|");
        }
        gravity.append("top");
      }
      else if (vertical == center) {
        if (gravity.length() > 0) {
          gravity.append("|");
        }
        gravity.append("center_vertical");
      }
      else if (vertical == bottom) {
        if (gravity.length() > 0) {
          gravity.append("|");
        }
        gravity.append("bottom");
      }
    }

    return gravity.length() == 0 ? null : gravity.toString();
  }

  public static int getFlags(RadComponent component) {
    String value = ((RadViewComponent)component).getTag().getAttributeValue("layout_gravity", SdkConstants.NS_RESOURCES);
    int flags = NONE;

    if (!StringUtil.isEmpty(value)) {
      for (String option : StringUtil.split(value, "|")) {
        option = option.trim();

        if ("top".equals(option)) {
          flags |= TOP;
        }
        else if ("bottom".equals(option)) {
          flags |= BOTTOM;
        }
        else if ("left".equals(option)) {
          flags |= LEFT;
        }
        else if ("right".equals(option)) {
          flags |= RIGHT;
        }
        else if ("center_vertical".equals(option)) {
          flags |= CENTER_VERTICAL;
        }
        else if ("fill_vertical".equals(option)) {
          flags |= FILL_VERTICAL;
        }
        else if ("center_horizontal".equals(option)) {
          flags |= CENTER_HORIZONTAL;
        }
        else if ("fill_horizontal".equals(option)) {
          flags |= FILL_HORIZONTAL;
        }
        else if ("center".equals(option)) {
          flags |= CENTER;
        }
        else if ("fill".equals(option)) {
          flags |= FILL;
        }
        else if ("clip_vertical".equals(option)) {
          flags |= CLIP_VERTICAL;
        }
        else if ("clip_horizontal".equals(option)) {
          flags |= CLIP_HORIZONTAL;
        }
        else if ("start".equals(option)) {
          flags |= START;
        }
        else if ("end".equals(option)) {
          flags |= END;
        }
      }
    }
    return flags;
  }

  public static List<Gravity> flagToValues(int flags) {
    List<Gravity> values = new ArrayList<Gravity>();

    if ((flags & LEFT) != 0) {
      values.add(left);
    }
    if ((flags & RIGHT) != 0) {
      values.add(right);
    }
    if ((flags & TOP) != 0) {
      values.add(top);
    }
    if ((flags & BOTTOM) != 0) {
      values.add(bottom);
    }
    if ((flags & CENTER) != 0) {
      values.add(center);
    }

    return values;
  }

  public static Pair<Gravity, Gravity> getSides(RadComponent component) {
    int flags = getFlags(component);

    Gravity horizontal = left;
    if ((flags & LEFT) != 0) {
      horizontal = left;
    }
    else if ((flags & RIGHT) != 0) {
      horizontal = right;
    }
    else if ((flags & CENTER_HORIZONTAL) != 0) {
      horizontal = center;
    }

    Gravity vertical = top;
    if ((flags & TOP) != 0) {
      vertical = top;
    }
    else if ((flags & BOTTOM) != 0) {
      vertical = bottom;
    }
    else if ((flags & CENTER_VERTICAL) != 0) {
      vertical = center;
    }

    return Pair.create(horizontal, vertical);
  }
}
