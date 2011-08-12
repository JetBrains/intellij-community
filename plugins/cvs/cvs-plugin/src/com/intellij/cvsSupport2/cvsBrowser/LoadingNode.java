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
package com.intellij.cvsSupport2.cvsBrowser;

import com.intellij.CommonBundle;
import com.intellij.cvsSupport2.ui.CvsTabbedWindow;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.Alarm;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.List;

/**
 * @author lesya
 */
class LoadingNode extends DefaultMutableTreeNode {
  private String myText = CommonBundle.getLoadingTreeNodeText();
  private int myPeriod = 0;

  private final Icon[] myIcons = new Icon[] {
    IconLoader.getIcon("/process/step_1.png"),
    IconLoader.getIcon("/process/step_2.png"),
    IconLoader.getIcon("/process/step_3.png"),
    IconLoader.getIcon("/process/step_4.png"),
    IconLoader.getIcon("/process/step_5.png"),
    IconLoader.getIcon("/process/step_6.png"),
    IconLoader.getIcon("/process/step_7.png"),
    IconLoader.getIcon("/process/step_8.png"),
    IconLoader.getIcon("/process/step_9.png"),
    IconLoader.getIcon("/process/step_10.png"),
    IconLoader.getIcon("/process/step_11.png"),
    IconLoader.getIcon("/process/step_12.png")
  };
  private boolean stopped = false;
  private Runnable myPeriodRequest;
  private final DefaultTreeModel myModel;

  public LoadingNode(DefaultTreeModel model) {
    myModel = model;
  }

  public boolean getAllowsChildren() {
    return false;
  }

  public String toString() {
    return myText;
  }

  public void updatePeriod() {
    myPeriod++;
    myPeriod %= 12;
    myModel.nodeChanged(this);
  }

  public Icon getIcon() {
    return myIcons[myPeriod];
  }

  public static class Manager implements CvsTabbedWindow.DeactivateListener{

    public Manager() {
    }

    private final Alarm myPeriodAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
    private final List<LoadingNode> loadingNodes = new ArrayList();

    public void addTo(final DefaultTreeModel model, MutableTreeNode parent) {
      if (!ApplicationManager.getApplication().isDispatchThread()) {
        throw new RuntimeException();
      }
      final LoadingNode loadingNode = new LoadingNode(model);
      loadingNodes.add(loadingNode); // no synchronization necessary because only called from event thread.
      model.insertNodeInto(loadingNode, parent, 0);
      loadingNode.start(myPeriodAlarm);
    }

    public void removeFrom(MutableTreeNode parent) {
      for (LoadingNode loadingNode : loadingNodes) {
        if (loadingNode.getParent() == parent) {
          loadingNode.stop(myPeriodAlarm);
          break;
        }
      }
    }

    @Override
    public void deactivated() {
      for (LoadingNode loadingNode : loadingNodes) {
        loadingNode.stop(myPeriodAlarm);
      }
    }
  }

  private void stop(Alarm periodAlarm) {
    stopped = true;
    periodAlarm.cancelRequest(myPeriodRequest);
    final TreeNode parent = getParent();
    removeFromParent();
    myModel.reload(parent);
  }

  private void start(final Alarm alarm) {
    myPeriodRequest = new Runnable() {
      public void run() {
        if (getParent() != null) {
          updatePeriod();
          if (!stopped) {
            alarm.addRequest(this, 100);
          }
        }
      }
    };
    alarm.addRequest(myPeriodRequest, 100);
  }
}
