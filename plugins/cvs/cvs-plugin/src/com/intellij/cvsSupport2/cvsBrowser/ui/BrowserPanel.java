/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.cvsBrowser.ui;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContextAdapter;
import com.intellij.cvsSupport2.actions.cvsContext.CvsDataKeys;
import com.intellij.cvsSupport2.actions.cvsContext.CvsLightweightFile;
import com.intellij.cvsSupport2.changeBrowser.CvsRepositoryLocation;
import com.intellij.cvsSupport2.checkout.CheckoutAction;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.cvsBrowser.CheckoutHelper;
import com.intellij.cvsSupport2.cvsBrowser.CvsElement;
import com.intellij.cvsSupport2.cvsBrowser.CvsTree;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.ui.CvsTabbedWindow;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.vfs.VcsVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.TreeUIHelper;
import com.intellij.util.OpenSourceUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.io.File;

/**
 * author: lesya
 */
public class BrowserPanel extends JPanel implements DataProvider, CvsTabbedWindow.DeactivateListener {
  private final CvsTree myTree;
  private final CheckoutHelper myCheckoutHelper;
  private final CvsRootConfiguration myCvsRootConfiguration;
  private final Project myProject;

  public BrowserPanel(CvsRootConfiguration configuration, Project project) {
    super(new BorderLayout(2, 0));
    setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    myProject = project;
    myCvsRootConfiguration = configuration;
    myTree = new CvsTree(configuration, project, true, TreeSelectionModel.SINGLE_TREE_SELECTION, false, true);
    add(myTree, BorderLayout.CENTER);
    myTree.init();
    myCheckoutHelper = new CheckoutHelper(configuration, this);

    TreeUIHelper uiHelper = TreeUIHelper.getInstance();

    uiHelper.installEditSourceOnDoubleClick(myTree.getTree());
    TreeUtil.installActions(myTree.getTree());

    ActionGroup group = getActionGroup();
    PopupHandler.installPopupHandler(myTree.getTree(), group, ActionPlaces.CHECKOUT_POPUP, ActionManager.getInstance());
  }

  public ActionGroup getActionGroup() {
    DefaultActionGroup result = new DefaultActionGroup();
    result.add(new EditSourceAction());
    result.add(new MyCheckoutAction());
    //TODO lesya
    //result.add(new ShowLightCvsFileHistoryAction());
    result.add(new MyAnnotateAction());
    result.add(new BrowseChangesAction());
    return result;
  }

  private static class EditSourceAction extends AnAction {
    public EditSourceAction() {
      super(ActionsBundle.actionText("EditSource"),
            ActionsBundle.actionDescription("EditSource"),
            IconLoader.getIcon("/actions/editSource.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      final Navigatable[] navigatableArray = e.getData(PlatformDataKeys.NAVIGATABLE_ARRAY);
      if (navigatableArray != null && navigatableArray.length > 0) {
        OpenSourceUtil.navigate(navigatableArray);
      }
    }

    public void update(final AnActionEvent e) {
      final Navigatable[] navigatableArray = e.getData(PlatformDataKeys.NAVIGATABLE_ARRAY);
      e.getPresentation().setEnabled(navigatableArray != null && navigatableArray.length > 0);
    }
  }

  private class MyCheckoutAction extends AnAction {
    public MyCheckoutAction() {
      super(CvsBundle.message("operation.name.check.out"), null, IconLoader.getIcon("/actions/checkOut.png"));
    }

    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(canPerformCheckout());
    }

    private boolean canPerformCheckout() {
      CvsElement[] currentSelection = myTree.getCurrentSelection();
      return (currentSelection.length == 1) && currentSelection[0].canBeCheckedOut();
    }

    public void actionPerformed(AnActionEvent e) {
      CvsElement[] cvsElements = myTree.getCurrentSelection();

      CvsElement selectedElement = cvsElements[0];
      if (!myCheckoutHelper.prepareCheckoutData(selectedElement, false, null)) {
        return;
      }
      CvsHandler checkoutHandler = CommandCvsHandler.createCheckoutHandler(
        myCvsRootConfiguration,
        new String[]{selectedElement.getCheckoutPath()},
        myCheckoutHelper.getCheckoutLocation(),
        false, CvsConfiguration.getInstance(myProject).MAKE_NEW_FILES_READONLY, VcsConfiguration.getInstance(myProject).getCheckoutOption());

      CvsContextAdapter context = new CvsContextAdapter() {
        public Project getProject() {
          return myProject;
        }
      };

      new CheckoutAction(new CvsElement[]{selectedElement}, myCheckoutHelper.getCheckoutLocation(), false).
          actionPerformed(context, checkoutHandler);
    }
  }

