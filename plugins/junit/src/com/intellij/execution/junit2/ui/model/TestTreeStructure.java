package com.intellij.execution.junit2.ui.model;

import com.intellij.execution.testframework.Filter;
import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import org.jetbrains.annotations.NotNull;

class TestTreeStructure extends AbstractTreeStructure {
  private final TestProxy myRootTest;
  private final JUnitConsoleProperties myProperties;
  private SpecialNode mySpecialNode;

  public void setFilter(final Filter filter) {
    myFilter = filter;
  }

  public Filter getFilter() {
    return myFilter;
  }

  private Filter myFilter = Filter.NO_FILTER;

  public TestTreeStructure(final TestProxy rootTest, final JUnitConsoleProperties properties) {
    myRootTest = rootTest;
    myProperties = properties;
  }

  public void setSpecialNode(final SpecialNode specialNode) { mySpecialNode = specialNode; }

  public Object getRootElement() {
    return myRootTest;
  }

  public Object[] getChildElements(final Object element) {
    final AbstractTestProxy[] children = ((TestProxy)element).selectChildren(myFilter);
    if (element == myRootTest) {
      if (children.length == 0 && myRootTest.getState().isPassed()) {
        mySpecialNode.setVisible(true);
        return mySpecialNode.asArray();
      }
      else {
        mySpecialNode.setVisible(false);
      }
    }

    return children;
  }

  public Object getParentElement(final Object element) {
    final TestProxy testProxy = (TestProxy)element;
    return testProxy.getParent();
  }

  @NotNull
  public TestProxyDescriptor createDescriptor(final Object element, final NodeDescriptor parentDescriptor) {
    final TestProxy testProxy = (TestProxy)element;
    return new TestProxyDescriptor(myProperties.getProject(), parentDescriptor, testProxy);
  }

  public void commit() {
  }

  public boolean hasSomethingToCommit() {
    return false;
  }
}
