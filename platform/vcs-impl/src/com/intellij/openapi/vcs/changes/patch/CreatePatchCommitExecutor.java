/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vcs.changes.ui.SessionDialog;
import com.intellij.util.WaitForProgressToShow;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.vcs.changes.patch.PatchWriter.writeAsPatchToClipboard;

public class CreatePatchCommitExecutor extends LocalCommitExecutor implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance(CreatePatchCommitExecutor.class);
  private static final String VCS_PATCH_PATH_KEY = "vcs.patch.path";
  private static final String VCS_PATCH_TO_CLIPBOARD = "vcs.patch.to.clipboard";

  private final Project myProject;
  private final ChangeListManager myChangeListManager;

  public static CreatePatchCommitExecutor getInstance(Project project) {
    return project.getComponent(CreatePatchCommitExecutor.class);
  }

  public CreatePatchCommitExecutor(final Project project, final ChangeListManager changeListManager) {
    myProject = project;
    myChangeListManager = changeListManager;
  }

  @Override
  @Nls
  public String getActionText() {
    return "Create Patch...";
  }

  @Override
  public String getHelpId() {
    return "reference.dialogs.vcs.patch.create";
  }

  @Override
  @NotNull
  public CommitSession createCommitSession() {
    return new CreatePatchCommitSession();
  }

  @Override
  public void projectOpened() {
    myChangeListManager.registerCommitExecutor(this);
  }

  @Override
  @NonNls
  @NotNull
  public String getComponentName() {
    return "CreatePatchCommitExecutor";
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

    @Override
    @Nullable
    public JComponent getAdditionalConfigurationUI() {
      return myPanel.getPanel();
    }

    @Override
    public JComponent getAdditionalConfigurationUI(final Collection<Change> changes, final String commitMessage) {
      String patchPath = StringUtil.nullize(PropertiesComponent.getInstance(myProject).getValue(VCS_PATCH_PATH_KEY));
      if (patchPath == null) {
        patchPath = VcsApplicationSettings.getInstance().PATCH_STORAGE_LOCATION;
        if (patchPath == null) {
          patchPath = getDefaultPatchPath();
        }
      }
      myPanel.setFileName(ShelveChangesManager.suggestPatchName(myProject, commitMessage, new File(patchPath), null));
      myPanel.setToClipboard(PropertiesComponent.getInstance(myProject).getBoolean(VCS_PATCH_TO_CLIPBOARD, false));
      File commonAncestor = ChangesUtil.findCommonAncestor(changes);
      myPanel.setCommonParentPath(commonAncestor);
      myPanel.selectBasePath(PatchWriter.calculateBaseForWritingPatch(myProject, changes));
      myPanel.setReversePatch(false);

      JComponent panel = myPanel.getPanel();
      panel.putClientProperty(SessionDialog.VCS_CONFIGURATION_UI_TITLE, "Patch File Settings");
      return panel;
    }

    @Override
    public boolean canExecute(Collection<Change> changes, String commitMessage) {
      return myPanel.isOkToExecute();
    }

    @Override
    public void execute(@NotNull Collection<Change> changes, @Nullable String commitMessage) {
      PropertiesComponent.getInstance(myProject).setValue(VCS_PATCH_TO_CLIPBOARD, myPanel.isToClipboard());
      try {
        if (myPanel.isToClipboard()) {
          String base = myPanel.getBaseDirName();
          List<FilePatch> patches = IdeaTextPatchBuilder.buildPatch(myProject, changes, base, myPanel.isReversePatch());
          writeAsPatchToClipboard(myProject, patches, base, myCommitContext);
          VcsNotifier.getInstance(myProject).notifySuccess("Patch copied to clipboard");
        }
        else {
          validateAndWritePatchToFile(changes);
        }
      }
      catch (IOException | VcsException ex) {
        LOG.info(ex);
        WaitForProgressToShow.runOrInvokeLaterAboveProgress(
          () -> Messages.showErrorDialog(myProject, VcsBundle.message("create.patch.error.title", ex.getMessage()),
                                         CommonBundle.getErrorTitle()), null, myProject);
      }
    }

    private void validateAndWritePatchToFile(@NotNull Collection<Change> changes) throws VcsException, IOException {
      final String fileName = myPanel.getFileName();
      final File file = new File(fileName).getAbsoluteFile();
      if (!checkIsFileValid(file)) return;
      //noinspection ResultOfMethodCallIgnored
      file.getParentFile().mkdirs();
      VcsConfiguration.getInstance(myProject).acceptLastCreatedPatchName(file.getName());
      String patchPath = FileUtil.toSystemIndependentName(StringUtil.notNullize(file.getParent()));
      String valueToStore = StringUtil.isEmpty(patchPath) || patchPath.equals(getDefaultPatchPath()) ? null : patchPath;
      PropertiesComponent.getInstance(myProject).setValue(VCS_PATCH_PATH_KEY, valueToStore);
      VcsApplicationSettings.getInstance().PATCH_STORAGE_LOCATION = valueToStore;
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
          configuration.SHOW_PATCH_IN_EXPLORER = showDialog(file);
        }
      }, null, myProject);
    }

    @Override
    @Nullable
    public ValidationInfo validateFields() {
      return myPanel.validateFields();
    }
  }

  private boolean checkIsFileValid(@NotNull File file) {
    if (file.exists()) {
      final int[] result = new int[1];
      WaitForProgressToShow.runOrInvokeAndWaitAboveProgress(
        () -> result[0] = Messages.showYesNoDialog(myProject, "File " + file.getName() + " (" + file.getParent() + ")" +
                                                              " already exists.\nDo you want to overwrite it?",
                                                   CommonBundle.getWarningTitle(),
                                                   "Overwrite", "Cancel",
                                                   Messages.getWarningIcon()));
      if (Messages.NO == result[0]) return false;
    }
    if (file.getParentFile() == null) {
      WaitForProgressToShow.runOrInvokeLaterAboveProgress(() ->
                                                            Messages.showErrorDialog(myProject, VcsBundle
                                                              .message("create.patch.error.title",
                                                                       "Can not write patch to specified file: " +
                                                                       file.getPath()), CommonBundle.getErrorTitle()),
                                                          ModalityState.NON_MODAL, myProject);
      return false;
    }
    return true;
  }

  @NotNull
  private String getDefaultPatchPath() {
    String baseDir = myProject.getBasePath();
    return baseDir == null ? FileUtil.toSystemIndependentName(PathManager.getHomePath()) : baseDir;
  }

  private Boolean showDialog(File file) {
    String message = VcsBundle.message("create.patch.success.confirmation", file.getPath());
    String title = VcsBundle.message("create.patch.commit.action.title");

    Boolean[] ref = new Boolean[1];
    DialogWrapper.DoNotAskOption option = new DialogWrapper.DoNotAskOption() {
      @Override
      public boolean isToBeShown() {
        return true;
      }

      @Override
      public void setToBeShown(boolean value, int exitCode) {
        if (!value) {
          ref[0] = exitCode == 0;
        }
      }

      @Override
      public boolean canBeHidden() {
        return true;
      }

      @Override
      public boolean shouldSaveOptionsOnCancel() {
        return true;
      }

      @NotNull
      @Override
      public String getDoNotShowMessage() {
        return CommonBundle.message("dialog.options.do.not.ask");
      }
    };

    ShowFilePathAction.showDialog(myProject, message, title, file, option);

    return ref[0];
  }
}