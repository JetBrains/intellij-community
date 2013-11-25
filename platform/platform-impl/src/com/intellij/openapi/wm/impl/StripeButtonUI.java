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
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.ui.Gray;
import com.intellij.util.ui.UIUtil;

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
    dim.height+=2;

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

    final Graphics2D g2 = (Graphics2D)g.create();

    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);

    final ButtonModel model=button.getModel();
    final Color background = button.getBackground();
    ourIconRect.x -= 2;
    ourTextRect.x -= 2;
    if (model.isArmed() && model.isPressed() || model.isSelected() || model.isRollover()) {
      if (anchor == ToolWindowAnchor.LEFT) g2.translate(-1, 0);
      if (anchor.isHorizontal()) g2.translate(0, -1);
      final boolean dark = UIUtil.isUnderDarcula();
      g2.setColor(dark ? Gray._15.withAlpha(85) : Gray._85.withAlpha(85));
      g2.fillRect(0, 0, button.getWidth(), button.getHeight());
      if (anchor == ToolWindowAnchor.LEFT) g2.translate(1, 0);
      if (anchor.isHorizontal()) g2.translate(0, 1);
    }


    AffineTransform tr=null;
    if(ToolWindowAnchor.RIGHT==anchor||ToolWindowAnchor.LEFT==anchor){
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
      if(icon!=null){
        icon.paintIcon(c,g2,ourIconRect.x,ourIconRect.y);
      }
    }

    // paint text

    if(text!=null){
      if(model.isEnabled()){
        if(model.isArmed()&&model.isPressed()||model.isSelected()){
          g2.setColor(background);
        } else{
          g2.setColor(button.getForeground());
        }
      } else{
        g2.setColor(background.darker());
      }
      /* Draw the Text */
      if(model.isEnabled()){
        /*** paint the text normally */
        g2.setColor(UIUtil.isUnderDarcula() && (model.isSelected() || model.isRollover()) ? button.getForeground().brighter() : button.getForeground());
        BasicGraphicsUtils.drawString(g2,clippedText,button.getMnemonic2(),ourTextRect.x,ourTextRect.y+fm.getAscent());
      } else{
        /*** paint the text disabled ***/
        if(model.isSelected()){
          g2.setColor(c.getBackground());
        } else{
          g2.setColor(getDisabledTextColor());
        }
        BasicGraphicsUtils.drawString(g2,clippedText,button.getMnemonic2(),ourTextRect.x,ourTextRect.y+fm.getAscent());
      }
    }
    if(ToolWindowAnchor.RIGHT==anchor||ToolWindowAnchor.LEFT==anchor){
      g2.setTransform(tr);
    }
    
    g2.dispose();
  }


  private static void paintLegacyDecoration(Graphics2D g2, AnchoredButton button, ButtonModel model, boolean vertical) {
    final boolean dark = UIUtil.isUnderDarcula();
    Color toBorder = model.isRollover() ? dark ? Gray._90 : new Color(0, 0, 0, 50) : null;
    if (model.isArmed() && model.isPressed() || model.isSelected()) {
      g2.setColor(dark ? Gray._85.withAlpha(85) : new Color(0, 0, 0, 30));
      g2.fillRect(3, 3, button.getWidth() - (vertical ? 6 : 5), button.getHeight() - 6);

      g2.setColor(dark ? Gray._40 : new Color(0, 0, 0, 120));
      g2.drawLine(2, 2, 3 + button.getWidth() - (vertical ? 7 : 6), 2);
      g2.drawLine(2, 3, 2, 3 + button.getHeight() - 7);

      g2.setColor(dark ? Gray._65 : new Color(0, 0, 0, 40));
      g2.drawRect(3, 3, button.getWidth() - (vertical ? 7 : 6), button.getHeight() - 7);

      g2.setColor(dark ? Gray._75 : new Color(255, 255, 255, 110));
      g2.drawLine(3, button.getHeight() - 3, 3 + button.getWidth() - (vertical ? 6 : 5), button.getHeight() - 3);
      g2.drawLine(3 + button.getWidth() - (vertical ? 6 : 5), 2, 3 + button.getWidth() - (vertical ? 6 : 5),
                  3 + button.getHeight() - 7);
      toBorder = null;
    }
    if (toBorder != null) {
      g2.setColor(toBorder);
      g2.drawRect(2, 2, button.getWidth() - (vertical ? 6 : 5), button.getHeight() - 6);
    }
  }
}
