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
package com.intellij.android.designer.designSurface;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class RootView extends JComponent {
  private int myX;
  private int myY;
  private BufferedImage myImage;
  private List<EmptyRegion> myEmptyRegions;

  public RootView(BufferedImage image, int x, int y) {
    myX = x;
    myY = y;
    setImage(image);
  }

  public void setImage(BufferedImage image) {
    myImage = image;
    myEmptyRegions = new ArrayList<EmptyRegion>();
    setBounds(myX, myY, image.getWidth(), image.getHeight());
  }

  public void addEmptyRegion(int x, int y, int width, int height) {
    if (new Rectangle(0, 0, myImage.getWidth(), myImage.getHeight()).contains(x, y)) {
      EmptyRegion r = new EmptyRegion();
      r.myX = x;
      r.myY = y;
      r.myWidth = width;
      r.myHeight = height;
      r.myColor = new Color(~myImage.getRGB(x, y));
      myEmptyRegions.add(r);
    }
  }

  @Override
  public void paintComponent(Graphics g) {
    super.paintComponent(g);
    g.drawImage(myImage, 0, 0, null);

    for (EmptyRegion r : myEmptyRegions) {
      g.setColor(r.myColor);
      g.fillRect(r.myX, r.myY, r.myWidth, r.myHeight);
    }
  }

  private static class EmptyRegion {
    public Color myColor;
    public int myX;
    public int myY;
    public int myWidth;
    public int myHeight;
  }
}