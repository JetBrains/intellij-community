// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffEditorTitleCustomizer;
import com.intellij.diff.DiffRequestFactory;
import com.intellij.diff.InvalidDiffRequestException;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.merge.MergeCallback;
import com.intellij.diff.merge.MergeRequest;
import com.intellij.diff.merge.MergeResult;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.diff.impl.patch.apply.GenericPatchApplier;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer;
import com.intellij.openapi.vcs.changes.patch.tool.ApplyPatchDiffRequest;
import com.intellij.openapi.vcs.changes.patch.tool.ApplyPatchMergeRequest;
import com.intellij.openapi.vcs.history.DiffTitleFilePathCustomizer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.*;

import java.util.Arrays;
import java.util.List;

public final class PatchDiffRequestFactory {
  @NotNull
  public static DiffRequest createDiffRequest(@Nullable Project project,
                                              @NotNull Change change,
                                              @NotNull String name,
                                              @NotNull UserDataHolder context,
                                              @NotNull ProgressIndicator indicator)
    throws DiffRequestProducerException {
    ChangeDiffRequestProducer proxyProducer = ChangeDiffRequestProducer.create(project, change);
    if (proxyProducer == null) throw new DiffRequestProducerException(VcsBundle.message("changes.error.can.t.show.diff.for",  name));
    return proxyProducer.process(context, indicator);
  }

  @ApiStatus.Internal
  @NotNull
  @CalledInAny
  public static DiffRequest createConflictDiffRequest(@Nullable Project project,
                                                      @Nullable VirtualFile file,
                                                      @NotNull TextFilePatch patch,
                                                      @NotNull @NlsContexts.Label String afterTitle,
                                                      @NotNull final ApplyPatchForBaseRevisionTexts texts,
                                                      @NotNull String name)
    throws DiffRequestProducerException {
    if (file == null) throw new DiffRequestProducerException(VcsBundle.message("changes.error.can.t.show.diff.for",  name));

    if (file.getFileType().isBinary()) {
      throw new DiffRequestProducerException(VcsBundle.message("changes.error.can.t.show.diff.for.binary.file", name));
    }

    if (texts.getBase() == null) {
      String localContent = texts.getLocal();

      final GenericPatchApplier applier = new GenericPatchApplier(localContent, patch.getHunks());
      applier.execute();

      final AppliedTextPatch appliedTextPatch = AppliedTextPatch.create(applier.getAppliedInfo());
      return createBadDiffRequest(project, file, localContent, appliedTextPatch, null, null,
                                  DiffBundle.message("merge.version.title.current"), null);
    }
    else {
      String localContent = texts.getLocal();
      String baseContent = texts.getBase();
      String patchedContent = texts.getPatched();

      return createDiffRequest(project, file, Arrays.asList(localContent, baseContent, patchedContent),
                               Arrays.asList(DiffBundle.message("merge.version.title.current"), DiffBundle.message("merge.version.title.base"),
                                             afterTitle));
    }
  }

  @NotNull
  private static DiffRequest createDiffRequest(@Nullable Project project,
                                               @NotNull VirtualFile file,
                                               @NotNull List<String> contents,
                                               @NotNull List<@NlsContexts.Label String> titles) {
    assert contents.size() == 3;
    assert titles.size() == 3;

    String localTitle = StringUtil.notNullize(titles.get(0), VcsBundle.message("patch.apply.conflict.local.version"));
    String baseTitle = StringUtil.notNullize(titles.get(1), DiffBundle.message("merge.version.title.base"));
    String patchedTitle = StringUtil.notNullize(titles.get(2), VcsBundle.message("patch.apply.conflict.patched.version"));

    FileType fileType = file.getFileType();

    DiffContentFactory contentFactory = DiffContentFactory.getInstance();
    DocumentContent localContent = contentFactory.createDocument(project, file);
    if (localContent == null) localContent = contentFactory.create(project, contents.get(0), fileType);
    DocumentContent baseContent = contentFactory.create(project, contents.get(1), fileType);
    DocumentContent patchedContent = contentFactory.create(project, contents.get(2), fileType);

    SimpleDiffRequest request = new SimpleDiffRequest(null, localContent, baseContent, patchedContent,
                                                      localTitle, baseTitle, patchedTitle);
    return DiffUtil.addTitleCustomizers(request, get3WayDiffCustomizers(project, file, baseTitle));
  }

  @NotNull
  public static DiffRequest createBadDiffRequest(@Nullable Project project,
                                                 @NotNull VirtualFile file,
                                                 @NotNull @NonNls String localContent,
                                                 @NotNull AppliedTextPatch textPatch,
                                                 @Nullable @NlsContexts.DialogTitle String windowTitle,
                                                 @Nullable @NlsContexts.Label String localTitle,
                                                 @Nullable @NlsContexts.Label String resultTitle,
                                                 @Nullable @NlsContexts.Label String patchTitle) {
    if (windowTitle == null) windowTitle = getBadPatchTitle(file);
    if (localTitle == null) localTitle = VcsBundle.message("patch.apply.conflict.local.version");
    if (resultTitle == null) resultTitle = VcsBundle.message("patch.apply.conflict.patched.somehow.version");
    if (patchTitle == null) patchTitle = VcsBundle.message("patch.apply.conflict.patch");

    DocumentContent resultContent = DiffContentFactory.getInstance().createDocument(project, file);
    if (resultContent == null) resultContent = DiffContentFactory.getInstance().create(project, localContent, file);
    DiffRequest request =
      new ApplyPatchDiffRequest(resultContent, textPatch, localContent, windowTitle, localTitle, resultTitle, patchTitle);

    return DiffUtil.addTitleCustomizers(request, get3WayDiffCustomizers(project, file, resultTitle));
  }

