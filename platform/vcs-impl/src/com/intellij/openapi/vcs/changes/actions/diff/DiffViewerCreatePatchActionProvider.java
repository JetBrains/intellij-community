// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff;

import com.intellij.codeInsight.daemon.OutsidersPsiFileSupport;
import com.intellij.diff.DiffVcsDataKeys;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.contents.EmptyContent;
import com.intellij.diff.contents.FileContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionExtensionProvider;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.changes.SimpleContentRevision;
import com.intellij.openapi.vcs.changes.actions.CreatePatchFromChangesAction;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@ApiStatus.Internal
public class DiffViewerCreatePatchActionProvider implements AnActionExtensionProvider {
  private final boolean mySilentClipboard;

  private DiffViewerCreatePatchActionProvider(boolean silentClipboard) {
    mySilentClipboard = silentClipboard;
  }

  public static class Dialog extends DiffViewerCreatePatchActionProvider {
    public Dialog() {
      super(false);
    }
  }

  public static class Clipboard extends DiffViewerCreatePatchActionProvider {
    public Clipboard() {
      super(true);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
  
  @Override
  public boolean isActive(@NotNull AnActionEvent e) {
    return e.getData(DiffDataKeys.DIFF_VIEWER) != null;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DiffRequest request = e.getData(DiffDataKeys.DIFF_REQUEST);
    boolean isEnabled = request != null && isSupported(request);
    e.getPresentation().setEnabledAndVisible(isEnabled);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DiffRequest request = Objects.requireNonNull(e.getData(DiffDataKeys.DIFF_REQUEST));
    Change change = createChange(request);
    CreatePatchFromChangesAction.createPatch(e.getProject(), null, Collections.singletonList(change), mySilentClipboard);
  }

  private static boolean isSupported(@NotNull DiffRequest request) {
    if (request.getUserData(ChangeDiffRequestProducer.CHANGE_KEY) != null) return true;

    ContentDiffRequest contentRequest = ObjectUtils.tryCast(request, ContentDiffRequest.class);
    if (contentRequest == null) return false;

    List<DiffContent> contents = contentRequest.getContents();
    if (contents.size() != 2) return false;

    for (DiffContent content : contents) {
      boolean canShow = content instanceof DocumentContent ||
                        content instanceof EmptyContent;
      if (!canShow) return false;
    }
    return true;
  }

  @NotNull
  private static Change createChange(@NotNull DiffRequest request) {
    Change change = request.getUserData(ChangeDiffRequestProducer.CHANGE_KEY);
    if (change != null) return change;

    List<DiffContent> contents = ((ContentDiffRequest)request).getContents();
    List<@Nls String> titles = ((ContentDiffRequest)request).getContentTitles();
    assert contents.size() == 2;
    String bTitle = ObjectUtils.chooseNotNull(titles.get(0), DiffBundle.message("diff.version.title.before"));
    String aTitle = ObjectUtils.chooseNotNull(titles.get(1), DiffBundle.message("diff.version.title.after"));
    ContentRevision bRev = createRevision(contents.get(0), bTitle);
    ContentRevision aRev = createRevision(contents.get(1), aTitle);
    return new Change(bRev, aRev);
  }

  @Nullable
  private static ContentRevision createRevision(@NotNull DiffContent content, @NotNull String title) {
    if (content instanceof EmptyContent) return null;
    if (content instanceof FileContent) {
      VirtualFile file = ((FileContent)content).getFile();
      if (file.isInLocalFileSystem()) {
        return new CurrentContentRevision(VcsUtil.getFilePath(file));
      }
    }
    if (content instanceof DocumentContent) {
      String text = ((DocumentContent)content).getDocument().getText();

      Pair<FilePath, VcsRevisionNumber> info = content.getUserData(DiffVcsDataKeys.REVISION_INFO);
      if (info != null) {
        return new SimpleContentRevision(text, info.first, info.second.asString());
      }

      return new SimpleContentRevision(text, guessFilePath(content, title), "");
    }
    throw new IllegalStateException(content.toString());
  }

  @NotNull
  private static FilePath guessFilePath(@NotNull DiffContent content, @NotNull String title) {
    if (content instanceof FileContent) {
      VirtualFile file = ((FileContent)content).getFile();
      String path = OutsidersPsiFileSupport.getOriginalFilePath(file);
      if (path != null) return VcsUtil.getFilePath(path, file.isDirectory());
    }

    String fileName = content.getUserData(DiffUserDataKeysEx.FILE_NAME);
    if (fileName == null && content instanceof DocumentContent) {
      VirtualFile highlightFile = ((DocumentContent)content).getHighlightFile();
      fileName = highlightFile != null ? highlightFile.getName() : null;
    }
    if (fileName != null) return VcsUtil.getFilePath(fileName, false);

    FileType fileType = content.getContentType();
    String ext = fileType != null ? fileType.getDefaultExtension() : null;
    if (StringUtil.isEmptyOrSpaces(ext)) ext = "tmp";

    String path = PathUtil.suggestFileName(title + "." + ext, true, false);
    return VcsUtil.getFilePath(path, false);
  }
}
