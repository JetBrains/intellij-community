/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.xdebugger;

import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodePresentationConfigurator;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValuePresentationUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.concurrent.Semaphore;

public class XTestValueNode extends XValueNodePresentationConfigurator.ConfigurableXValueNodeImpl {
  public Icon myIcon;
  public String myName;
  public String myType;
  public String myValue;
  public boolean myHasChildren;

  public XFullValueEvaluator myFullValueEvaluator;

  private final Semaphore myFinished = new Semaphore(0);

  @Override
  public void applyPresentation(@Nullable Icon icon,
                                @NotNull XValuePresentation valuePresentation,
                                boolean hasChildren) {
    myIcon = icon;
    myType = valuePresentation.getType();
    myValue = XValuePresentationUtil.computeValueText(valuePresentation);
    myHasChildren = hasChildren;

    myFinished.release();
  }

  @Override
  public void setFullValueEvaluator(@NotNull XFullValueEvaluator fullValueEvaluator) {
    myFullValueEvaluator = fullValueEvaluator;
  }

  public void waitFor(long timeoutInMillis) {
    if (!XDebuggerTestUtil.waitFor(myFinished, timeoutInMillis)) {
      throw new AssertionError("Waiting timed out");
    }
  }

  @Override
  public String toString() {
    return myName + "{" + myType + "} = " + myValue + ", hasChildren = " + myHasChildren;
  }
}
