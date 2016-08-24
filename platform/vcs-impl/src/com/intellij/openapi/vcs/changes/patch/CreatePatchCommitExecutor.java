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

package com.intellij.openapi.vcs.changes.patch;

import com.intellij.CommonBundle;
import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.lifecycle.PeriodicalTasksCloser;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsApplicationSettings;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vcs.changes.ui.SessionDialog;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.WaitForProgressToShow;
import com.intellij.vcsUtil.VcsUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class CreatePatchCommitExecutor extends LocalCommitExecutor implements ProjectComponent, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.patch.CreatePatchCommitExecutor");

  private final Project myProject;
  private final ChangeListManager myChangeListManager;

  public String PATCH_PATH = "";

  public static CreatePatchCommitExecutor getInstance(Project project) {
    return PeriodicalTasksCloser.getInstance().safeGetComponent(project, CreatePatchCommitExecutor.class);
  }

  public CreatePatchCommitExecutor(final Project project, final ChangeListManager changeListManager) {
    myProject = project;
    myChangeListManager = changeListManager;
  }

  @Nls
  public String getActionText() {
    return "Create Patch...";
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

  private class CreatePatchCommitSession implements CommitSession, CommitSessionContextAware {
    private final CreatePatchConfigurationPanel myPanel = new CreatePatchConfigurationPanel(myProject);
    private CommitContext myCommitContext;

    public CreatePatchCommitSession() {
    }

    @Override
    public void setContext(CommitContext context) {
      myCommitContext = context;
    }

    @Nullable
    public JComponent getAdditionalConfigurationUI() {
      return myPanel.getPanel();
    }

    public JComponent getAdditionalConfigurationUI(final Collection<Change> changes, final String commitMessage) {
      if (PATCH_PATH.length() == 0) {
        VcsApplicationSettings settings = VcsApplicationSettings.getInstance();
        PATCH_PATH = settings.PATCH_STORAGE_LOCATION;
        if (PATCH_PATH == null) {
          PATCH_PATH = myProject.getBaseDir() == null ? PathManager.getHomePath() : myProject.getBaseDir().getPresentableUrl();
        }
      }
      myPanel.setFileName(ShelveChangesManager.suggestPatchName(myProject, commitMessage, new File(PATCH_PATH), null));
      File commonAncestor = ChangesUtil.findCommonAncestor(changes);
      myPanel.setCommonParentPath(commonAncestor);
      Set<AbstractVcs> affectedVcses = ChangesUtil.getAffectedVcses(changes, myProject);
      if (affectedVcses.size() == 1 && commonAncestor != null) {
        VirtualFile vcsRoot = VcsUtil.getVcsRootFor(myProject, VcsUtil.getFilePath(commonAncestor));
        if (vcsRoot != null) {
          myPanel.selectBasePath(vcsRoot);
        }
      }
      myPanel.setReversePatch(false);

      JComponent panel = myPanel.getPanel();
      panel.putClientProperty(SessionDialog.VCS_CONFIGURATION_UI_TITLE, "Patch File Settings");
      return panel;
    }

    public boolean canExecute(Collection<Change> changes, String commitMessage) {
      return myPanel.isOkToExecute();
    }

    public void execute(Collection<Change> changes, String commitMessage) {
      final String fileName = myPanel.getFileName();
      final File file = new File(fileName).getAbsoluteFile();
      if (file.exists()) {
        final int[] result = new int[1];
        WaitForProgressToShow.runOrInvokeAndWaitAboveProgress(
          () -> result[0] = Messages.showYesNoDialog(myProject, "File " + file.getName() + " (" + file.getParent() + ")" +
                                                                " already exists.\nDo you want to overwrite it?",
                                                     CommonBundle.getWarningTitle(), Messages.getWarningIcon()));
        if (Messages.NO == result[0]) return;
      }
      if (file.getParentFile() == null) {
        WaitForProgressToShow.runOrInvokeLaterAboveProgress(() ->
                                                              Messages.showErrorDialog(myProject, VcsBundle
                                                                .message("create.patch.error.title",
                                                                         "Can not write patch to specified file: " +
                                                                         file.getPath()), CommonBundle.getErrorTitle()),
                                                            ModalityState.NON_MODAL, myProject);
        return;
      }
      try {
        //noinspection ResultOfMethodCallIgnored
        file.getParentFile().mkdirs();
        VcsConfiguration.getInstance(myProject).acceptLastCreatedPatchName(file.getName());
        PATCH_PATH = file.getParent();
        VcsApplicationSettings.getInstance().PATCH_STORAGE_LOCATION = PATCH_PATH;
        final boolean reversePatch = myPanel.isReversePatch();

        String baseDirName = myPanel.getBaseDirName();
        List<FilePatch> patches = IdeaTextPatchBuilder.buildPatch(myProject, changes, baseDirName, reversePatch);
        PatchWriter.writePatches(myProject, fileName, baseDirName, patches, myCommitContext, myPanel.getEncoding(), true);
        WaitForProgressToShow.runOrInvokeLaterAboveProgress(() -> {
          final VcsConfiguration configuration = VcsConfiguration.getInstance(myProject);
          if (Boolean.TRUE.equals(configuration.SHOW_PATCH_IN_EXPLORER)) {
            ShowFilePathAction.openFile(file);
          }
          else if (configuration.SHOW_PATCH_IN_EXPLORER == null) {
            configuration.SHOW_PATCH_IN_EXPLORER =
              ShowFilePathAction.showDialog(myProject, VcsBundle.message("create.patch.success.confirmation", file.getPath()),
                                            VcsBundle.message("create.patch.commit.action.title"), file);
          }
        }, null, myProject);
      } catch (ProcessCanceledException e) {
        //
      } catch (final Exception ex) {
        LOG.info(ex);
        WaitForProgressToShow.runOrInvokeLaterAboveProgress(
          () -> Messages.showErrorDialog(myProject, VcsBundle.message("create.patch.error.title", ex.getMessage()),
                                         CommonBundle.getErrorTitle()), null, myProject);
      }
    }

    public void executionCanceled() {
    }

    @Override
    @Nullable
    public ValidationInfo validateFields() {
      return myPanel.validateFields();
    }

    @Override
    public String getHelpId() {
      return null;
    }
  }
}
