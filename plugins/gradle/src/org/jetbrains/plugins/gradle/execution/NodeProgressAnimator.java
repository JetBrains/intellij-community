// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeUi;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * @author Vladislav.Soroka
 */
public class NodeProgressAnimator implements Runnable, Disposable {
  private static final int FRAMES_COUNT = 8;
  private static final int MOVIE_TIME = 800;
  private static final int FRAME_TIME = MOVIE_TIME / FRAMES_COUNT;

  public static final Icon[] FRAMES = new Icon[FRAMES_COUNT];

  private long myLastInvocationTime = -1;

  private Alarm myAlarm;
  private SimpleNode myCurrentNode;
  private AbstractTreeBuilder myTreeBuilder;

  public NodeProgressAnimator(AbstractTreeBuilder builder) {
    Disposer.register(builder, this);
    init(builder);
  }

  static {
    FRAMES[0] = AllIcons.Process.State.GreyProgr_1;
    FRAMES[1] = AllIcons.Process.State.GreyProgr_2;
    FRAMES[2] = AllIcons.Process.State.GreyProgr_3;
    FRAMES[3] = AllIcons.Process.State.GreyProgr_4;
    FRAMES[4] = AllIcons.Process.State.GreyProgr_5;
    FRAMES[5] = AllIcons.Process.State.GreyProgr_6;
    FRAMES[6] = AllIcons.Process.State.GreyProgr_7;
    FRAMES[7] = AllIcons.Process.State.GreyProgr_8;
  }

  public static int getCurrentFrameIndex() {
    return (int) ((System.currentTimeMillis() % MOVIE_TIME) / FRAME_TIME);
  }

  public static Icon getCurrentFrame() {
    return FRAMES[getCurrentFrameIndex()];
  }

  /**
   * Initializes animator: creates alarm and sets tree builder
   * @param treeBuilder tree builder
   */
  protected void init(final AbstractTreeBuilder treeBuilder) {
    myAlarm = new Alarm();
    myTreeBuilder = treeBuilder;
  }

  public SimpleNode getCurrentNode() {
    return myCurrentNode;
  }

  @Override
  public void run() {
    if (myCurrentNode != null) {
      final long time = System.currentTimeMillis();
      // optimization:
      // we shouldn't repaint if this frame was painted in current interval
      if (time - myLastInvocationTime >= FRAME_TIME) {
        repaintSubTree();
        myLastInvocationTime = time;
      }
    }
    scheduleRepaint();
  }

  public void setCurrentNode(@Nullable final SimpleNode node) {
    myCurrentNode = node;
    scheduleRepaint();
  }

  public void stopMovie() {
    repaintSubTree();
    setCurrentNode(null);
    cancelAlarm();
  }


  @Override
  public void dispose() {
    myTreeBuilder = null;
    myCurrentNode = null;
    cancelAlarm();
  }

  private void cancelAlarm() {
    if (myAlarm != null) {
      myAlarm.cancelAllRequests();
      myAlarm = null;
    }
  }

  private void repaintSubTree() {
    if (myTreeBuilder != null && myCurrentNode != null) {
      repaintWithParents(myCurrentNode);
    }
  }

  public void repaintWithParents(@NotNull SimpleNode element) {
    SimpleNode current = element;
    do {
      DefaultMutableTreeNode node = myTreeBuilder.getNodeForElement(current);
      if (node != null) {
        final AbstractTreeUi treeUi = myTreeBuilder.getUi();
        treeUi.addSubtreeToUpdate(node, false);
      }
      current = current.getParent();
    }
    while (current != null);
  }


  private void scheduleRepaint() {
    if (myAlarm == null) {
      return;
    }
    myAlarm.cancelAllRequests();
    if (myCurrentNode != null) {
      myAlarm.addRequest(this, FRAME_TIME);
    }
  }

}
