package org.jetbrains.debugger;

import com.intellij.openapi.util.Conditions;
import com.intellij.util.Consumer;
import com.intellij.xdebugger.XTestValueNode;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueGroup;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import javax.swing.*;

public class TestValueNode extends XTestValueNode {
  private final AsyncPromise<XTestValueNode> result = new AsyncPromise<XTestValueNode>();

  private volatile Content children;

  @NotNull
  public Promise<XTestValueNode> getResult() {
    return result;
  }

  @NotNull
  public Promise<Content> loadChildren(@NotNull XValue value) {
    TestCompositeNode childrenNode = new TestCompositeNode();
    value.computeChildren(childrenNode);
    return childrenNode.loadContent(Conditions.<XValueGroup>alwaysFalse(), Conditions.<VariableView>alwaysFalse())
      .done(new Consumer<Content>() {
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

    result.setResult(this);
  }
}