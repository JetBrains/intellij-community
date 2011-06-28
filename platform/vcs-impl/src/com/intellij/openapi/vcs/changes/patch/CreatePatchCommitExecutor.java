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

package com.intellij.openapi.vcs.changes.patch;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.lifecycle.PeriodicalTasksCloser;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.util.WaitForProgressToShow;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class CreatePatchCommitExecutor implements CommitExecutorWithHelp, ProjectComponent, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.patch.CreatePatchCommitExecutor");
  
  private final Project myProject;
  private final ChangeListManager myChangeListManager;

  public String PATCH_PATH = "";
  public boolean REVERSE_PATCH = false;

  public static CreatePatchCommitExecutor getInstance(Project project) {
    return PeriodicalTasksCloser.getInstance().safeGetComponent(project, CreatePatchCommitExecutor.class);
  }

  public CreatePatchCommitExecutor(final Project project, final ChangeListManager changeListManager) {
    myProject = project;
    myChangeListManager = changeListManager;
  }

  @Nls
  public String getActionText() {
    return VcsBundle.message("create.patch.commit.action.text");
  }

  @Override
  public String getHelpId() {
    return "reference.dialogs.vcs.patch.create";
  }

  @NotNull
  public CommitSession createCommitSession() {
    return new CreatePatchCommitSession();
  }

  public void projectOpened() {
    myChangeListManager.registerCommitExecutor(this);
  }

  public void projectClosed() {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "CreatePatchCommitExecutor";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  private class CreatePatchCommitSession implements CommitSession {
    private final CreatePatchConfigurationPanel myPanel = new CreatePatchConfigurationPanel(myProject);

    @Nullable
    public JComponent getAdditionalConfigurationUI() {
      return myPanel.getPanel();
    }

    public JComponent getAdditionalConfigurationUI(final Collection<Change> changes, final String commitMessage) {
      if (PATCH_PATH.length() == 0) {
        PATCH_PATH = myProject.getBaseDir().getPresentableUrl();
      }
      myPanel.setFileName(ShelveChangesManager.suggestPatchName(myProject, commitMessage, new File(PATCH_PATH), null));
      myPanel.setReversePatch(REVERSE_PATCH);
      return myPanel.getPanel();
    }

    public boolean canExecute(Collection<Change> changes, String commitMessage) {
      return myPanel.isOkToExecute();
    }

    public void execute(Collection<Change> changes, String commitMessage) {
      if (! myPanel.isOkToExecute()) {
        Messages.showErrorDialog(myProject, VcsBundle.message("create.patch.error.title", myPanel.getError()), CommonBundle.getErrorTitle());
        return;
      }
      int binaryCount = 0;
      for(Change change: changes) {
        if (ChangesUtil.isBinaryChange(change)) {
          binaryCount++;
        }
      }
      if (binaryCount == changes.size()) {
        WaitForProgressToShow.runOrInvokeLaterAboveProgress(new Runnable() {
          public void run() {
            Messages.showInfoMessage(myProject, VcsBundle.message("create.patch.all.binary"),
                                     VcsBundle.message("create.patch.commit.action.title"));
          }
        }, null, myProject);
        return;
      }
      try {
        final String fileName = myPanel.getFileName();
        final File file = new File(fileName).getAbsoluteFile();
        VcsConfiguration.getInstance(myProject).acceptLastCreatedPatchName(file.getName());
        PATCH_PATH = file.getParent();
        REVERSE_PATCH = myPanel.isReversePatch();
        
        List<FilePatch> patches = IdeaTextPatchBuilder.buildPatch(myProject, changes, myProject.getBaseDir().getPresentableUrl(), REVERSE_PATCH);
        PatchWriter.writePatches(myProject, fileName, patches);
        final String message;
        if (binaryCount == 0) {
          message = VcsBundle.message("create.patch.success.confirmation", file.getPath());
        }
        else {
          message = VcsBundle.message("create.patch.partial.success.confirmation", file.getPath(),
                                      binaryCount);
        }
        WaitForProgressToShow.runOrInvokeLaterAboveProgress(new Runnable() {
          public void run() {
            if (Messages.showDialog(myProject, message,
                                    VcsBundle.message("create.patch.commit.action.title"),
                                    new String[]{RevealFileAction.getActionName(), IdeBundle.message("action.close")},
                                    0, Messages.getInformationIcon()) == 0) {
              ShowFilePathAction.open(file, file);
            }
          }
        }, null, myProject);
      }
      catch (final Exception ex) {
        LOG.info(ex);
        WaitForProgressToShow.runOrInvokeLaterAboveProgress(new Runnable() {
          public void run() {
            Messages.showErrorDialog(myProject, VcsBundle.message("create.patch.error.title", ex.getMessage()), CommonBundle.getErrorTitle());
          }
        }, null, myProject);
      }
    }

    public void executionCanceled() {
    }

    @Override
    public String getHelpId() {
      return null;
    }
  }
}