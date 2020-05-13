// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.CommonBundle;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.diff.impl.patch.TextPatchBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vcs.changes.shelf.ShelvedBinaryFile;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import com.intellij.openapi.vcs.changes.ui.SessionDialog;
import com.intellij.ui.UIBundle;
import com.intellij.util.WaitForProgressToShow;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;

import static com.intellij.openapi.vcs.changes.patch.PatchWriter.writeAsPatchToClipboard;
import static com.intellij.util.ObjectUtils.chooseNotNull;
import static com.intellij.util.containers.ContainerUtil.*;

public final class CreatePatchCommitExecutor extends LocalCommitExecutor {
  private static final Logger LOG = Logger.getInstance(CreatePatchCommitExecutor.class);
  private static final String VCS_PATCH_PATH_KEY = "vcs.patch.path"; //NON-NLS
  private static final String VCS_PATCH_TO_CLIPBOARD = "vcs.patch.to.clipboard"; //NON-NLS

  private final Project myProject;

  public CreatePatchCommitExecutor(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  @Nls
  public String getActionText() {
    return VcsBundle.message("action.name.create.patch");
  }

  @Override
  public String getHelpId() {
    return "reference.dialogs.vcs.patch.create";
  }

  @Override
  public boolean supportsPartialCommit() {
    return true;
  }

  @NotNull
  @Override
  public CommitSession createCommitSession(@NotNull CommitContext commitContext) {
    return createCommitSession(myProject, new DefaultPatchBuilder(myProject), commitContext);
  }

  public static CommitSession createCommitSession(@NotNull Project project,
                                                  @NotNull PatchBuilder patchBuilder,
                                                  @NotNull CommitContext commitContext) {
    return new CreatePatchCommitSession(project, commitContext, patchBuilder);
  }

  private static class CreatePatchCommitSession implements CommitSession {
    @NotNull private final Project myProject;
    @NotNull private final CommitContext myCommitContext;
    @NotNull private final PatchBuilder myPatchBuilder;

    private final CreatePatchConfigurationPanel myPanel;

    private CreatePatchCommitSession(@NotNull Project project, @NotNull CommitContext commitContext, @NotNull PatchBuilder patchBuilder) {
      myProject = project;
      myCommitContext = commitContext;
      myPatchBuilder = patchBuilder;
      myPanel = new CreatePatchConfigurationPanel(myProject);
    }

    @Override
    public JComponent getAdditionalConfigurationUI(@NotNull Collection<Change> changes, @Nullable String commitMessage) {
      String patchPath = StringUtil.nullize(PropertiesComponent.getInstance(myProject).getValue(VCS_PATCH_PATH_KEY));
      if (patchPath == null) {
        patchPath = VcsApplicationSettings.getInstance().PATCH_STORAGE_LOCATION;
        if (patchPath == null) {
          patchPath = getDefaultPatchPath(myProject);
        }
      }
      myPanel.setFileName(ShelveChangesManager.suggestPatchName(myProject, commitMessage, new File(patchPath), null));
      myPanel.setToClipboard(PropertiesComponent.getInstance(myProject).getBoolean(VCS_PATCH_TO_CLIPBOARD, false));
      File commonAncestor = ChangesUtil.findCommonAncestor(changes);
      myPanel.setCommonParentPath(commonAncestor);
      myPanel.selectBasePath(PatchWriter.calculateBaseForWritingPatch(myProject, changes));
      myPanel.setReversePatch(false);
      myPanel.setReverseEnabledAndVisible(myPatchBuilder.isReverseSupported());

      JComponent panel = myPanel.getPanel();
      panel.putClientProperty(SessionDialog.VCS_CONFIGURATION_UI_TITLE, VcsBundle.message("create.patch.settings.dialog.title"));
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
        String baseDir = myPanel.getBaseDirName();
        boolean isReverse = myPanel.isReversePatch();
        String fileName = myPanel.getFileName();
        Charset encoding = myPanel.getEncoding();

        if (myPanel.isToClipboard()) {
          writePatchToClipboard(myProject, baseDir, changes, isReverse, true, myPatchBuilder, myCommitContext);
        }
        else {
          validateAndWritePatchToFile(myProject, baseDir, changes, isReverse, fileName, encoding, myPatchBuilder, myCommitContext);
        }
      }
      catch (IOException | VcsException ex) {
        LOG.info(ex);
        WaitForProgressToShow.runOrInvokeLaterAboveProgress(
          () -> Messages.showErrorDialog(myProject, VcsBundle.message("create.patch.error.title", ex.getMessage()),
                                         CommonBundle.getErrorTitle()), null, myProject);
      }
    }

    public static void validateAndWritePatchToFile(@NotNull Project project,
                                                   @NotNull String baseDir,
                                                   @NotNull Collection<? extends Change> changes,
                                                   boolean reversePatch,
                                                   @NotNull String fileName,
                                                   @NotNull Charset encoding,
                                                   @NotNull PatchBuilder patchBuilder,
                                                   @NotNull CommitContext commitContext) throws VcsException, IOException {
      final File file = new File(fileName).getAbsoluteFile();
      if (!checkIsFileValid(project, file)) return;
      //noinspection ResultOfMethodCallIgnored
      file.getParentFile().mkdirs();
      VcsConfiguration.getInstance(project).acceptLastCreatedPatchName(file.getName());
      String patchPath = FileUtil.toSystemIndependentName(StringUtil.notNullize(file.getParent()));
      String valueToStore = StringUtil.isEmpty(patchPath) || patchPath.equals(getDefaultPatchPath(project)) ? null : patchPath;
      PropertiesComponent.getInstance(project).setValue(VCS_PATCH_PATH_KEY, valueToStore);
      VcsApplicationSettings.getInstance().PATCH_STORAGE_LOCATION = valueToStore;

      List<FilePatch> patches = patchBuilder.buildPatches(baseDir, changes, reversePatch, true);
      PatchWriter.writePatches(project, fileName, baseDir, patches, commitContext, encoding, true);

      WaitForProgressToShow.runOrInvokeLaterAboveProgress(() -> {
        final VcsConfiguration configuration = VcsConfiguration.getInstance(project);
        if (Boolean.TRUE.equals(configuration.SHOW_PATCH_IN_EXPLORER)) {
          RevealFileAction.openFile(file);
        }
        else if (configuration.SHOW_PATCH_IN_EXPLORER == null) {
          configuration.SHOW_PATCH_IN_EXPLORER = showDialog(project, file);
        }
      }, null, project);
    }

    @Override
    @Nullable
    public ValidationInfo validateFields() {
      return myPanel.validateFields();
    }

    @Nullable
    @Override
    public String getHelpId() {
      return "reference.dialogs.PatchFileSettings"; //NON-NLS
    }
  }

