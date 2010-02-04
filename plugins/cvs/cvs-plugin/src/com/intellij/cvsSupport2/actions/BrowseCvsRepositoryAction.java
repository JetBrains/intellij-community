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
package com.intellij.cvsSupport2.actions;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContextWrapper;
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.config.ui.SelectCvsConfigurationDialog;
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsBrowser.ui.BrowserPanel;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.cvsExecution.ModalityContextImpl;
import com.intellij.cvsSupport2.cvshandlers.AbstractCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.cvshandlers.FileSetToBeUpdated;
import com.intellij.cvsSupport2.cvsoperations.common.LoginPerformer;
import com.intellij.cvsSupport2.ui.CvsTabbedWindow;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.util.Consumer;

import java.util.Collections;

/**
 * author: lesya
 */
public class BrowseCvsRepositoryAction extends AbstractAction implements DumbAware {
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

    return new MyCvsHandler(context.getProject());
  }

  protected void onActionPerformed(CvsContext context,
                                   CvsTabbedWindow tabbedWindow,
                                   boolean successfully,
                                   CvsHandler handler) {
    if (mySelectedConfiguration == null) return;
    if (! loginImpl(context.getProject(), new ModalityContextImpl(ModalityState.NON_MODAL, false),
                    new Consumer<VcsException>() {
                      public void consume(VcsException e) {
                        //
                      }
                    })) return;
    super.onActionPerformed(context, tabbedWindow, successfully, handler);
    if (successfully){
      Project project = context.getProject();
      LOG.assertTrue(project != null);
      LOG.assertTrue(mySelectedConfiguration != null);
      final BrowserPanel browserPanel = new BrowserPanel(mySelectedConfiguration, project);
      tabbedWindow.addTab(TITLE, browserPanel,
                          true, true, true, true, browserPanel.getActionGroup(), "cvs.browse");
      tabbedWindow.ensureVisible(project);

    }
  }

  private class MyCvsHandler extends AbstractCvsHandler {
    private final Project myProject;

    public MyCvsHandler(Project project) {
      super(TITLE, FileSetToBeUpdated.EMPTY);
      myProject = project;
    }

    public boolean isCanceled() {
      return false;
    }

    protected int getFilesToProcessCount() {
      return 0;
    }

    public boolean login(ModalityContext executor) throws Exception {
      return loginImpl(myProject, executor, new Consumer<VcsException>() {
                                            public void consume(VcsException e) {
                                              myErrors.add(e);
                                            }
                                          });
      /*final LoginPerformer.MyProjectKnown performer =
        new LoginPerformer.MyProjectKnown(myProject, Collections.<CvsEnvironment>singletonList(mySelectedConfiguration),
                                          new Consumer<VcsException>() {
                                            public void consume(VcsException e) {
                                              myErrors.add(e);
                                            }
                                          });
      return performer.loginAll(executor, false);*/
    }
  }

  public boolean loginImpl(final Project project, final ModalityContext executor, final Consumer<VcsException> exceptionConsumer) {
    final LoginPerformer.MyProjectKnown performer =
      new LoginPerformer.MyProjectKnown(project, Collections.<CvsEnvironment>singletonList(mySelectedConfiguration), exceptionConsumer);
    return performer.loginAll(executor, false);
  }
}