  private static @NotNull List<DiffEditorTitleCustomizer> get3WayDiffCustomizers(Project project, VirtualFile file, String centerTitle) {
    return Arrays.asList(
      DiffTitleFilePathCustomizer.EMPTY_CUSTOMIZER,
      DiffTitleFilePathCustomizer.getTitleCustomizer(project, VcsUtil.getFilePath(file), centerTitle),
      DiffTitleFilePathCustomizer.EMPTY_CUSTOMIZER
    );
  }

  @NotNull
  public static MergeRequest createMergeRequest(@Nullable Project project,
                                                @NotNull Document document,
                                                @NotNull VirtualFile file,
                                                @NotNull @NonNls String baseContent,
                                                @NotNull @NonNls String localContent,
                                                @NotNull @NonNls String patchedContent,
                                                @Nullable Consumer<? super MergeResult> callback)
    throws InvalidDiffRequestException {
    List<String> titles = Arrays.asList(null, null, null);
    List<String> contents = Arrays.asList(localContent, baseContent, patchedContent);

    return createMergeRequest(project, document, file, contents, null, titles, callback);
  }

  @NotNull
  public static MergeRequest createBadMergeRequest(@Nullable Project project,
                                                   @NotNull Document document,
                                                   @NotNull VirtualFile file,
                                                   @NotNull String localContent,
                                                   @NotNull AppliedTextPatch textPatch,
                                                   @Nullable Consumer<? super MergeResult> callback)
    throws InvalidDiffRequestException {
    return createBadMergeRequest(project, document, file, localContent, textPatch, null, null, null, null, callback);
  }

  @NotNull
  public static MergeRequest createMergeRequest(@Nullable Project project,
                                                @NotNull Document document,
                                                @Nullable VirtualFile file,
                                                @NotNull List<String> contents,
                                                @Nullable @NlsContexts.DialogTitle String windowTitle,
                                                @NotNull List<@NlsContexts.Label String> titles,
                                                @Nullable Consumer<? super MergeResult> callback)
    throws InvalidDiffRequestException {
    assert contents.size() == 3;
    assert titles.size() == 3;

    if (windowTitle == null) windowTitle = getPatchTitle(file);

    String localTitle = StringUtil.notNullize(titles.get(0), VcsBundle.message("patch.apply.conflict.local.version"));
    String baseTitle = StringUtil.notNullize(titles.get(1), VcsBundle.message("patch.apply.conflict.merged.version"));
    String patchedTitle = StringUtil.notNullize(titles.get(2), VcsBundle.message("patch.apply.conflict.patched.version"));

    List<@NlsContexts.Label String> actualTitles = Arrays.asList(localTitle, baseTitle, patchedTitle);

    FileType fileType = file != null ? file.getFileType() : null;
    return DiffRequestFactory.getInstance().createMergeRequest(project, fileType, document, contents, windowTitle, actualTitles, callback);
  }

  @NotNull
  public static MergeRequest createBadMergeRequest(@Nullable Project project,
                                                   @NotNull Document document,
                                                   @Nullable VirtualFile file,
                                                   @NotNull String localContent,
                                                   @NotNull AppliedTextPatch textPatch,
                                                   @Nullable @NlsContexts.DialogTitle String windowTitle,
                                                   @Nullable @NlsContexts.Label String localTitle,
                                                   @Nullable @NlsContexts.Label String resultTitle,
                                                   @Nullable @NlsContexts.Label String patchTitle,
                                                   @Nullable Consumer<? super MergeResult> callback)
    throws InvalidDiffRequestException {
    if (!DiffUtil.canMakeWritable(document)) {
      throw new InvalidDiffRequestException("Output is read only" + (file != null ? " : '" + file.getPresentableUrl() +"'": ""));
    }

    if (windowTitle == null) windowTitle = getBadPatchTitle(file);
    if (localTitle == null) localTitle = VcsBundle.message("patch.apply.conflict.local.version");
    if (resultTitle == null) resultTitle = VcsBundle.message("patch.apply.conflict.patched.somehow.version");
    if (patchTitle == null) patchTitle = VcsBundle.message("patch.apply.conflict.patch");

    DocumentContent resultContent = DiffContentFactory.getInstance().create(project, document, file);
    ApplyPatchMergeRequest request = new ApplyPatchMergeRequest(project, resultContent, textPatch, localContent,
                                                                windowTitle, localTitle, resultTitle, patchTitle);
    return MergeCallback.register(request, callback);
  }

  @NotNull
  private static @NlsContexts.DialogTitle String getPatchTitle(@Nullable VirtualFile file) {
    if (file != null) {
      return VcsBundle.message("patch.apply.conflict.for.title", getPresentablePath(file));
    }
    else {
      return VcsBundle.message("patch.apply.conflict.title");
    }
  }


  @Nls
  @NotNull
  private static String getBadPatchTitle(@Nullable VirtualFile file) {
    if (file != null) {
      return VcsBundle.message("patch.apply.bad.diff.to.title", getPresentablePath(file));
    }
    else {
      return VcsBundle.message("patch.apply.bad.diff.title");
    }
  }

  @Nls
  @NotNull
  private static String getPresentablePath(@NotNull VirtualFile file) {
    String fullPath = file.getParent() == null ? file.getPresentableUrl() : file.getParent().getPresentableUrl();
    return file.getName() + " (" + fullPath + ")";
  }
}

