/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.DirectoryContent;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsListenerWithProgress;
import com.intellij.cvsSupport2.ui.CvsTabbedWindow;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TreeUIHelper;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.*;
import java.util.List;

public class CvsTree extends JPanel implements CvsTabbedWindow.DeactivateListener, ChildrenLoader<CvsElement> {
  private CvsElement[] myCurrentSelection = CvsElement.EMPTY_ARRAY;
  private Tree myTree;
  private DefaultTreeModel myModel;
  private CvsRootConfiguration myCvsRootConfiguration = null;
  private final Observable mySelectionObservable = new AlwaysNotifiedObservable();
  private final boolean myShowFiles;
  private final Consumer<VcsException> myErrorCallback;
  private final boolean myAllowRootSelection;
  private final boolean myShowModules;
  private final Project myProject;
  @JdkConstants.TreeSelectionMode private final int mySelectionMode;
  private final LoadingNode.Manager myLoadingNodeManager = new LoadingNode.Manager();

  @NonNls public static final String SELECTION_CHANGED = "Selection Changed";

  public CvsTree(Project project, boolean allowRootSelection, @JdkConstants.TreeSelectionMode int selectionMode,
                 boolean showModules, boolean showFiles, Consumer<VcsException> errorCallback) {
    super(new BorderLayout());
    myProject = project;
    mySelectionMode = selectionMode;
    myShowModules = showModules;
    myAllowRootSelection = allowRootSelection;
    myShowFiles = showFiles;
    myErrorCallback = errorCallback;
    setSize(500, 500);
    addListener(myLoadingNodeManager);
  }

