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
package com.intellij.openapi.ui;

import junit.framework.TestCase;

import javax.swing.*;
import java.awt.*;

public class SplitterTest extends TestCase{

  public void testAll() {
    for (Splitter.LackOfSpaceStrategy strategy : Splitter.LackOfSpaceStrategy.values()) {
      {
        Splitter splitter = new Splitter(true);
        splitter.setLackOfSpaceStrategy(strategy);
        resizeTest(splitter);
      }

      {
        Splitter splitter = new Splitter(false);
        splitter.setLackOfSpaceStrategy(strategy);
        resizeTest(splitter);
      }
    }
  }

  private static void resizeTest(Splitter splitter) {
    JPanel jPanel1 = new JPanel();
    jPanel1.setMinimumSize(new Dimension(100, 100));
    JPanel jPanel2 = new JPanel();
    jPanel2.setMinimumSize(new Dimension(100, 100));
    splitter.setFirstComponent(jPanel1);
    splitter.setSecondComponent(jPanel2);
    splitter.setHonorComponentsMinimumSize(true);


    splitter.setSize(new Dimension(500, 500));
    splitter.doLayout();
    checkBounds(splitter);

    splitter.setSize(new Dimension(300, 300));
    splitter.doLayout();
    checkBounds(splitter);

    for (float proportion = 0; proportion <= 1; proportion+=0.01) {
      splitter.setProportion(proportion);
      splitter.doLayout();
      checkBounds(splitter);
    }

    splitter.setProportion(.9f);
    splitter.doLayout();
    checkBounds(splitter);

    splitter.setSize(new Dimension(100, 100));
    splitter.doLayout();
    checkBounds(splitter);

    splitter.setProportion(.1f);
    splitter.doLayout();
    checkBounds(splitter);

    splitter.setSize(new Dimension(10, 10));
    splitter.doLayout();
    checkBounds(splitter);

    splitter.setSize(new Dimension(100, 100));
    splitter.doLayout();
    checkBounds(splitter);

    splitter.setSize(new Dimension(150, 150));
    splitter.doLayout();
    checkBounds(splitter);

    splitter.setSize(splitter.isVertical() ? new Dimension(150, 1000) : new Dimension(1000, 150));
    for (float f = .01F; f < 1F; f+=.01F) {
      splitter.setProportion(f);
      splitter.doLayout();
      float proportion = splitter.getProportion();
      assertTrue (proportion==f);
    }
  }


  private static void checkBounds(Splitter splitter) {
    Dimension firstSize = splitter.getFirstComponent().getSize();
    Dimension secondSize = splitter.getSecondComponent().getSize();

    Dimension size = splitter.getSize();

    if(splitter.getOrientation()) { // Split horizontally
      assertTrue(firstSize.height + splitter.getDividerWidth() + secondSize.height == size.height);
      assertTrue(firstSize.width == size.width && secondSize.width == size.width);
    }
    else {
      assertTrue(firstSize.width + splitter.getDividerWidth() + secondSize.width == size.width);
      assertTrue(firstSize.height == size.height && secondSize.height == size.height);
    }

    if(splitter.isHonorMinimumSize()) {
      Dimension firstMinimum = splitter.getFirstComponent().getMinimumSize();
      Dimension secondMinimum = splitter.getSecondComponent().getMinimumSize();
      switch (splitter.getLackOfSpaceStrategy()) {
        case SIMPLE_RATIO:
          assertTrue(firstSize.width < firstMinimum.width == secondSize.width < secondMinimum.width);
          assertTrue(firstSize.height < firstMinimum.height == secondSize.height < secondMinimum.height);
          break;
        case HONOR_THE_FIRST_MIN_SIZE:
          assertTrue(splitter.getOrientation() ? firstSize.height >= firstMinimum.height : firstSize.width >= firstMinimum.width);
          break;
        case HONOR_THE_SECOND_MIN_SIZE:
          assertTrue(splitter.getOrientation() ? secondSize.height >= secondMinimum.height : secondSize.width >= secondMinimum.width);
          break;
      }
    }
  }
}
