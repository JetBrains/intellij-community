package com.intellij.xdebugger;

import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.frame.XValuePresenter;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodePresentationConfigurator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.concurrent.Semaphore;

public class XTestValueNode extends XValueNodePresentationConfigurator.ConfigurableXValueNodeImpl {
  public String myName;
  public String myType;
  public String myValue;
  public boolean myHasChildren;

  public XFullValueEvaluator myFullValueEvaluator;

  private final Semaphore myFinished = new Semaphore(0);

  @Override
  public void applyPresentation(@Nullable Icon icon,
                                @Nullable String type,
                                @Nullable String value,
                                @NotNull XValuePresenter valuePresenter,
                                boolean hasChildren,
                                boolean expand) {
    myType = type;
    myValue = value;
    myHasChildren = hasChildren;

    myFinished.release();
  }

  @Override
  public void setFullValueEvaluator(@NotNull XFullValueEvaluator fullValueEvaluator) {
    myFullValueEvaluator = fullValueEvaluator;
  }

  @Override
  public boolean isObsolete() {
    return false;
  }

  public void waitFor(long timeoutInMillis) throws InterruptedException {
    if (!XDebuggerTestUtil.waitFor(myFinished, timeoutInMillis)) throw new AssertionError("Waiting timed out");
  }
}