  private void addSelectionListener() {
    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        setCurrentSelection(myTree.getSelectionPaths());
      }
    });
  }

  public void setCvsRootConfiguration(@NotNull CvsRootConfiguration cvsRootConfiguration) {
    if (cvsRootConfiguration.equals(myCvsRootConfiguration)) {
      return;
    }
    myCvsRootConfiguration = cvsRootConfiguration;
    final TreeNode root = createRoot(myProject);
    myModel.setRoot(root);
  }

  private void setCurrentSelection(TreePath[] paths) {
    final ArrayList<CvsElement> selection = new ArrayList<>();
    if (paths != null) {
      for (TreePath path : paths) {
        final Object selectedObject = path.getLastPathComponent();
        if (!(selectedObject instanceof CvsElement)) continue;
        final CvsElement cvsElement = (CvsElement)selectedObject;
        if (cvsElement.getElementPath().equals(".") && !myAllowRootSelection) continue;
        selection.add(cvsElement);
      }
    }
    myCurrentSelection = selection.toArray(new CvsElement[selection.size()]);
    mySelectionObservable.notifyObservers(SELECTION_CHANGED);
  }

  private TreeNode createRoot(Project project) {
    if (myCvsRootConfiguration == null) {
      return new DefaultMutableTreeNode();
    }
    final String rootName = myCvsRootConfiguration.toString();
    final CvsElement result = CvsElementFactory.FOLDER_ELEMENT_FACTORY.createElement(rootName, myCvsRootConfiguration, project);
    result.setDataProvider(new RootDataProvider(myCvsRootConfiguration));
    result.setPath(".");
    result.cannotBeCheckedOut();
    result.setChildrenLoader(this);
    return result;
  }

  public CvsElement[] getCurrentSelection() {
    return myCurrentSelection;
  }

  public void selectRoot() {
    myTree.setSelectionPath(myTree.getPathForRow(0));
  }

  public void addSelectionObserver(Observer observer) {
    mySelectionObservable.addObserver(observer);
  }

  public Tree getTree() {
    return myTree;
  }

  public void init() {
    final TreeNode root = createRoot(myProject);
    myModel = new DefaultTreeModel(root, true);
    myTree = new Tree(myModel);
    add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);

    myTree.getSelectionModel().setSelectionMode(mySelectionMode);
    myTree.setCellRenderer(new Cvs2Renderer());
    addSelectionListener();

    final TreeUIHelper uiHelper = TreeUIHelper.getInstance();
    uiHelper.installTreeSpeedSearch(myTree);
    TreeUtil.installActions(myTree);

    myTree.requestFocus();
  }

  private static class AlwaysNotifiedObservable extends Observable{
    @Override
    public void notifyObservers(Object arg) {
      setChanged();
      super.notifyObservers(arg);
    }
  }

  private final List<CvsTabbedWindow.DeactivateListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public void addListener(final CvsTabbedWindow.DeactivateListener listener) {
    synchronized (myListeners) {
      if (! myListeners.contains(listener)) {
        myListeners.add(listener);
      }
    }
  }

  public void removeListener(final CvsTabbedWindow.DeactivateListener listener) {
    synchronized (myListeners) {
      myListeners.remove(listener);
    }
  }

  @Override
  public void deactivated() {
    mySelectionObservable.deleteObservers();
    synchronized (myListeners) {
      for (CvsTabbedWindow.DeactivateListener listener : myListeners) {
        listener.deactivated();
      }
      myListeners.clear();
    }
  }

  @Override
  public void loadChildren(final CvsElement element) {
    element.setLoading(true);
    myLoadingNodeManager.addTo(myModel, element);
    final Application application = ApplicationManager.getApplication();
    final ModalityState modalityState = application.getCurrentModalityState();
    application.executeOnPooledThread(() -> {
      final RemoteResourceDataProvider dataProvider = element.getDataProvider();
      dataProvider.fillContentFor(new MyGetContentCallback(element, modalityState, myProject), myErrorCallback);
    });
  }

  private class MyGetContentCallback implements GetContentCallback, CvsTabbedWindow.DeactivateListener {

    private final CvsElement myParentNode;
    private final ModalityState myModalityState;
    private final Project myProject;
    private CvsListenerWithProgress myListener;
    private long timeStamp = 0L;
    private int waitTime = 100;
    private TreePath mySelectionPath;

    public MyGetContentCallback(CvsElement parentNode, ModalityState modalityState, Project project) {
      myParentNode = parentNode;
      myModalityState = modalityState;
      myProject = project;
      addListener(this);
      ApplicationManager.getApplication().invokeLater(() -> {
        mySelectionPath = myTree.getSelectionPath();
        if (mySelectionPath == null) {
          selectRoot();
          mySelectionPath = myTree.getSelectionPath();
        }
      }, myModalityState);
    }

    @Override
    public Project getProject() {
      return myProject;
    }

    @Override
    public String getElementPath() {
      return myParentNode.getElementPath();
    }

    @Override
    public ModalityState getModalityState() {
      return myModalityState;
    }

    @Override
    public void deactivated() {
      if (myListener != null) {
        myListener.indirectCancel();
      }
    }

    @Override
    public void useForCancel(final CvsListenerWithProgress listener) {
      myListener = listener;
    }

    @Override
    public void appendDirectoryContent(final DirectoryContent directoryContent) {
      ApplicationManager.getApplication().invokeLater(() -> {
        final TreePath selectionPath = myTree.getSelectionPath();
        if (selectionPath != null) {
          mySelectionPath = selectionPath;
        }
        if (myShowModules) {
          process(directoryContent.getSubModulesRaw(), CvsElementFactory.MODULE_ELEMENT_FACTORY,
                  new ModuleDataProvider(myCvsRootConfiguration));
        }
        process(directoryContent.getSubDirectoriesRaw(), CvsElementFactory.FOLDER_ELEMENT_FACTORY,
                myParentNode.getDataProvider().getChildrenDataProvider());
        if (myShowFiles) {
          process(directoryContent.getFilesRaw(), CvsElementFactory.FILE_ELEMENT_FACTORY, RemoteResourceDataProvider.NOT_EXPANDABLE);
        }
        if (myTree.getSelectionPath() == null) {
          myTree.setSelectionPath(mySelectionPath);
        }
        final long currentTime = System.currentTimeMillis();
        if (currentTime - timeStamp > waitTime) {
          waitTime += 100; // ease off
          myModel.reload(myParentNode);
          timeStamp = System.currentTimeMillis();
        }
      }, myModalityState);
    }

    protected void process(Collection<String> children, CvsElementFactory elementFactory, RemoteResourceDataProvider dataProvider) {
      for (final String name : children) {
        final CvsElement element = elementFactory.createElement(name, myCvsRootConfiguration, myProject);
        element.setDataProvider(dataProvider);
        element.setPath(myParentNode.createPathForChild(name));
        element.setChildrenLoader(CvsTree.this);
        myParentNode.insertSorted(element, TreeNodeComparator.INSTANCE);
      }
    }

    @Override
    public void finished() {
      removeListener(this);
      ApplicationManager.getApplication().invokeLater(() -> {
        myLoadingNodeManager.removeFrom(myParentNode);
        myParentNode.setLoading(false);
        if (mySelectionPath != null) {
          if (mySelectionPath.getLastPathComponent() instanceof LoadingNode) {
            final TreeNode firstChild = myParentNode.getFirstChild();
            myTree.setSelectionPath(mySelectionPath.getParentPath().pathByAddingChild(firstChild));
          }
          else if (myTree.getSelectionPath() == null) {
            myTree.setSelectionPath(mySelectionPath);
          }
        }
      }, myModalityState);
    }
  }

}
