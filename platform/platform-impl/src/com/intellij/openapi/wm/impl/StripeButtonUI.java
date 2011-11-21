/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.wm.ToolWindowAnchor;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.plaf.metal.MetalToggleButtonUI;
import java.awt.*;
import java.awt.geom.AffineTransform;

/**
 * @author Vladimir Kondratyev
 */
public final class StripeButtonUI extends MetalToggleButtonUI{
  private static final StripeButtonUI ourInstance=new StripeButtonUI();

  private static final Rectangle ourIconRect=new Rectangle();
  private static final Rectangle ourTextRect=new Rectangle();
  private static final Rectangle ourViewRect=new Rectangle();
  private static Insets ourViewInsets=new Insets(0,0,0,0);

  private StripeButtonUI(){}

  /** Invoked by reflection */
  public static ComponentUI createUI(final JComponent c){
    return ourInstance;
  }

  public Dimension getPreferredSize(final JComponent c){
    final AnchoredButton button=(AnchoredButton)c;
    final Dimension dim=super.getPreferredSize(button);

    dim.width=(int)(4+dim.width*1.1f);
    dim.height+=4;

    final ToolWindowAnchor anchor=button.getAnchor();
    if(ToolWindowAnchor.LEFT==anchor||ToolWindowAnchor.RIGHT==anchor){
      return new Dimension(dim.height,dim.width);
    } else{
      return dim;
    }
  }

  public void paint(final Graphics g,final JComponent c){
    final AnchoredButton button=(AnchoredButton)c;

    final String text=button.getText();
    final Icon icon=(button.isEnabled()) ? button.getIcon() : button.getDisabledIcon();

    if((icon==null)&&(text==null)){
      return;
    }

    final FontMetrics fm=button.getFontMetrics(button.getFont());
    ourViewInsets=c.getInsets(ourViewInsets);

    ourViewRect.x=ourViewInsets.left;
    ourViewRect.y=ourViewInsets.top;

    final ToolWindowAnchor anchor=button.getAnchor();

    // Use inverted height & width
    if(ToolWindowAnchor.RIGHT==anchor||ToolWindowAnchor.LEFT==anchor){
      ourViewRect.height=c.getWidth()-(ourViewInsets.left+ourViewInsets.right);
      ourViewRect.width=c.getHeight()-(ourViewInsets.top+ourViewInsets.bottom);
    } else{
      ourViewRect.height=c.getHeight()-(ourViewInsets.left+ourViewInsets.right);
      ourViewRect.width=c.getWidth()-(ourViewInsets.top+ourViewInsets.bottom);
    }

    ourIconRect.x=ourIconRect.y=ourIconRect.width=ourIconRect.height=0;
    ourTextRect.x=ourTextRect.y=ourTextRect.width=ourTextRect.height=0;

    final String clippedText=SwingUtilities.layoutCompoundLabel(
      c,fm,text,icon,
      button.getVerticalAlignment(),button.getHorizontalAlignment(),
      button.getVerticalTextPosition(),button.getHorizontalTextPosition(),
      ourViewRect,ourIconRect,ourTextRect,
      button.getText()==null ? 0 : button.getIconTextGap()
    );

    // Paint button's background

    final Graphics2D g2=(Graphics2D)g;
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);

    final ButtonModel model=button.getModel();

    final Color background = button.getBackground();

    boolean toFill = model.isArmed() && model.isPressed() || model.isSelected();
    if (toFill) {
      g2.setColor(new Color(0, 0, 0, 30));
      g2.fillRect(0, 0, button.getWidth(), button.getHeight());
    }

    g.setColor(new Color(0, 0, 0, 90));
    StripeButton stripeButton = (StripeButton)c;

    AffineTransform tr=null;
    if(ToolWindowAnchor.RIGHT==anchor||ToolWindowAnchor.LEFT==anchor){
      boolean oppositeSide = stripeButton.isOppositeSide();
      if (oppositeSide) {
        g.drawLine(0, 0, button.getWidth() - 1, 0);
      } else {
        g.drawLine(0, button.getHeight() - 1, button.getWidth() - 1, button.getHeight() - 1);
      }
      
      if (toFill) {
        if (anchor == ToolWindowAnchor.LEFT) {
          g2.setColor(new Color(0, 0, 0, 30));
          g2.drawRect(0, stripeButton.isFirst() || oppositeSide ? 1 : 0, button.getWidth() - 2,
                      button.getHeight() - (oppositeSide ? 2 : stripeButton.isFirst() ? 3 : 2));
        } else {
          g2.setColor(new Color(0, 0, 0, 30));
          g2.drawRect(1, stripeButton.isFirst() || oppositeSide ? 1 : 0, button.getWidth() - 2,
                      button.getHeight() - (oppositeSide ? 2 : stripeButton.isFirst() ? 3 : 2));
        }
      }
      
      tr=g2.getTransform();
      if(ToolWindowAnchor.RIGHT==anchor){
        if(icon != null){ // do not rotate icon
          icon.paintIcon(c, g2, ourIconRect.y, ourIconRect.x);
        }
        g2.rotate(Math.PI/2);
        g2.translate(0,-c.getWidth());
      } else {
        if(icon != null){ // do not rotate icon
          icon.paintIcon(c, g2, ourIconRect.y, c.getHeight() - ourIconRect.x - icon.getIconHeight());
        }
        g2.rotate(-Math.PI/2);
        g2.translate(-c.getHeight(),0);
      }
    }
    else{
      boolean oppositeSide = stripeButton.isOppositeSide();
      if (oppositeSide) {
        g.drawLine(0, 0, 0, button.getHeight() - 1);
        if (stripeButton.isLast()) {
          g.drawLine(button.getWidth() - 1, 0, button.getWidth() - 1, button.getHeight() - 1);
        }
      } else {
        g.drawLine(button.getWidth() - 1, 0, button.getWidth() - 1, button.getHeight() - 1);
        if (stripeButton.isFirst()) {
          g.drawLine(0, 0, 0, button.getHeight() - 1);
        }
      }
      
      if (toFill) {
        g2.setColor(new Color(0, 0, 0, 30));
        g2.drawRect(oppositeSide || stripeButton.isFirst() ? 1 : 0, 1,
                    button.getWidth() - (stripeButton.isLast() || stripeButton.isFirst() ? 3 : 2), button.getHeight() - 2);
      }

      if(icon!=null){
        icon.paintIcon(c,g2,ourIconRect.x,ourIconRect.y);
      }
    }

    // paint text

    if(text!=null){
      if(model.isEnabled()){
        if(model.isArmed()&&model.isPressed()||model.isSelected()){
          g.setColor(background);
        } else{
          g.setColor(button.getForeground());
        }
      } else{
        g.setColor(background.darker());
      }
      /* Draw the Text */
      if(model.isEnabled()){
        /*** paint the text normally */
        g.setColor(button.getForeground());
        BasicGraphicsUtils.drawString(g,clippedText,button.getMnemonic2(),ourTextRect.x,ourTextRect.y+fm.getAscent());
      } else{
        /*** paint the text disabled ***/
        if(model.isSelected()){
          g.setColor(c.getBackground());
        } else{
          g.setColor(getDisabledTextColor());
        }
        BasicGraphicsUtils.drawString(g,clippedText,button.getMnemonic2(),ourTextRect.x,ourTextRect.y+fm.getAscent());
      }
    }
    if(ToolWindowAnchor.RIGHT==anchor||ToolWindowAnchor.LEFT==anchor){
      g2.setTransform(tr);
    }
  }
}