  public interface PatchBuilder {

    default boolean isReverseSupported() {return true;}

    List<FilePatch> buildPatches(String baseDir,
                                 @NotNull Collection<? extends Change> changes,
                                 boolean reversePatch, boolean honorExcludedFromCommit) throws VcsException;
  }

  public static class DefaultPatchBuilder implements PatchBuilder {
    private final Project myProject;

    public DefaultPatchBuilder(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public List<FilePatch> buildPatches(String baseDir,
                                        @NotNull Collection<? extends Change> changes,
                                        boolean reversePatch, boolean honorExcludedFromCommit) throws VcsException {
      return IdeaTextPatchBuilder.buildPatch(myProject, changes, baseDir, reversePatch, honorExcludedFromCommit);
    }
  }

  public static class ShelfPatchBuilder implements PatchBuilder {
    @NotNull private final Project myProject;
    @NotNull private final ShelvedChangeList myShelvedChangeList;
    @NotNull private final List<String> mySelectedPaths;

    public ShelfPatchBuilder(@NotNull Project project,
                             @NotNull ShelvedChangeList shelvedChangeList,
                             @NotNull List<String> selectedPaths) {
      myProject = project;
      myShelvedChangeList = shelvedChangeList;
      mySelectedPaths = selectedPaths;
    }

    @Override
    public boolean isReverseSupported() {
      return false;
    }

