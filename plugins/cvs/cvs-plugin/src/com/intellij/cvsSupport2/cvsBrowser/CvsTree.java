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

import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.ui.CvsTabbedWindow;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TreeUIHelper;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

public class CvsTree extends JPanel implements CvsTabbedWindow.DeactivateListener {
  private CvsElement[] myCurrentSelection = new CvsElement[0];
  private Tree myTree;
  private final CvsRootConfiguration myCvsRootConfiguration;
  private final Observable mySelectionObservable = new AlwaysNotificatedObservable();
  private final boolean myShowFiles;
  private final boolean myAllowRootSelection;
  private final boolean myShowModules;
  private final Project myProject;
  private final int mySelectionModel;

  @NonNls public static final String SELECTION_CHANGED = "Selection Changed";
  @NonNls public static final String LOGIN_ABORTED = "Login Aborted";

  public CvsTree(CvsRootConfiguration env,
                 Project project,
                 boolean showFiles,
                 int selectionMode,
                 boolean allowRootSelection, boolean showModules) {
    super(new BorderLayout());
    myProject = project;
    mySelectionModel = selectionMode;
    myShowModules = showModules;
    myAllowRootSelection = allowRootSelection;
    myShowFiles = showFiles;
    setSize(500, 500);
    myCvsRootConfiguration = env;
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
      for (int i = 0; i < paths.length; i++) {
        Object selectedObject = paths[i].getLastPathComponent();
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
    CvsElement result = CvsElementFactory.FOLDER_ELEMENT_FACTORY
      .createElement(rootName, myCvsRootConfiguration, project);
    result.setName(rootName);
    result.setDataProvider(new RootDataProvider(myCvsRootConfiguration, myShowFiles, myShowModules));
    result.setPath(".");
    result.cannotBeCheckedOut();
    return result;
  }

  public CvsElement[] getCurrentSelection() {
    return myCurrentSelection;
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

  public void onLoginAborted() {
    mySelectionObservable.notifyObservers(LOGIN_ABORTED);
  }

  public void init() {
    CvsElement root = createRoot(myProject);
    TreeModel deafModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    CvsTreeModel model = new CvsTreeModel(root);
    root.setModel(model);
    myTree = new Tree(deafModel);
    model.setTree(myTree);
    model.setCvsTree(this);

    add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);

    myTree.setModel(model);


    myTree.getSelectionModel().setSelectionMode(mySelectionModel);
    myTree.setCellRenderer(new Cvs2Renderer());
    addSelectionListener();

    TreeUIHelper uiHelper = TreeUIHelper.getInstance();
    uiHelper.installTreeSpeedSearch(myTree);
    TreeUtil.installActions(myTree);

    myTree.requestFocus();
  }

  class AlwaysNotificatedObservable extends Observable{
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
}
