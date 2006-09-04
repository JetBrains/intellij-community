package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContextWrapper;
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.config.ui.SelectCvsConfigurationDialog;
import com.intellij.cvsSupport2.cvsBrowser.ui.BrowserPanel;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.cvshandlers.AbstractCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.cvshandlers.FileSetToBeUpdated;
import com.intellij.cvsSupport2.ui.CvsTabbedWindow;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.CvsBundle;

/**
 * author: lesya
 */
public class BrowseCvsRepositoryAction extends AbstractAction{
  private static final String TITLE = CvsBundle.message("operation.name.browse.repository");
  private CvsRootConfiguration mySelectedConfiguration;

  public BrowseCvsRepositoryAction() {
    super(false);
  }

  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    VcsContext context = CvsContextWrapper.createInstance(e);
    boolean projectExists = context.getProject() != null;
    presentation.setVisible(true);
    presentation.setEnabled(projectExists);
  }

  protected String getTitle(VcsContext context) {
    return TITLE;
  }

  protected CvsHandler getCvsHandler(CvsContext context) {
    SelectCvsConfigurationDialog selectCvsConfigurationDialog = new SelectCvsConfigurationDialog(context.getProject());
    selectCvsConfigurationDialog.show();

    if (!selectCvsConfigurationDialog.isOK()) return CvsHandler.NULL;

    mySelectedConfiguration = selectCvsConfigurationDialog.getSelectedConfiguration();

    return new MyCvsHandler();
  }

  protected void onActionPerformed(CvsContext context,
                                   CvsTabbedWindow tabbedWindow,
                                   boolean successfully,
                                   CvsHandler handler) {
    super.onActionPerformed(context, tabbedWindow, successfully, handler);
    if (successfully){
      Project project = context.getProject();
      LOG.assertTrue(project != null);
      LOG.assertTrue(mySelectedConfiguration != null);
      tabbedWindow.addTab(TITLE, new BrowserPanel(mySelectedConfiguration, project),
                          true, true, true, true, "cvs.browse");
      tabbedWindow.ensureVisible(project);

    }
  }

  private class MyCvsHandler extends AbstractCvsHandler {
    public MyCvsHandler() {
      super(TITLE, FileSetToBeUpdated.EMTPY);
    }

    public boolean isCanceled() {
      return false;
    }

    protected int getFilesToProcessCount() {
      return 0;
    }

    public boolean login(ModalityContext executor) throws Exception {
      return mySelectedConfiguration.login(executor);
    }
  }
}
