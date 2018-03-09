// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.cvsBrowser.ui;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsFilePath;
import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContextAdapter;
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
import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.vfs.VcsVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.TreeUIHelper;
import com.intellij.util.Consumer;
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

  public BrowserPanel(CvsRootConfiguration configuration, Project project, Consumer<VcsException> errorCallback) {
    super(new BorderLayout(2, 0));
    setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    myProject = project;
    myCvsRootConfiguration = configuration;
    myTree = new CvsTree(project, false, TreeSelectionModel.SINGLE_TREE_SELECTION, true, true, errorCallback);
    add(myTree, BorderLayout.CENTER);
    myTree.init();
    myTree.setCvsRootConfiguration(configuration);
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
    result.add(new MyHistoryAction());
    result.add(new MyAnnotateAction());
    result.add(new BrowseChangesAction());
    return result;
  }

  private static class EditSourceAction extends AnAction implements DumbAware {
    public EditSourceAction() {
      super(ActionsBundle.actionText("EditSource"),
            ActionsBundle.actionDescription("EditSource"),
            AllIcons.Actions.EditSource);
    }

    public void actionPerformed(AnActionEvent e) {
      final Navigatable[] navigatableArray = e.getData(CommonDataKeys.NAVIGATABLE_ARRAY);
      OpenSourceUtil.navigate(navigatableArray);
    }

    public void update(final AnActionEvent e) {
      final Navigatable[] navigatableArray = e.getData(CommonDataKeys.NAVIGATABLE_ARRAY);
      e.getPresentation().setEnabled(navigatableArray != null && navigatableArray.length > 0);
    }
  }

  private class MyCheckoutAction extends AnAction implements DumbAware {
    public MyCheckoutAction() {
      super(CvsBundle.message("operation.name.check.out"), null, AllIcons.Actions.CheckOut);
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
      if (!myCheckoutHelper.prepareCheckoutData(selectedElement, false)) {
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

  private class MyHistoryAction extends AnAction implements DumbAware {
    public MyHistoryAction() {
      super(CvsBundle.message("operation.name.show.file.history"),
            CvsBundle.message("operation.name.show.file.history.description"), AllIcons.Vcs.History);
    }

    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setVisible(true);
      CvsLightweightFile cvsLightFile = getCvsLightFile();
      presentation.setEnabled(cvsLightFile != null && cvsLightFile.getCvsFile() != null);
    }

    public void actionPerformed(AnActionEvent e) {
      final CvsElement[] currentSelection = myTree.getCurrentSelection();
      if (currentSelection.length != 1) return;
      final CvsElement cvsElement = currentSelection[0];
      final VirtualFile virtualFile = cvsElement.getVirtualFile();
      if (virtualFile == null || virtualFile.isDirectory()) return;
      final CvsVcs2 vcs = CvsVcs2.getInstance(myProject);
      final VcsHistoryProvider historyProvider = vcs.getVcsHistoryProvider();
      final String moduleName = cvsElement.getElementPath();
      final CvsRepositoryLocation location = new CvsRepositoryLocation(null, myCvsRootConfiguration, moduleName);
      CvsFilePath filePath = new CvsFilePath(virtualFile.getPath(), virtualFile.isDirectory(), location);
      AbstractVcsHelper.getInstance(myProject).showFileHistory(historyProvider, filePath, vcs);
    }
  }

  private class MyAnnotateAction extends AnAction implements DumbAware {
    public MyAnnotateAction() {
      super(CvsBundle.message("operation.name.annotate"), null, AllIcons.Actions.Annotate);
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

  private class BrowseChangesAction extends AnAction implements DumbAware {
    public BrowseChangesAction() {
      super(VcsBundle.message("browse.changes.action"), "", AllIcons.Actions.ShowChangesOnly);
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
    if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
      VirtualFile cvsVirtualFile = getCvsVirtualFile();
      if (cvsVirtualFile == null || !cvsVirtualFile.isValid()) return null;
      return new OpenFileDescriptor(myProject, cvsVirtualFile);
    }
    else if (CommonDataKeys.PROJECT.is(dataId)) {
      return myProject;
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
