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

import com.android.SdkConstants;
import com.intellij.android.designer.designSurface.layout.actions.ResizeOperation;
import com.intellij.android.designer.designSurface.layout.flow.FlowBaseOperation;
import com.intellij.android.designer.model.ModelParser;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.layout.Gravity;
import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.feedbacks.TextFeedback;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class LinearLayoutOperation extends FlowBaseOperation {
  private GravityFeedback myFeedback;
  private TextFeedback myTextFeedback;
  private Gravity myExclude;
  private Gravity myGravity;

  public LinearLayoutOperation(RadComponent container, OperationContext context, boolean horizontal) {
    super(container, context, horizontal);

    if (context.isMove() && context.getComponents().size() == 1) {
      myExclude = getGravity(myHorizontal, context.getComponents().get(0));
    }
  }

  @Override
  protected void createFeedback() {
    super.createFeedback();

    if (myFeedback == null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();

      myFeedback = new GravityFeedback();
      if (myContainer.getChildren().isEmpty()) {
        myFeedback.setBounds(myBounds);
      }
      layer.add(myFeedback, 0);

      myTextFeedback = new TextFeedback();
      myTextFeedback.setBorder(IdeBorderFactory.createEmptyBorder(0, 3, 2, 0));
      layer.add(myTextFeedback);

      layer.repaint();
    }
  }

  @Override
  public void showFeedback() {
    super.showFeedback();

    Point location = myContext.getLocation();
    Gravity gravity = myHorizontal ? calculateVertical(myBounds, location) : calculateHorizontal(myBounds, location);

    if (!myContainer.getChildren().isEmpty()) {
      myFeedback.setBounds(myInsertFeedback.getBounds());
    }

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
    Gravity horizontal = Gravity.right;
    double width = bounds.width / 4.0;
    double left = bounds.x + width;
    double center = bounds.x + 2 * width;
    double fill = bounds.x + 3 * width;

    if (location.x < left) {
      horizontal = Gravity.left;
    }
    else if (left < location.x && location.x < center) {
      horizontal = Gravity.center;
    }
    else if (center < location.x && location.x < fill) {
      horizontal = null;
    }

    return horizontal;
  }

  @Nullable
  private static Gravity calculateVertical(Rectangle bounds, Point location) {
    Gravity vertical = Gravity.bottom;
    double height = bounds.height / 4.0;
    double top = bounds.y + height;
    double center = bounds.y + 2 * height;
    double fill = bounds.y + 3 * height;

    if (location.y < top) {
      vertical = Gravity.top;
    }
    else if (top < location.y && location.y < center) {
      vertical = Gravity.center;
    }
    else if (center < location.y && location.y < fill) {
      vertical = null;
    }

    return vertical;
  }

  @Override
  public boolean canExecute() {
    return super.canExecute() || (myComponents.size() == 1 && myGravity != myExclude);
  }

  @Override
  public void execute() throws Exception {
    if (super.canExecute()) {
      super.execute();
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        execute(myHorizontal, myGravity, myComponents);
      }
    });
  }

  public static void execute(boolean horizontal, Gravity gravity, List<RadComponent> components) {
    if (gravity == null) {
      for (RadComponent component : components) {
        XmlTag tag = ((RadViewComponent)component).getTag();
        ModelParser.deleteAttribute(tag, "layout_gravity");
        tag.setAttribute(horizontal ? "layout_height" : "layout_width", SdkConstants.NS_RESOURCES, "fill_parent");
      }
    }
    else {
      String gravityValue = horizontal ? Gravity.getValue(Gravity.center, gravity) : Gravity.getValue(gravity, Gravity.center);

      for (RadComponent component : components) {
        XmlTag tag = ((RadViewComponent)component).getTag();

        XmlAttribute attribute = tag.getAttribute(horizontal ? "layout_height" : "layout_width", SdkConstants.NS_RESOURCES);
        if (attribute != null && ("match_parent".equals(attribute.getValue()) || "fill_parent".equals(attribute.getValue()))) {
          attribute.setValue("wrap_content");
        }

        tag.setAttribute("layout_gravity", SdkConstants.NS_RESOURCES, gravityValue);
      }
    }
  }

  @Nullable
  public static Gravity getGravity(boolean horizontal, RadComponent component) {
    XmlTag tag = ((RadViewComponent)component).getTag();
    String length = tag.getAttributeValue(horizontal ? "layout_height" : "layout_width", SdkConstants.NS_RESOURCES);

    if (!ResizeOperation.isFill(length)) {
      Pair<Gravity, Gravity> gravity = Gravity.getSides(component);
      return horizontal ? gravity.second : gravity.first;
    }

    return null;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Feedback
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  private static final int SIZE = 2;

  private class GravityFeedback extends JComponent {
    private Gravity myGravity;

    public void setGravity(Gravity gravity) {
      myGravity = gravity;
      repaint();
    }

    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      g.setColor(JBColor.MAGENTA);

      if (myHorizontal) {
        paintHorizontalCell(g);
      }
      else {
        paintVerticalCell(g);
      }
    }

    private void paintHorizontalCell(Graphics g) {
      int y = 0;
      int height = (getHeight() - 3) / 4;
      if (myGravity == Gravity.center) {
        y = height + 1;
      }
      else if (myGravity == null) {
        y = 2 * height + 2;
      }
      else if (myGravity == Gravity.bottom) {
        y = getHeight() - height;
      }

      int vSpace = Math.min(5, Math.max(1, getHeight() / 30));
      if (vSpace > 1) {
        y += vSpace;
        height -= 2 * vSpace;
      }

      if (myContainer.getChildren().isEmpty()) {
        g.fillRect(0, y, 2, height);
        g.fillRect(myBounds.width - SIZE, y, SIZE, height);
      }
      else {
        g.fillRect(SIZE, y, SIZE, height);
      }
    }

    private void paintVerticalCell(Graphics g) {
      int x = 0;
      int width = (getWidth() - 3) / 4;
      if (myGravity == Gravity.center) {
        x = width + 1;
      }
      else if (myGravity == null) {
        x = 2 * width + 2;
      }
      else if (myGravity == Gravity.right) {
        x = getWidth() - width;
      }

      int hSpace = Math.min(5, Math.max(1, getWidth() / 30));
      if (hSpace > 1) {
        x += hSpace;
        width -= 2 * hSpace;
      }

      if (myContainer.getChildren().isEmpty()) {
        g.fillRect(x, 0, width, SIZE);
        g.fillRect(x, myBounds.height - SIZE, width, SIZE);
      }
      else {
        g.fillRect(x, SIZE, width, SIZE);
      }
    }
  }
}