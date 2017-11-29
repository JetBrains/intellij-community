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
  private int myFirstFixedSize;

  public PseudoSplitter(boolean vertical) {
    super(vertical);
    myFirstIsFixed = false;
  }

  private int getSizeForComp(final JComponent component) {
    return getOrientation() ? component.getHeight() : component.getWidth();
  }

  public void fixFirst(final float proportion) {
    assert getFirstComponent() != null;
    int total = getSizeForComp(this);
    myFirstFixedSize = (int)(proportion * (total - getDividerWidth()));
    myFirstIsFixed = true;
  }

  public void fixFirst() {
    assert getFirstComponent() != null;
    myFirstFixedSize = getSizeForComp(getFirstComponent());
    myFirstIsFixed = true;
  }

  public void freeAll() {
    myFirstIsFixed = false;
  }

  @Override
  public void doLayout() {
    int total = getSizeForComp(this);
    if (myFirstIsFixed) {
      myProportion = ((float)myFirstFixedSize) / (total - getDividerWidth());
    }
    super.doLayout();
  }

  @Override
  public void setProportion(float proportion) {
    boolean firstIsFixed = myFirstIsFixed;
    myFirstIsFixed = false;
    super.setProportion(proportion);
    
    int total = getSizeForComp(this);
    if (firstIsFixed) {
      myFirstFixedSize = (int) (myProportion * (total - getDividerWidth()));
      myFirstIsFixed = true;
    }
  }
}
