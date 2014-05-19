package org.jetbrains.debugger;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import com.intellij.xdebugger.XTestValueNode;
import com.intellij.xdebugger.frame.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class TestCompositeNode implements XCompositeNode {
  private final AsyncResult<XValueChildrenList> result = new AsyncResult<XValueChildrenList>();
  private final XValueChildrenList children = new XValueChildrenList();

  private final XValueGroup valueGroup;
  public Content content;

  public TestCompositeNode() {
    valueGroup = null;
  }

  public TestCompositeNode(@NotNull XValueGroup group) {
    valueGroup = group;
  }

  @NotNull
  public XValueGroup getValueGroup() {
    return valueGroup;
  }

  @Override
  public void addChildren(@NotNull XValueChildrenList children, boolean last) {
    for (XValueGroup group : children.getTopGroups()) {
      this.children.addTopGroup(group);
    }
    for (int i = 0; i < children.size(); i++) {
      this.children.add(children.getName(i), children.getValue(i));
    }
    for (XValueGroup group : children.getBottomGroups()) {
      this.children.addBottomGroup(group);
    }

    if (last) {
      result.setDone(this.children);
    }
  }

  @Override
  public void tooManyChildren(int remaining) {
    result.setDone();
  }

  @Override
  public void setAlreadySorted(boolean alreadySorted) {
  }

  @Override
  public void setErrorMessage(@NotNull String errorMessage) {
    result.reject(errorMessage);
  }

  @Override
  public void setErrorMessage(@NotNull String errorMessage, @Nullable XDebuggerTreeNodeHyperlink link) {
    setErrorMessage(errorMessage);
  }

  @Override
  public void setMessage(@NotNull String message, @Nullable Icon icon, @NotNull SimpleTextAttributes attributes, @Nullable XDebuggerTreeNodeHyperlink link) {
  }

  @Override
  public boolean isObsolete() {
    return false;
  }

  @NotNull
  public AsyncResult<XValueChildrenList> getResult() {
    return result;
  }

  @NotNull
  public AsyncResult<Content> getContent(final @NotNull Condition<XValueGroup> nestedChildrenResolveCondition) {
    assert content == null;

    final AsyncResult<Content> compoundResult = new AsyncResult<Content>();
    content = new Content();
    result.doWhenDone(new Consumer<XValueChildrenList>() {
      @Override
      public void consume(XValueChildrenList children) {
        ActionCallback.Chunk chunk = new ActionCallback.Chunk();
        resolveGroups(children.getTopGroups(), content.topGroups, chunk);
        for (int i = 0; i < children.size(); i++) {
          XValue value = children.getValue(i);
          XTestValueNode node = new XTestValueNode();
          node.myName = children.getName(i);
          value.computePresentation(node, XValuePlace.TREE);
          content.values.add(node);
          chunk.add(node.getResult());
        }
        resolveGroups(children.getBottomGroups(), content.bottomGroups, chunk);

        chunk.create().doWhenDone(new Runnable() {
          @Override
          public void run() {
            compoundResult.setDone(content);
          }
        }).notifyWhenRejected(compoundResult);
      }

      private void resolveGroups(@NotNull List<XValueGroup> valueGroups, @NotNull List<TestCompositeNode> resultNodes, @NotNull ActionCallback.Chunk chunk) {
        for (XValueGroup group : valueGroups) {
          TestCompositeNode node = new TestCompositeNode(group);
          boolean computeChildren = nestedChildrenResolveCondition.value(group);
          if (computeChildren) {
            group.computeChildren(node);
          }
          resultNodes.add(node);
          if (computeChildren) {
            chunk.add(node.getContent(Conditions.<XValueGroup>alwaysFalse()));
          }
        }
      }
    }).notifyWhenRejected(compoundResult);
    return compoundResult;
  }

  public static final class Content {
    public final List<TestCompositeNode> topGroups = new SmartList<TestCompositeNode>();
    public final List<XTestValueNode> values = new SmartList<XTestValueNode>();
    public final List<TestCompositeNode> bottomGroups = new SmartList<TestCompositeNode>();
  }
}