  private class MyAnnotateAction extends AnAction {
    public MyAnnotateAction() {
      super(CvsBundle.message("operation.name.annotate"), null, IconLoader.getIcon("/actions/annotate.png"));
    }

    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setVisible(true);
      CvsLightweightFile cvsLightFile = getCvsLightFile();
      if (cvsLightFile != null) {
        File file = cvsLightFile.getCvsFile();
        presentation.setEnabled(file != null);
      } else {
        presentation.setEnabled(false);
      }
    }

    public void actionPerformed(AnActionEvent e) {
      VcsVirtualFile vcsVirtualFile = (VcsVirtualFile)getCvsVirtualFile();
      try {
        final CvsVcs2 vcs = CvsVcs2.getInstance(myProject);
        final FileAnnotation annotation = vcs
            .createAnnotation(vcsVirtualFile, vcsVirtualFile.getRevision(), myCvsRootConfiguration);
        AbstractVcsHelper.getInstance(myProject).showAnnotation(annotation, vcsVirtualFile, vcs);
      }
      catch (VcsException e1) {
        AbstractVcsHelper.getInstance(myProject).showError(e1, CvsBundle.message("operation.name.annotate"));
      }
    }
  }

  private class BrowseChangesAction extends AnAction {
    public BrowseChangesAction() {
      super(VcsBundle.message("browse.changes.action"), "", IconLoader.getIcon("/actions/showChangesOnly.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      CvsElement[] currentSelection = myTree.getCurrentSelection();
      assert currentSelection.length == 1;
      final String moduleName = currentSelection [0].getElementPath();
      final CvsRepositoryLocation location = new CvsRepositoryLocation(null, myCvsRootConfiguration, moduleName);
      AbstractVcsHelper.getInstance(myProject).showChangesBrowser(CvsVcs2.getInstance(myProject).getCommittedChangesProvider(),
                                                                  location,
                                                                  VcsBundle.message("browse.changes.scope", moduleName), BrowserPanel.this);
    }

    public void update(final AnActionEvent e) {
      CvsElement[] currentSelection = myTree.getCurrentSelection();
      e.getPresentation().setEnabled(currentSelection.length == 1);
    }
  }

  public Object getData(String dataId) {
    if (PlatformDataKeys.NAVIGATABLE.is(dataId)) {
      VirtualFile cvsVirtualFile = getCvsVirtualFile();
      if (cvsVirtualFile == null || !cvsVirtualFile.isValid()) return null;
      return new OpenFileDescriptor(myProject, cvsVirtualFile);
    }
    else if (PlatformDataKeys.PROJECT.is(dataId)) {
      return myProject;
    }
    else if (CvsDataKeys.CVS_ENVIRONMENT.is(dataId)) {
      return myCvsRootConfiguration;
    }
    else if (CvsDataKeys.CVS_LIGHT_FILE.is(dataId)) {
      return getCvsLightFile();
    }
    else {
      return null;
    }
  }

  @Nullable
  private VirtualFile getCvsVirtualFile() {
    CvsElement[] currentSelection = myTree.getCurrentSelection();
    if (currentSelection.length != 1) return null;
    VirtualFile file = currentSelection[0].getVirtualFile();
    if (file == null) return null;
    if (file.isDirectory()) return null;
    return file;
  }

  @Nullable
  private CvsLightweightFile getCvsLightFile() {
    CvsElement[] currentSelection = myTree.getCurrentSelection();
    if (currentSelection.length != 1) return null;
    return new CvsLightweightFile(currentSelection[0].getCvsLightFile(), null);
  }

  public void deactivated() {
    myTree.deactivated();
  }
}
