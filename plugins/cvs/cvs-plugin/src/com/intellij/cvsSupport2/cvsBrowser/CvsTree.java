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

import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.DirectoryContent;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsListenerWithProgress;
import com.intellij.cvsSupport2.ui.CvsTabbedWindow;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TreeUIHelper;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.*;
import java.util.List;

public class CvsTree extends JPanel implements CvsTabbedWindow.DeactivateListener, ChildrenLoader {
  private CvsElement[] myCurrentSelection = new CvsElement[0];
  private Tree myTree;
  private DefaultTreeModel myModel;
  private final CvsRootConfiguration myCvsRootConfiguration;
  private final Observable mySelectionObservable = new AlwaysNotificatedObservable();
  private final boolean myShowFiles;
  private final boolean myAllowRootSelection;
  private final boolean myShowModules;
  private final Project myProject;
  private final int mySelectionModel;
  private final RemoteResourceDataProvider myDataProvider;
  private final LoadingNode.Manager myLoadingNodeManager = new LoadingNode.Manager();

  @NonNls public static final String SELECTION_CHANGED = "Selection Changed";

  public CvsTree(CvsRootConfiguration env,
                 Project project,
                 boolean showFiles,
                 int selectionMode,
                 boolean allowRootSelection,
                 boolean showModules) {
    super(new BorderLayout());
    myProject = project;
    mySelectionModel = selectionMode;
    myShowModules = showModules;
    myAllowRootSelection = allowRootSelection;
    myShowFiles = showFiles;
    setSize(500, 500);
    myCvsRootConfiguration = env;
    myDataProvider = new RootDataProvider(myCvsRootConfiguration);
    addListener(myLoadingNodeManager);
  }

  private void addSelectionListener() {
    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        setCurrentSelection(myTree.getSelectionPaths());
      }
    });
  }

  private void setCurrentSelection(TreePath[] paths) {
    ArrayList<CvsElement> selection = new ArrayList<CvsElement>();
    if (paths != null) {
      for (TreePath path : paths) {
        Object selectedObject = path.getLastPathComponent();
        if (!(selectedObject instanceof CvsElement)) continue;
        CvsElement cvsElement = (CvsElement)selectedObject;
        if (cvsElement.getElementPath().equals(".") && (!myAllowRootSelection)) continue;
        selection.add(cvsElement);
      }
    }
    myCurrentSelection = selection.toArray(new CvsElement[selection.size()]);
    mySelectionObservable.notifyObservers(SELECTION_CHANGED);
  }

  private CvsElement createRoot(Project project) {
    String rootName = myCvsRootConfiguration.toString();
    CvsElement result = CvsElementFactory.FOLDER_ELEMENT_FACTORY.createElement(rootName, myCvsRootConfiguration, project);
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

  public void dispose() {
    mySelectionObservable.deleteObservers();
  }

  public void init() {
    CvsElement root = createRoot(myProject);
    myModel = new DefaultTreeModel(root, true);
    myTree = new Tree(myModel);
    add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);

    myTree.getSelectionModel().setSelectionMode(mySelectionModel);
    myTree.setCellRenderer(new Cvs2Renderer());
    addSelectionListener();

    TreeUIHelper uiHelper = TreeUIHelper.getInstance();
    uiHelper.installTreeSpeedSearch(myTree);
    TreeUtil.installActions(myTree);

    myTree.requestFocus();
  }

  static class AlwaysNotificatedObservable extends Observable{
    public void notifyObservers(Object arg) {
      setChanged();
      super.notifyObservers(arg);
    }
  }

  private final List<CvsTabbedWindow.DeactivateListener> myListeners = new ArrayList<CvsTabbedWindow.DeactivateListener>();

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

  public void deactivated() {
    synchronized (myListeners) {
      for (CvsTabbedWindow.DeactivateListener listener : myListeners) {
        listener.deactivated();
      }
      myListeners.clear();
    }
  }

  @Override
  public void loadChildren(final MutableTreeNode element, final String elementPath, final RemoteResourceDataProvider dataProvider) {
    myLoadingNodeManager.addTo(myModel, element);
    final Application application = ApplicationManager.getApplication();
    final ModalityState modalityState = application.getCurrentModalityState();
    application.executeOnPooledThread(new Runnable() {
      public void run() {
        myDataProvider.fillContentFor(new MyGetContentCallback(element, elementPath, modalityState, dataProvider, myProject));
      }
    });
  }

  private class MyGetContentCallback implements GetContentCallback, CvsTabbedWindow.DeactivateListener {

    private final MutableTreeNode myParentNode;
    private final String myElementPath;
    private final ModalityState myModalityState;
    private final RemoteResourceDataProvider myDataProvider;
    private final Project myProject;
    private CvsListenerWithProgress myListener;

    public MyGetContentCallback(MutableTreeNode parentNode,
                                String elementPath,
                                ModalityState modalityState,
                                RemoteResourceDataProvider dataProvider,
                                Project project) {
      myParentNode = parentNode;
      myElementPath = elementPath;
      myModalityState = modalityState;
      myDataProvider = dataProvider;
      myProject = project;
      addListener(this);
    }

    @Override
    public Project getProject() {
      return myProject;
    }

    @Override
    public String getElementPath() {
      return myElementPath;
    }

    @Override
    public ModalityState getModalityState() {
      return myModalityState;
    }

    public void deactivated() {
      System.out.println("CvsTree$MyGetContentCallback.deactivated()");
      if (myListener != null) {
        myListener.indirectCancel();
      }
    }

    public void useForCancel(final CvsListenerWithProgress listener) {
      myListener = listener;
    }

    public void appendDirectoryContent(final DirectoryContent directoryContent) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          if (myShowModules) {
            process(directoryContent.getSubModulesRaw(), CvsElementFactory.MODULE_ELEMENT_FACTORY,
                    new ModuleDataProvider(myCvsRootConfiguration));
          }
          process(directoryContent.getSubDirectoriesRaw(), CvsElementFactory.FOLDER_ELEMENT_FACTORY,
                  myDataProvider.getChildrenDataProvider());
          if (myShowFiles) {
            process(directoryContent.getFilesRaw(), CvsElementFactory.FILE_ELEMENT_FACTORY,
                    RemoteResourceDataProvider.NOT_EXPANDABLE);
          }
          myModel.reload(myParentNode);
          if (myTree.getSelectionPath() == null) {
            selectRoot();
          }
        }
      });
    }

    protected void process(Collection<String> children, CvsElementFactory elementFactory, RemoteResourceDataProvider dataProvider) {
      for (final String name : children) {
        final CvsElement element = elementFactory.createElement(name, myCvsRootConfiguration, myProject);
        element.setDataProvider(dataProvider);
        myParentNode.insert(element, 0);
      }
    }

    public void finished() {
      removeListener(this);
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          myLoadingNodeManager.removeFrom(myParentNode);
          final TreePath selectionPath = myTree.getSelectionPath();
          if (myTree.getSelectionPath() == null) {
            myTree.setSelectionPath(selectionPath);
          }
        }
      });
    }
  }

}
