// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs.impl;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public final class ShapeTransformTest {
  private static final class Painter extends JLabel {
    private final ShapeTransform myT;

    private Painter(String text, ShapeTransform transform) {
      setText(text);
      setFont(getFont().deriveFont(30f));
      setBorder(new EmptyBorder(6, 6, 6, 6));
      myT = transform;
      setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);

      g.setColor(Color.red);
      final Graphics2D g2d = (Graphics2D)g;

      int arc1 = 4;
      int arc2 = 6;

      final Rectangle r = new Rectangle(arc1, arc1, getWidth() - arc1 - 1, getHeight() - arc1 - 1);

      myT.reset(r);

      myT.moveTo(myT.getX() - myT.deltaX(arc1), myT.getMaxY());
      myT.quadTo(myT.getX(), myT.getMaxY(), myT.getX(), myT.getMaxY() - myT.deltaY(arc1));
      myT.lineTo(myT.getX(), myT.getY() + myT.deltaY(arc2));
      myT.quadTo(myT.getX(), myT.getY(), myT.getX() + myT.deltaX(arc2), myT.getY());
      myT.lineTo(myT.getMaxX() - myT.deltaX(arc2), myT.getY());
      myT.quadTo(myT.getMaxX(), myT.getY(), myT.getMaxX(), myT.getY() + myT.deltaY(arc2));
      myT.lineTo(myT.getMaxX(), myT.getMaxY());

      g2d.draw(myT.getShape());


      final int innerRecSize = 18;
      final Rectangle innerRec = new Rectangle(getWidth() / 2 - innerRecSize / 2, getHeight() / 2 - innerRecSize / 2, innerRecSize, innerRecSize);

      final ShapeTransform inner = myT.createTransform(innerRec);
      inner.moveTo(inner.getX(), inner.getMaxY());
      inner.lineTo(inner.getX(), inner.getY());
      inner.lineTo(inner.getMaxX(), inner.getY());
      inner.lineTo(inner.getMaxX(), inner.getMaxY());

      g2d.draw(inner.getShape());
    }
  }


  public static void main(String[] args) {
    final JFrame frame = new JFrame();
    frame.getContentPane().setLayout(new BorderLayout());
    final JPanel content = new JPanel(new FlowLayout(FlowLayout.CENTER));
    frame.getContentPane().add(content, BorderLayout.CENTER);

    content.add(new Painter("TOP", new ShapeTransform.Top()));
    content.add(new Painter("LEFT", new ShapeTransform.Left()));
    content.add(new Painter("BOTTOM", new ShapeTransform.Bottom()));
    content.add(new Painter("RIGHT", new ShapeTransform.Right()));

    frame.setBounds(300, 300, 300, 300);
    frame.show();
  }
}
