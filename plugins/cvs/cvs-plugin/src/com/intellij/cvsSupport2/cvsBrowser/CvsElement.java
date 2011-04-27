/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsListenerWithProgress;
import com.intellij.cvsSupport2.ui.CvsTabbedWindow;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.io.File;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Future;

public class CvsElement extends DefaultMutableTreeNode implements CvsTabbedWindow.DeactivateListener {

  protected RemoteResourceDataProvider myDataProvider;
  private boolean myLoading;
  protected String myName;
  protected String myPath;
  private final Icon myIcon;
  private final Icon myExpandedIcon;
  private boolean myCanBecheckedOut = true;
  private CvsTreeModel myModel;
  private final Project myProject;
  private Future<?> myLoadingThreadFuture;

  private final Alarm myPeriodAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  public CvsElement(Icon icon, Icon expandedIcon, Project project) {
    myIcon = icon;
    myExpandedIcon = expandedIcon;
    myProject = project;
  }

  public CvsElement(Icon icon, Project project) {
    this(icon, icon, project);
  }

  public void setModel(CvsTreeModel model) {
    myModel = model;
  }

  public void deactivated() {
    myPeriodAlarm.cancelAllRequests();
  }

  public void setDataProvider(RemoteResourceDataProvider dataProvider) {
    myDataProvider = dataProvider;
  }

  public void setName(String name) {
    myName = name;
  }

  public String getName() {
    return myName;
  }

  public void setPath(String path) {
    if (path.startsWith("./")) {
      myPath = path.substring(2);
    }
    else {
      myPath = path;
    }
  }

  public TreeNode getChildAt(int childIndex) {
    return (TreeNode)getMyChildren().get(childIndex);
  }

  public int getChildCount() {
    return getMyChildren().size();
  }

  public int getIndex(TreeNode node) {
    return getMyChildren().indexOf(node);
  }

  public boolean getAllowsChildren() {
    if (alreadyLoaded()) {
      return getChildCount() > 0;
    }
    else {
      return !myDataProvider.equals(RemoteResourceDataProvider.NOT_EXPANDABLE);
    }
  }

  private boolean alreadyLoaded() {
    return children != null;
  }

  public Enumeration children() {
    return new Vector(getMyChildren()).elements();
  }

  private List getMyChildren() {
    if (children == null) {
      final LoadingNode loadingNode = new LoadingNode();

      getModel().insertNodeInto(loadingNode, this, 0);
      myLoading = true;
      myModel.getCvsTree().getTree().setEnabled(false);
      myLoadingThreadFuture = ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        public void run() {
          myDataProvider.fillContentFor(CvsElement.this, myProject, new MyGetContentCallback(loadingNode));
        }
      });

      final Runnable periodRequest = new Runnable() {
        public void run() {
          if (isNodeChild(loadingNode)) {
            loadingNode.updatePeriod();
            ((DefaultTreeModel)myModel.getCvsTree().getTree().getModel()).nodeChanged(loadingNode);
            myPeriodAlarm.addRequest(this, 200);
          }

        }
      };
      myPeriodAlarm.addRequest(periodRequest, 200);
      myModel.getCvsTree().addListener(this);
    }

    return children;
  }

  public CvsTreeModel getModel() {
    return myModel;
  }

  public String toString() {
    return myName;
  }

  public Icon getIcon(boolean expanded) {
    return expanded ? myExpandedIcon : myIcon;
  }

  public String getElementPath() {
    return myPath;
  }

  public String getCheckoutPath() {
    return getElementPath();
  }

  public boolean canBeCheckedOut() {
    return myCanBecheckedOut;
  }

  public void cannotBeCheckedOut() {
    myCanBecheckedOut = false;
  }

  public String getCheckoutDirectoryName() {
    return new File(getCheckoutPath()).getName();
  }

  public VirtualFile getVirtualFile() {
    return null;
  }

  public String createPathForChild(String name) {
    return getElementPath() + "/" + name;
  }

  public File getCvsLightFile() {
    return null;
  }

  private class MyGetContentCallback implements GetContentCallback, CvsTabbedWindow.DeactivateListener {
    private final LoadingNode myLoadingNode;
    private CvsListenerWithProgress myListener;

    public MyGetContentCallback(LoadingNode loadingNode) {
      myLoadingNode = loadingNode;
      myModel.getCvsTree().addListener(this);
    }

    public void deactivated() {
      if (myListener != null) {
        myListener.indirectCancel();
      }
    }

    public void useForCancel(final CvsListenerWithProgress listener) {
      myListener = listener;
    }

    public void appendDirectoryContent(final List<CvsElement> directoryContent) {
      fill(directoryContent);
    }

    public void fillDirectoryContent(final List<CvsElement> content) {
      //fill(content);
    }

    private void fill(final List<CvsElement> content) {
      myPeriodAlarm.cancelAllRequests();
      SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            final int offset = getChildCount();
            for (final CvsElement newChild : content) {
              final int insertionPoint;
              if (children == null) {
                insertionPoint = -1;
              } else {
                insertionPoint = Collections.binarySearch(children, newChild, CvsElementComparator.INSTANCE);
              }
              if (insertionPoint < 0) {
                insert(newChild, -(insertionPoint + 1));
              }
            }
            if ((offset <= 1) && isNodeChild(myLoadingNode)) {
              remove(myLoadingNode);
            }
            getModel().reload(CvsElement.this);
            getModel().getCvsTree().getTree().expandPath(new TreePath(getModel().getPathToRoot(CvsElement.this)));
          }
        });
    }

    public void finished() {
      if (getParent() == null) {
        getModel().selectRoot();
      }
      myModel.getCvsTree().removeListener(this);
      myLoading = false;

      myPeriodAlarm.cancelAllRequests();
      myModel.getCvsTree().getTree().setEnabled(true);
      myLoadingThreadFuture = null;
      SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            if (isNodeChild(myLoadingNode)) {
              remove(myLoadingNode);
              getModel().reload(CvsElement.this);
            }
          }
      });
    }

    public void loginAborted() {
      myModel.getCvsTree().removeListener(this);

      CvsTree cvsTree = myModel.getCvsTree();
      if (cvsTree != null) {
        cvsTree.onLoginAborted();
      }
      else {
        throw new LoginAbortedException();
      }
    }
  }

  public void release() {
    if (myLoadingThreadFuture != null) {
      myLoadingThreadFuture.cancel(true);
    }
    if (children == null) return;
    Object[] nodes = children.toArray();
    for (Object node : nodes) {
      if (node instanceof CvsElement) {
        ((CvsElement)node).release();
      }
    }
  }

  public Component getTree() {
    return myModel.getCvsTree().getTree();
  }

  public boolean isLoading() {
    return myLoading;
  }
}
