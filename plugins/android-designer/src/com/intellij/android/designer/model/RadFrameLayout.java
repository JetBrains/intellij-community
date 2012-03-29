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
package com.intellij.android.designer.model;

import com.intellij.android.designer.designSurface.AbstractEditOperation;
import com.intellij.android.designer.designSurface.TreeDropToOperation;
import com.intellij.designer.designSurface.*;
import com.intellij.designer.designSurface.feedbacks.AlphaComponent;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.LightColors;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Set;

/**
 * @author Alexander Lobas
 */
public class RadFrameLayout extends RadViewLayoutWithData {
  private static final String[] LAYOUT_PARAMS = {"FrameLayout_Layout", "ViewGroup_MarginLayout"};

  @Override
  public String[] getLayoutParams() {
    return LAYOUT_PARAMS;
  }

  @Override
  public EditOperation processChildOperation(OperationContext context) {
    if (context.isCreate() || context.isPaste() || context.isAdd() || context.isMove()) {
      if (context.isTree()) {
        return new TreeDropToOperation(myContainer, context);
      }
      return new MyEditOperation((RadViewComponent)myContainer, context);
    }
    return null;
  }

  @Override
  public ComponentDecorator getChildSelectionDecorator(RadComponent component, List<RadComponent> selection) {
    return super.getChildSelectionDecorator(component, selection); // TODO: Auto-generated method stub
  }

