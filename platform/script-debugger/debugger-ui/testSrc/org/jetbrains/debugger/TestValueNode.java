package org.jetbrains.debugger;

import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.Conditions;
import com.intellij.util.Consumer;
import com.intellij.xdebugger.XTestValueNode;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueGroup;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class TestValueNode extends XTestValueNode {
  private final AsyncResult<XTestValueNode> result = new AsyncResult<XTestValueNode>();

  private volatile Content children;

  @NotNull
  public AsyncResult<XTestValueNode> getResult() {
    return result;
  }

  @NotNull
  public AsyncResult<Content> loadChildren(@NotNull XValue value) {
    TestCompositeNode childrenNode = new TestCompositeNode();
    value.computeChildren(childrenNode);
    return childrenNode.loadContent(Conditions.<XValueGroup>alwaysFalse(), Conditions.<VariableView>alwaysFalse()).doWhenDone(new Consumer<Content>() {
      @Override
      public void consume(Content content) {
        children = content;
      }
    });
  }

  @Nullable
  public Content getChildren() {
    return children;
  }

  @Override
  public void applyPresentation(@Nullable Icon icon, @NotNull XValuePresentation valuePresentation, boolean hasChildren) {
    super.applyPresentation(icon, valuePresentation, hasChildren);

    result.setDone(this);
  }
}