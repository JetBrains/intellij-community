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
package com.intellij.android.designer.designSurface.layout;

import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.layout.Gravity;
import com.intellij.android.designer.model.layout.RadFrameLayout;
import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.feedbacks.AlphaFeedback;
import com.intellij.designer.designSurface.feedbacks.TextFeedback;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.LightColors;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class LinearLayoutOperation extends FlowBaseOperation {
  private GravityFeedback myFeedback;
  private TextFeedback myTextFeedback;
  private List<Gravity> myExcludes;
  private Gravity myGravity;

  public LinearLayoutOperation(RadViewComponent container, OperationContext context, boolean horizontal) {
    super(container, context, horizontal);

    if (context.isMove()) {
      myExcludes = new ArrayList<Gravity>();
      for (RadComponent component : context.getComponents()) {
        String fill;
        if (myHorizontal) {
          fill = ((RadViewComponent)component).getTag().getAttributeValue("android:layout_height");
        }
        else {
          fill = ((RadViewComponent)component).getTag().getAttributeValue("android:layout_width");
        }

        if ("match_parent".equals(fill) || "fill_parent".equals(fill)) {
          myExcludes.add(null);
        }
        else {
          Pair<Gravity, Gravity> gravity = RadFrameLayout.gravity(component);
          myExcludes.add(myHorizontal ? gravity.second : gravity.first);
        }
      }
    }
  }

  @Override
  protected void createFeedback() {
    super.createFeedback();
    if (myFeedback == null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();

      myFeedback = new GravityFeedback();
      layer.add(myFeedback);

      myTextFeedback = new TextFeedback();
      myTextFeedback.setBorder(IdeBorderFactory.createEmptyBorder(0, 3, 2, 0));
      layer.add(myTextFeedback);

      layer.repaint();
    }
  }

  @Override
  public void showFeedback() {
    super.showFeedback();

    if (myChildTarget == null) {
      myFeedback.setBounds(myBounds);
    }
    else if (myHorizontal) {
      Rectangle childBounds = myChildTarget.getBounds(myContext.getArea().getFeedbackLayer());
      myFeedback.setBounds(childBounds.x, myBounds.y, childBounds.width, myBounds.height);
    }
    else {
      Rectangle childBounds = myChildTarget.getBounds(myContext.getArea().getFeedbackLayer());
      myFeedback.setBounds(myBounds.x, childBounds.y, myBounds.width, childBounds.height);
    }

    Point location = myContext.getLocation();
    Gravity gravity = myHorizontal ? calculateVertical(myBounds, location) : calculateHorizontal(myBounds, location);

    myFeedback.setGravity(gravity);

    myTextFeedback.clear();
    myTextFeedback.bold(gravity == null ? "fill_parent" : gravity.name());
    myTextFeedback.centerTop(myBounds);

    myGravity = gravity;
  }

  @Override
  public void eraseFeedback() {
    super.eraseFeedback();
    if (myFeedback != null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
      layer.remove(myFeedback);
      layer.remove(myTextFeedback);
      layer.repaint();
      myFeedback = null;
      myTextFeedback = null;
    }
  }

  @Nullable
  private static Gravity calculateHorizontal(Rectangle bounds, Point location) {
    Gravity horizontal = null;
    double width = bounds.width / 4.0;
    double left = bounds.x + width;
    double center = bounds.x + 2 * width;
    double right = bounds.x + 3 * width;

    if (location.x < left) {
      horizontal = Gravity.left;
    }
    else if (left < location.x && location.x < center) {
      horizontal = Gravity.center;
    }
    else if (center < location.x && location.x < right) {
      horizontal = Gravity.right;
    }

    return horizontal;
  }

  @Nullable
  private static Gravity calculateVertical(Rectangle bounds, Point location) {
    Gravity vertical = null;
    double height = bounds.height / 4.0;
    double top = bounds.y + height;
    double center = bounds.y + 2 * height;
    double bottom = bounds.y + 3 * height;

    if (location.y < top) {
      vertical = Gravity.top;
    }
    else if (top < location.y && location.y < center) {
      vertical = Gravity.center;
    }
    else if (center < location.y && location.y < bottom) {
      vertical = Gravity.bottom;
    }

    return vertical;
  }

  @Override
  public boolean canExecute() {
    if (myContext.isMove()) {
      return !isExclude(myGravity);
    }
    return true;
  }

  private boolean isExclude(Gravity gravity) {
    int index = myComponents.indexOf(myChildTarget);
    return index != -1 && gravity == myExcludes.get(index);
  }

  @Override
  public void execute() throws Exception {
    super.execute();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        if (myGravity == null) {
          for (RadComponent component : myComponents) {
            XmlTag tag = ((RadViewComponent)component).getTag();

            XmlAttribute attribute = tag.getAttribute("android:layout_gravity");
            if (attribute != null) {
              attribute.delete();
            }

            tag.setAttribute(myHorizontal ? "android:layout_height" : "android:layout_width", "fill_parent");
          }
        }
        else {
          for (RadComponent component : myComponents) {
            XmlTag tag = ((RadViewComponent)component).getTag();

            XmlAttribute attribute = tag.getAttribute(myHorizontal ? "android:layout_height" : "android:layout_width");
            if (attribute != null && ("match_parent".equals(attribute.getValue()) || "fill_parent".equals(attribute.getValue()))) {
              attribute.setValue("wrap_content");
            }

            tag.setAttribute("android:layout_gravity", myHorizontal ?
                                                       Gravity.getValue(null, myGravity) : Gravity.getValue(myGravity, null));
          }
        }
      }
    });
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Feedback
  //
  //////////////////////////////////////////////////////////////////////////////////////////
  public static final Gravity[] HORIZONTALS = {Gravity.left, Gravity.center, Gravity.right, null};
  public static final Gravity[] VERTICALS = {Gravity.top, Gravity.center, Gravity.bottom, null};

  private class GravityFeedback extends AlphaFeedback {
    private Gravity myGravity;

    public GravityFeedback() {
      super(BorderStaticDecorator.COLOR);
    }

    public void setGravity(Gravity gravity) {
      myGravity = gravity;
      repaint();
    }

    @Override
    protected void paintOther1(Graphics2D g2d) {
      if (myHorizontal) {
        for (Gravity gravity : VERTICALS) {
          if (!myContext.isMove() || !isExclude(gravity)) {
            paintHorizontalCell(g2d, gravity, false);
          }
        }
      }
      else {
        for (Gravity gravity : HORIZONTALS) {
          if (!myContext.isMove() || !isExclude(gravity)) {
            paintVerticalCell(g2d, gravity, false);
          }
        }
      }
    }

    @Override
    protected void paintOther2(Graphics2D g2d) {
      if (myHorizontal) {
        for (Gravity gravity : VERTICALS) {
          if ((!myContext.isMove() || !isExclude(gravity)) && paintHorizontalCell(g2d, gravity, true)) {
            break;
          }
        }
      }
      else {
        for (Gravity gravity : HORIZONTALS) {
          if ((!myContext.isMove() || !isExclude(gravity)) && paintVerticalCell(g2d, gravity, true)) {
            break;
          }
        }
      }
    }

    private boolean paintHorizontalCell(Graphics2D g2d, Gravity gravity, boolean selection) {
      int x = 0;
      int width = getWidth();

      int y = 0;
      int height = (getHeight() - 3) / 4;
      if (gravity == Gravity.center) {
        y = height + 1;
      }
      else if (gravity == Gravity.bottom) {
        y = 2 * height + 2;
      }
      else if (gravity == null) {
        y = getHeight() - height;
      }

      int hSpace = Math.min(5, Math.max(1, getWidth() / 30));
      if (hSpace > 1) {
        x += hSpace;
        width -= 2 * hSpace;
      }

      int vSpace = Math.min(5, Math.max(1, getHeight() / 30));
      if (vSpace > 1) {
        y += vSpace;
        height -= 2 * vSpace;
      }

      if (selection) {
        if (myGravity == gravity) {
          Color oldColor = g2d.getColor();
          g2d.setColor(LightColors.YELLOW);
          g2d.fillRect(x, y, width, height);
          g2d.setColor(oldColor);

          return true;
        }
      }
      else {
        g2d.fillRect(x, y, width, height);
      }

      return false;
    }

    private boolean paintVerticalCell(Graphics2D g2d, Gravity gravity, boolean selection) {
      int x = 0;
      int width = (getWidth() - 3) / 4;
      if (gravity == Gravity.center) {
        x = width + 1;
      }
      else if (gravity == Gravity.right) {
        x = 2 * width + 2;
      }
      else if (gravity == null) {
        x = getWidth() - width;
      }

      int y = 0;
      int height = getHeight();

      int hSpace = Math.min(5, Math.max(1, getWidth() / 30));
      if (hSpace > 1) {
        x += hSpace;
        width -= 2 * hSpace;
      }

      int vSpace = Math.min(5, Math.max(1, getHeight() / 30));
      if (vSpace > 1) {
        y += vSpace;
        height -= 2 * vSpace;
      }

      if (selection) {
        if (myGravity == gravity) {
          Color oldColor = g2d.getColor();
          g2d.setColor(LightColors.YELLOW);
          g2d.fillRect(x, y, width, height);
          g2d.setColor(oldColor);

          return true;
        }
      }
      else {
        g2d.fillRect(x, y, width, height);
      }

      return false;
    }
  }
}