  @Override
  public void addSelectionActions(DesignerEditorPanel designer,
                                  DefaultActionGroup actionGroup,
                                  JComponent shortcuts,
                                  List<RadComponent> selection) {
    super.addSelectionActions(designer, actionGroup, shortcuts, selection); // TODO: Auto-generated method stub
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // EditOperation
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  private static class MyEditOperation extends AbstractEditOperation {
    private FrameFeedback myFeedback;
    private JLabel myTextFeedback;
    private String myGravity;
    private Set<Pair<Gravity, Gravity>> myExcludes;

    public MyEditOperation(RadViewComponent container, OperationContext context) {
      super(container, context);

      if (context.isMove()) {
        myExcludes = new HashSet<Pair<Gravity, Gravity>>();

        for (RadComponent component : context.getComponents()) {
          String value = ((RadViewComponent)component).getTag().getAttributeValue("android:layout_gravity");
          if (StringUtil.isEmpty(value)) {
            myExcludes.add(new Pair<Gravity, Gravity>(Gravity.Left, Gravity.Top));
          }
          else {
            for (String option : StringUtil.split(value, "|")) {
              // TODO
            }
          }
        }
      }
    }

    private void createFeedback(FeedbackLayer layer, Rectangle bounds) {
      if (myFeedback == null) {
        myFeedback = new FrameFeedback();
        layer.add(myFeedback);
        myFeedback.setBounds(bounds);

        myTextFeedback = new JLabel();
        myTextFeedback.setFont(myTextFeedback.getFont().deriveFont(Font.BOLD));
        myTextFeedback.setBackground(LightColors.YELLOW);
        myTextFeedback.setBorder(IdeBorderFactory.createEmptyBorder(0, 3, 2, 0));
        myTextFeedback.setOpaque(true);
        layer.add(myTextFeedback);

        layer.repaint();
      }
    }

    @Override
    public void showFeedback() {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
      Rectangle bounds = myContainer.getBounds(layer);

      createFeedback(layer, bounds);

      Point location = myContext.getLocation();
      Gravity horizontal = calculateHorizontal(bounds, location);
      Gravity vertical = calculateVertical(bounds, location);

      if (myContext.isMove() && myExcludes.contains(new Pair<Gravity, Gravity>(horizontal, vertical))) {
        horizontal = vertical = null;
      }

      myFeedback.setGravity(horizontal, vertical);
      configureTextFeedback(bounds, horizontal, vertical);

      configureGravity(horizontal, vertical);
    }

    private void configureTextFeedback(Rectangle bounds, Gravity horizontal, Gravity vertical) {
      if (horizontal == null || vertical == null) {
        myTextFeedback.setText("None");
      }
      else {
        myTextFeedback.setText("[" + vertical.name() + ", " + horizontal.name() + "]");
      }

      Dimension textSize = myTextFeedback.getPreferredSize();
      myTextFeedback
        .setBounds(bounds.x + bounds.width / 2 - textSize.width / 2, bounds.y - textSize.height - 10, textSize.width, textSize.height);
    }

    @Nullable
    private static Gravity calculateHorizontal(Rectangle bounds, Point location) {
      Gravity horizontal = null;
      double left = bounds.x + bounds.width / 3.0;
      double right = bounds.x + 2 * bounds.width / 3.0;

      if (location.x < left) {
        horizontal = Gravity.Left;
      }
      else if (left < location.x && location.x < right) {
        horizontal = Gravity.Center;
      }
      else if (location.x > right) {
        horizontal = Gravity.Right;
      }

      return horizontal;
    }

    @Nullable
    private static Gravity calculateVertical(Rectangle bounds, Point location) {
      Gravity vertical = null;
      double top = bounds.y + bounds.height / 3.0;
      double bottom = bounds.y + 2 * bounds.height / 3.0;

      if (location.y < top) {
        vertical = Gravity.Top;
      }
      else if (top < location.y && location.y < bottom) {
        vertical = Gravity.Center;
      }
      else if (location.y > bottom) {
        vertical = Gravity.Bottom;
      }

      return vertical;
    }

    private void configureGravity(Gravity horizontal, Gravity vertical) {
      StringBuffer gravity = new StringBuffer();

      if (horizontal == Gravity.Center && vertical == Gravity.Center) {
        gravity.append("center");
      }
      else {
        if (horizontal == Gravity.Left) {
          gravity.append("left");
        }
        else if (horizontal == Gravity.Center) {
          gravity.append("center_horizontal");
        }
        else if (horizontal == Gravity.Right) {
          gravity.append("right");
        }

        if (vertical == Gravity.Top) {
          if (gravity.length() > 0) {
            gravity.append("|");
          }
          gravity.append("top");
        }
        else if (vertical == Gravity.Center) {
          if (gravity.length() > 0) {
            gravity.append("|");
          }
          gravity.append("center_vertical");
        }
        else if (vertical == Gravity.Bottom) {
          if (gravity.length() > 0) {
            gravity.append("|");
          }
          gravity.append("bottom");
        }
      }

      myGravity = gravity.length() == 0 ? null : gravity.toString();
    }

    @Override
    public void eraseFeedback() {
      if (myFeedback != null) {
        FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
        layer.remove(myFeedback);
        layer.remove(myTextFeedback);
        layer.repaint();
        myFeedback = null;
        myTextFeedback = null;
      }
    }

    @Override
    public boolean canExecute() {
      return myGravity != null;
    }

    @Override
    public void execute() throws Exception {
      super.execute();

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          for (RadComponent component : myComponents) {
            ((RadViewComponent)component).getTag().setAttribute("android:layout_gravity", myGravity);
          }
        }
      });
    }
  }

  private static class FrameFeedback extends AlphaComponent {
    private static final Gravity[] HORIZONTAL = {Gravity.Left, Gravity.Center, Gravity.Right};
    private static final Gravity[] VERTICAL = {Gravity.Top, Gravity.Center, Gravity.Bottom};

    private static final int SPACE = 5;

    private Gravity myHorizontal;
    private Gravity myVertical;

    public FrameFeedback() {
      super(Color.green);
    }

    public void setGravity(Gravity horizontal, Gravity vertical) {
      myHorizontal = horizontal;
      myVertical = vertical;
      repaint();
    }

    @Override
    protected void paintOther1(Graphics2D g2d) {
      for (Gravity h : HORIZONTAL) {
        for (Gravity v : VERTICAL) {
          paintCell(g2d, h, v, false);
        }
      }
    }

    @Override
    protected void paintOther2(Graphics2D g2d) {
      for (Gravity h : HORIZONTAL) {
        for (Gravity v : VERTICAL) {
          if (paintCell(g2d, h, v, true)) {
            break;
          }
        }
      }
    }

    private boolean paintCell(Graphics2D g2d, Gravity horizontal, Gravity vertical, boolean selection) {
      int x = 0;
      int y = 0;
      int width = getWidth() / 3;
      int height = getHeight() / 3;

      if (horizontal == Gravity.Center) {
        x = width;
      }
      else if (horizontal == Gravity.Right) {
        x = getWidth() - width;
      }

      if (vertical == Gravity.Center) {
        y = height;
      }
      else if (vertical == Gravity.Bottom) {
        y = getHeight() - height;
      }

      if (selection) {
        if (myHorizontal == horizontal && myVertical == vertical) {
          Color oldColor = g2d.getColor();
          g2d.setColor(LightColors.YELLOW);
          g2d.fillRect(x + SPACE, y + SPACE, width - 2 * SPACE, height - 2 * SPACE);
          g2d.setColor(oldColor);

          return true;
        }
      }
      else {
        g2d.fillRect(x + SPACE, y + SPACE, width - 2 * SPACE, height - 2 * SPACE);
      }

      return false;
    }
  }

  private static enum Gravity {
    Left,
    Right,
    Center,
    Top,
    Bottom
  }
}