    @Override
    public List<FilePatch> buildPatches(String baseDir,
                                        @NotNull Collection<? extends Change> changes,
                                        boolean reversePatch,
                                        boolean honorExcludedFromCommit) throws VcsException {
      List<FilePatch> result = new ArrayList<>(createFilePatchesFromShelf(myProject, baseDir, myShelvedChangeList, mySelectedPaths));

      List<ShelvedBinaryFile> binaries = isEmpty(mySelectedPaths)
                                         ? myShelvedChangeList.getBinaryFiles()
                                         : filter(myShelvedChangeList.getBinaryFiles(), binary -> mySelectedPaths
                                           .contains(chooseNotNull(binary.AFTER_PATH, binary.BEFORE_PATH)));
      result.addAll(IdeaTextPatchBuilder.buildPatch(myProject, map(binaries, b -> b.createChange(myProject)), baseDir, reversePatch));

      return result;
    }
  }

  private static boolean checkIsFileValid(@NotNull Project project, @NotNull File file) {
    if (file.exists()) {
      final int[] result = new int[1];
      WaitForProgressToShow.runOrInvokeAndWaitAboveProgress(() -> {
        result[0] = Messages.showYesNoDialog(project,
                                             VcsBundle.message("patch.apply.already.exists.overwrite.prompt",
                                                               file.getName(), file.getParent()),
                                             VcsBundle.message("patch.creation.save.patch.file.title"),
                                             CommonBundle.message("button.overwrite"),
                                             CommonBundle.message("button.cancel"),
                                             Messages.getWarningIcon());
      });
      if (Messages.NO == result[0]) return false;
    }
    if (file.getParentFile() == null) {
      WaitForProgressToShow.runOrInvokeLaterAboveProgress(() -> {
        Messages.showErrorDialog(project, VcsBundle
                                   .message("create.patch.error.title", VcsBundle.message("patch.creation.can.not.write.patch.error", file.getPath())),
                                 CommonBundle.getErrorTitle());
      }, ModalityState.NON_MODAL, project);
      return false;
    }
    return true;
  }

  @NotNull
  private static String getDefaultPatchPath(@NotNull Project project) {
    String baseDir = project.getBasePath();
    return baseDir == null ? FileUtil.toSystemIndependentName(PathManager.getHomePath()) : baseDir;
  }

  private static Boolean showDialog(@NotNull Project project, @NotNull File file) {
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
        return UIBundle.message("dialog.options.do.not.ask");
      }
    };

    RevealFileAction.showDialog(project, message, title, file, option);

    return ref[0];
  }

  public static void writePatchToClipboard(@NotNull Project project,
                                           @NotNull String baseDir,
                                           @NotNull Collection<? extends Change> changes,
                                           boolean reversePatch,
                                           boolean honorExcludedFromCommit,
                                           @NotNull PatchBuilder patchBuilder,
                                           @NotNull CommitContext commitContext) throws VcsException, IOException {
    List<FilePatch> patches = patchBuilder.buildPatches(baseDir, changes, reversePatch, honorExcludedFromCommit);
    writeAsPatchToClipboard(project, patches, baseDir, commitContext);
    VcsNotifier.getInstance(project).notifySuccess(VcsBundle.message("patch.copied.to.clipboard"));
  }

  private static List<? extends FilePatch> createFilePatchesFromShelf(@NotNull Project project, String basePath,
                                                                      @NotNull ShelvedChangeList shelvedList,
                                                                      @Nullable Collection<String> selectedPaths) {
    try {
      List<TextFilePatch> textFilePatches = ShelveChangesManager.loadPatches(project, shelvedList.PATH, null);
      List<TextFilePatch> result =
        !isEmpty(selectedPaths) ? filter(textFilePatches, patch -> selectedPaths.contains(patch.getAfterName())) : textFilePatches;
      mapPatchesToNewBase(Objects.requireNonNull(project.getBasePath()), basePath, result);
      return result;
    }
    catch (Exception exception) {
      LOG.error(exception);
      return Collections.emptyList();
    }
  }

  private static void mapPatchesToNewBase(@NotNull String oldBase, @NotNull String newBase, @NotNull List<? extends FilePatch> patches) {
    for (FilePatch patch : patches) {
      patch.setBeforeName(patch.getBeforeName() == null
                          ? null
                          : TextPatchBuilder.getRelativePath(newBase, new File(oldBase, patch.getBeforeName()).getPath()));
      patch.setAfterName((patch.getAfterFileName() == null
                          ? null
                          : TextPatchBuilder.getRelativePath(newBase, new File(oldBase, patch.getAfterName()).getPath())));
    }
  }
}