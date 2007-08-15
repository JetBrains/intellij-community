package com.intellij.execution.junit2.ui.model;

import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.testframework.Filter;
import com.intellij.execution.junit2.TestProgress;
import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.TestingStatus;
import com.intellij.execution.junit2.segments.DispatchListener;
import com.intellij.execution.junit2.segments.PacketExtractorBase;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.junit2.ui.Animator;
import com.intellij.execution.junit2.ui.TestProxyClient;
import com.intellij.execution.testframework.TestTreeView;
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.List;
import java.util.ArrayList;

public class JUnitRunningModel implements TestFrameworkRunningModel {
  private final TestProgress myProgress;
  private final TestProxy myRoot;
  private final TestingStatus myStatus;
  private final JUnitConsoleProperties myProperties;
  private final MyTreeSelectionListener myTreeListener = new MyTreeSelectionListener();
  private JTree myTreeView;
  private TestTreeBuilder myTreeBuilder;

  private final JUnitListenersNotifier myNotifier = new JUnitListenersNotifier();
  private final Animator myAnimator = new Animator();
  private PacketExtractorBase myPacketExtractor;
  private List<ModelListener> myListeners = new ArrayList<ModelListener>();

  public JUnitRunningModel(final TestProxy root, final TestingStatus status, final JUnitConsoleProperties properties) {
    myRoot = root;
    myStatus = status;
    myProperties = properties;

    myRoot.setEventsConsumer(myNotifier);

    myProgress = new TestProgress(this);
    myStatus.setListener(myNotifier);
  }

  public TestTreeBuilder getTreeBuilder() {
    return myTreeBuilder;
  }

  public TestingStatus getStatus() { return myStatus; }

  public void attachToTree(final TestTreeView treeView) {
    myTreeBuilder = new TestTreeBuilder(treeView, this, myProperties);
    myAnimator.setModel(this);
    myTreeView = treeView;
    selectTest(getRoot());
    myTreeListener.install();
  }

  public void dispose() {
    myTreeListener.dispose();
    Disposer.dispose(myTreeBuilder);
    myNotifier.onDispose(this);
    if (myPacketExtractor != null)
      myPacketExtractor.setDispatchListener(DispatchListener.DEAF);
    for (ModelListener listener : myListeners) {
      listener.onDispose();
    }
  }

  public TestTreeView getTreeView() {
    return (TestTreeView) myTreeBuilder.getTree();
  }

  public boolean hasTestSuites() {
    return myRoot.hasChildSuites();
  }

  public TestProgress getProgress() {
    return myProgress;
  }

  public TestProxy getRoot() {
    return myRoot;
  }

  public void selectAndNotify(final AbstractTestProxy testProxy) {
    selectTest((TestProxy)testProxy);
    myNotifier.onTestSelected((TestProxy)testProxy);
  }

  public Project getProject() {
    return myProperties.getProject();
  }

  public JUnitConsoleProperties getProperties() { return myProperties; }

  public void selectTest(final TestProxy test) {
    if (test == null) return;
    final TreePath pathToNode = pathToTest(test, true);
    if (pathToNode == null) return;
    myTreeView.setSelectionPath(pathToNode);
    myTreeView.makeVisible(pathToNode);
    myTreeView.scrollPathToVisible(pathToNode);
  }


  public void collapse(final TestProxy test) {
    if (test == getRoot())
      return;
    final TreePath path = pathToTest(test, false);
    if (path == null) return;
    myTreeView.collapsePath(path);
  }

  public DispatchListener getNotifier() {
    return myNotifier;
  }

  public void setFilter(final Filter filter) {
    final TestTreeStructure treeStructure = getStructure();
    treeStructure.setFilter(filter);
    myTreeBuilder.updateFromRoot();
  }

  public void addListener(ModelListener l) {
    myListeners.add(l);
  }

  public boolean isRunning() {
    return getStatus().isRunning();
  }

  private TestTreeStructure getStructure() {
    return (TestTreeStructure)myTreeBuilder.getTreeStructure();
  }

  public void addListener(final JUnitListener listener) {
    myNotifier.addListener(listener);
  }

  public void removeListener(final JUnitListener listener) {
    myNotifier.removeListener(listener);
  }

  public void onUIBuilt() {
    myNotifier.onTestSelected(myRoot);
  }

  private TreePath pathToTest(final TestProxy test, final boolean expandIfCollapsed) {
    final TestTreeBuilder treeBuilder = getTreeBuilder();
    DefaultMutableTreeNode node = treeBuilder.getNodeForElement(test);
    if (node == null && !expandIfCollapsed)
      return null;
    node = treeBuilder.ensureTestVisible(test);
    if (node == null)
      return null;
    return TreeUtil.getPath((TreeNode) myTreeView.getModel().getRoot(), node);
  }

  public boolean hasInTree(final AbstractTestProxy test) {
    return getStructure().getFilter().shouldAccept(test);
  }

  public void attachTo(final PacketExtractorBase packetExtractor) {
    myPacketExtractor = packetExtractor;
    myPacketExtractor.setDispatchListener(myNotifier);
  }

  public JUnitConfiguration getConfiguration() {
    return myProperties.getConfiguration();
  }

  private class MyTreeSelectionListener extends FocusAdapter implements TreeSelectionListener {
    public void valueChanged(final TreeSelectionEvent e) {
      final TestProxy test = TestProxyClient.from(e.getPath());
      if (myTreeView.isFocusOwner())
        myNotifier.onTestSelected(test);
    }

    public void focusGained(final FocusEvent e) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              myNotifier.onTestSelected((TestProxy)getTreeView().getSelectedTest());
            }
          });
    }

    public void install() {
      myTreeView.addTreeSelectionListener(this);
      myTreeView.addFocusListener(this);
    }

    public void dispose() {
      myTreeView.removeTreeSelectionListener(this);
      myTreeView.removeFocusListener(this);
    }
  }
}
