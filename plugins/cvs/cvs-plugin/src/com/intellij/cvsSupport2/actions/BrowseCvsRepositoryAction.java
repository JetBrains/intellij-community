/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.config.ui.SelectCvsConfigurationDialog;
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsBrowser.ui.BrowserPanel;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.cvshandlers.FileSetToBeUpdated;
import com.intellij.cvsSupport2.cvsoperations.common.LoginPerformer;
import com.intellij.cvsSupport2.ui.CvsTabbedWindow;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
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

  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final boolean projectExists = e.getData(CommonDataKeys.PROJECT) != null;
    presentation.setVisible(projectExists);
    presentation.setEnabled(projectExists);
  }

  @Override
  protected String getTitle(VcsContext context) {
    return TITLE;
  }

  @Override
  protected CvsHandler getCvsHandler(CvsContext context) {
    final SelectCvsConfigurationDialog selectCvsConfigurationDialog = new SelectCvsConfigurationDialog(context.getProject());
    if (!selectCvsConfigurationDialog.showAndGet()) {
      return CvsHandler.NULL;
    }

    mySelectedConfiguration = selectCvsConfigurationDialog.getSelectedConfiguration();
    return new MyCvsHandler();
  }

  @Override
  protected void onActionPerformed(CvsContext context,
                                   CvsTabbedWindow tabbedWindow,
                                   boolean successfully,
                                   CvsHandler handler) {
    if (mySelectedConfiguration == null) return;
    final Project project = context.getProject();
    if (! loginImpl(context.getProject(),
                    new Consumer<VcsException>() {
                      @Override
                      public void consume(VcsException e) {
                        VcsBalloonProblemNotifier.showOverChangesView(project, e.getMessage(), MessageType.WARNING);
                      }
                    })) return;
    super.onActionPerformed(context, tabbedWindow, successfully, handler);
    if (successfully){
      LOG.assertTrue(project != null);
      LOG.assertTrue(mySelectedConfiguration != null);
      final BrowserPanel browserPanel = new BrowserPanel(mySelectedConfiguration, project, new Consumer<VcsException>() {
        @Override
        public void consume(VcsException e) {
          VcsBalloonProblemNotifier.showOverChangesView(project, e.getMessage(), MessageType.ERROR);
        }
      });
      tabbedWindow.addTab(TITLE, browserPanel,
                          true, true, true, true, browserPanel.getActionGroup(), "cvs.browse");
    }
  }

  private class MyCvsHandler extends CvsHandler {

    public MyCvsHandler() {
      super(TITLE, FileSetToBeUpdated.EMPTY);
    }

    @Override
    public boolean isCanceled() {
      return false;
    }

    @Override
    protected int getFilesToProcessCount() {
      return 0;
    }

    @Override
    public boolean login(Project project) {
      return loginImpl(project, new Consumer<VcsException>() {
        public void consume(VcsException e) {
          myErrors.add(e);
        }
      });
    }
  }

  private boolean loginImpl(final Project project, final Consumer<VcsException> exceptionConsumer) {
    final LoginPerformer performer =
      new LoginPerformer(project, Collections.<CvsEnvironment>singletonList(mySelectedConfiguration), exceptionConsumer);
    try {
      return performer.loginAll(false);
    } catch (Exception e) {
      VcsBalloonProblemNotifier.showOverChangesView(project, e.getMessage(), MessageType.ERROR);
      return false;
    }
  }
}
