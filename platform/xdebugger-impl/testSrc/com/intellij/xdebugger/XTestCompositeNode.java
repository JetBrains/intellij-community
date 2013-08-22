package com.intellij.xdebugger;

import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueChildrenProvider;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class XTestCompositeNode extends XTestContainer<XValue> implements XCompositeNode {
  private volatile boolean myAlreadySorted;

  @Override
  public void addChildren(@NotNull XValueChildrenProvider children, boolean last) {
    final List<XValue> list = new ArrayList<XValue>();
    for (int i = 0; i < children.size(); i++) {
      list.add(children.getValue(i));
    }
    addChildren(list, last);
  }

  @Override
  public boolean isAlreadySorted() {
    return myAlreadySorted;
  }

  @Override
  public void setAlreadySorted(boolean alreadySorted) {
    myAlreadySorted = alreadySorted;
  }
}
