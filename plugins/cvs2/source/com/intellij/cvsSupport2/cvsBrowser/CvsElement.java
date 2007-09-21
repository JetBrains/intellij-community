package com.intellij.cvsSupport2.cvsBrowser;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.io.File;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Future;

public class CvsElement extends DefaultMutableTreeNode {

  protected RemoteResourceDataProvider myDataProvider;
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

  public void setModel(CvsTreeModel model) { myModel = model; }

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
  };

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

  private class MyGetContentCallback implements GetContentCallback {
    private final LoadingNode myLoadingNode;

    public MyGetContentCallback(LoadingNode loadingNode) {
      myLoadingNode = loadingNode;
    }

    public void fillDirectoryContent(final List<CvsElement> content) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            for (int i = 0; i < content.size(); i++) {
              insert(content.get(i), i);
            }
            getModel().nodeStructureChanged(CvsElement.this);
            if (getParent() == null) {
              getModel().selectRoot();
            }
          }
        });
    }

    public void finished() {
      myPeriodAlarm.cancelAllRequests();
      if (isNodeChild(myLoadingNode)) {
        remove(myLoadingNode);
      }
      myModel.getCvsTree().getTree().setEnabled(true);
      myLoadingThreadFuture = null;
    }

    public void loginAborted() {
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
    for (int i = 0; i < nodes.length; i++) {
      Object node = nodes[i];
      if (node instanceof CvsElement) {
        ((CvsElement)node).release();
      }
    }
  }

  public Component getTree() {
    return myModel.getCvsTree().getTree();
  }
}
