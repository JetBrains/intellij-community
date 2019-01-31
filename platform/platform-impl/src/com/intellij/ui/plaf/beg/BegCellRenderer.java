// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.plaf.beg;

import com.intellij.ui.Gray;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;

//TODO remove the class

public class BegCellRenderer extends JLabel implements TreeCellRenderer, ListCellRenderer {
  protected Icon myLeafIcon;
  protected Color myTextBackground;
  protected boolean mySelected;
  protected Color mySelectionForeground;
  protected Color mySelectionBorderColor;
  protected Icon myOpenIcon;
  protected boolean myHasFocus;
  protected Color myTextForeground;
  protected boolean myDrawsFocusBorderAroundIcon;
  protected Color mySelectionBackground;
  protected Icon myClosedIcon;

  public BegCellRenderer() {
    setHorizontalAlignment(SwingConstants.LEFT);
  }

  public void setSelectionBorderColor(Color color) {
    mySelectionBorderColor = color;
  }

  @Override
  public Component getTreeCellRendererComponent(JTree tree, Object obj, boolean selected, boolean expanded, boolean leaf, int i1, boolean hasFocus) {
    setFont(UIUtil.getLabelFont());
    setLeafIcon(UIManager.getIcon("Tree.leafIcon"));
    setClosedIcon(UIManager.getIcon("Tree.closedIcon"));
    setOpenIcon(UIManager.getIcon("Tree.openIcon"));
    setSelectionForeground(UIUtil.getTreeSelectionForeground());
    setTextForeground(UIUtil.getTreeForeground());
    setSelectionBackground(UIUtil.getTreeSelectionBackground());
    setTextBackground(UIUtil.getTreeBackground());
    setSelectionBorderColor(UIUtil.getTreeSelectionBorderColor());
    Object obj1 = UIManager.get("Tree.drawsFocusBorderAroundIcon");
    myDrawsFocusBorderAroundIcon = obj1 != null && ((Boolean)obj1).booleanValue();

    myHasFocus = hasFocus;
    mySelected = selected;
    String text = tree.convertValueToText(obj, selected, expanded, leaf, i1, hasFocus);
    setText(text);
    setForeground(UIUtil.getTreeForeground(selected, hasFocus));
    Icon icon = getIcon(tree, obj, selected, expanded, leaf, i1, hasFocus);
    setIcon(icon);
    return this;
  }

  @Override
  public Component getListCellRendererComponent(JList list, Object obj, int index, boolean selected, boolean hasFocus) {
    setFont(UIUtil.getLabelFont());
    setSelectionForeground(UIUtil.getListSelectionForeground());
    setTextForeground(UIUtil.getListForeground());
    setSelectionBackground(UIManager.getColor("List.selectionBackground"));
    setTextBackground(UIUtil.getListBackground());
    setSelectionBorderColor(UIUtil.getTreeSelectionBorderColor());
    Object obj1 = UIManager.get("Tree.drawsFocusBorderAroundIcon");
    myDrawsFocusBorderAroundIcon = obj1 != null && ((Boolean)obj1).booleanValue();

    myHasFocus = hasFocus;
    mySelected = selected;
    String text = String.valueOf(obj);
    setText(text);
    if (hasFocus && selected){
      setForeground(getSelectionForeground());
    }else{
      setForeground(getTextForeground());
    }
    return this;
  }


  public void setSelectionBackground(Color color) {
    mySelectionBackground = color;
  }

  public Icon getClosedIcon() {
    return myClosedIcon;
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension dimension = super.getPreferredSize();
    if (dimension != null){
      dimension = new Dimension(dimension.width + 3, dimension.height);
    }
    return dimension;
  }

  public Icon getOriginalClosedIcon() {
    return UIManager.getIcon("Tree.closedIcon");
  }

  public Color getSelectionBorderColor() {
    return mySelectionBorderColor;
  }

  public Color getSelectionForeground() {
    return mySelectionForeground;
  }

  public Color getTextBackground() {
    return myTextBackground;
  }

  public void setClosedIcon(Icon icon) {
    myClosedIcon = icon;
  }

  public void setTextBackground(Color color) {
    myTextBackground = color;
  }

  public void setTextForeground(Color color) {
    myTextForeground = color;
  }

  protected Icon getIcon(JTree jtree, Object obj, boolean selected, boolean expanded, boolean leaf, int i1, boolean flag3) {
    Icon icon;
    if (leaf){
      icon = getLeafIcon();
    }
    else
      if (expanded)
        icon = getOpenIcon();
      else
        icon = getClosedIcon();
    return icon;
  }

  public Icon getLeafIcon() {
    return myLeafIcon;
  }

  public void setOpenIcon(Icon icon) {
    myOpenIcon = icon;
  }

  protected void paintNode(Graphics g) {
    Color color;
    if (mySelected) {
      if (myHasFocus){
        color = getSelectionBackground();
      }
      else{
        color = Gray._223;
      }
    }
    else{
      color = getTextBackground();
      if (color == null){
        color = getBackground();
      }
    }
    if (color != null){
      int i1 = getBorderLeft();
      g.setColor(color);
      g.fillRect(i1 - 1, 0, getWidth() - i1, getHeight());
    }
  }

  @Override
  protected void paintBorder(Graphics g) {
//    if (/*myHasFocus ||*/ mySelected) {
    if (myHasFocus){
      int i1 = getBorderLeft();
      if (myDrawsFocusBorderAroundIcon){
        i1 = 0;
      }
      else
        if (i1 == -1){
          i1 = getBorderLeft();
        }
      Color color = getSelectionBorderColor();
      if (color != null){
        g.setColor(color);
        int j1 = getWidth() - 1;
        int k1 = getHeight() - 1;
        UIUtil.drawDottedRectangle(g, i1 > 0 ? i1 - 1 : i1, 0, j1, k1);
      }
    }
  }

  @Override
  public void paint(Graphics g) {
    paintNode(g);
    paintBorder(g);
    super.paint(g);
  }

  public Icon getOpenIcon() {
    return myOpenIcon;
  }

  public void setSelectionForeground(Color color) {
    mySelectionForeground = color;
  }

  public Icon getOriginalOpenIcon() {
    return UIManager.getIcon("Tree.openIcon");
  }

  public void setLeafIcon(Icon icon) {
    myLeafIcon = icon;
  }

  @Override
  public void setBackground(Color color) {
    if (color instanceof ColorUIResource){
      color = null;
    }
    super.setBackground(color);
  }

  public Color getSelectionBackground() {
    return mySelectionBackground;
  }


  public Icon getOriginalLeafIcon() {
    return UIManager.getIcon("Tree.leafIcon");
  }

  protected int getBorderLeft() {
    Icon icon = getIcon();
    if (icon != null && getText() != null){
      return icon.getIconWidth() + Math.max(0, getIconTextGap() - 1);
      //return 0;
    }
    else{
      return 0;
    }
  }

  public Color getTextForeground() {
    return myTextForeground;
  }

}
