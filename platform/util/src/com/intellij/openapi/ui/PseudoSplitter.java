/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import javax.swing.*;

public class PseudoSplitter extends Splitter {
  private boolean myFirstIsFixed;
  private boolean mySecondIsFixed;
  private int myFirstFixedSize;
  private int mySecondFixedSize;

  public PseudoSplitter(boolean vertical) {
    super(vertical);
    myFirstIsFixed = false;
    mySecondIsFixed = false;
  }

  private int getSizeForComp(final JComponent component) {
    return getOrientation() ? component.getHeight() : component.getWidth();
  }

  public void fixFirst(final float proportion) {
    assert getFirstComponent() != null;
    int total = getSizeForComp(this);
    myFirstFixedSize = (int)(proportion * (total - getDividerWidth()));
    myFirstIsFixed = true;
    mySecondIsFixed = false;
  }

  public void fixFirst() {
    assert getFirstComponent() != null;
    myFirstFixedSize = getSizeForComp(getFirstComponent());
    myFirstIsFixed = true;
    mySecondIsFixed = false;
  }

  public void freeAll() {
    myFirstIsFixed = false;
    mySecondIsFixed = false;
  }

  @Override
  public void doLayout() {
    int total = getSizeForComp(this);
    if (myFirstIsFixed) {
      myProportion = ((float)myFirstFixedSize) / (total - getDividerWidth());
    } else if (mySecondIsFixed) {
      myProportion = ((float)total - mySecondFixedSize) / (total - getDividerWidth());
    }
    super.doLayout();
  }

  @Override
  public void setProportion(float proportion) {
    boolean firstIsFixed = myFirstIsFixed;
    boolean secondIsFixed = mySecondIsFixed;
    myFirstIsFixed = false;
    mySecondIsFixed = false;
    super.setProportion(proportion);
    
    int total = getSizeForComp(this);
    if (firstIsFixed) {
      myFirstFixedSize = (int) (myProportion * (total - getDividerWidth()));
      myFirstIsFixed = true;
    } else if (secondIsFixed) {
      mySecondFixedSize = (int) ((1 - myProportion) * (total - getDividerWidth()));
      mySecondIsFixed = true;
    }
  }
}
