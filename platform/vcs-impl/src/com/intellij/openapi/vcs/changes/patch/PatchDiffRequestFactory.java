/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffRequestFactory;
import com.intellij.diff.InvalidDiffRequestException;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.merge.MergeRequest;
import com.intellij.diff.merge.MergeResult;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.diff.impl.patch.apply.GenericPatchApplier;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer;
import com.intellij.openapi.vcs.changes.patch.tool.ApplyPatchDiffRequest;
import com.intellij.openapi.vcs.changes.patch.tool.ApplyPatchMergeRequest;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PatchDiffRequestFactory {
  @NotNull
  public static DiffRequest createDiffRequest(@Nullable Project project,
                                              @NotNull Change change,
                                              @NotNull String name,
                                              @NotNull UserDataHolder context,
                                              @NotNull ProgressIndicator indicator)
    throws DiffRequestProducerException {
    ChangeDiffRequestProducer proxyProducer = ChangeDiffRequestProducer.create(project, change);
    if (proxyProducer == null) throw new DiffRequestProducerException("Can't show diff for '" + name + "'");
    return proxyProducer.process(context, indicator);
  }

  @NotNull
  public static DiffRequest createConflictDiffRequest(@Nullable Project project,
                                                      @Nullable VirtualFile file,
                                                      @NotNull TextFilePatch patch,
                                                      @NotNull String afterTitle,
                                                      @NotNull final Getter<ApplyPatchForBaseRevisionTexts> textsGetter,
                                                      @NotNull String name,
                                                      @NotNull UserDataHolder context,
                                                      @NotNull ProgressIndicator indicator)
    throws DiffRequestProducerException {
    if (file == null) throw new DiffRequestProducerException("Can't show diff for '" + name + "'");
    if (file.getFileType().isBinary()) throw new DiffRequestProducerException("Can't show diff for binary file '" + name + "'");

    final Ref<ApplyPatchForBaseRevisionTexts> textsRef = new Ref<>();
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        textsRef.set(textsGetter.get());
      }
    }, indicator.getModalityState());
    ApplyPatchForBaseRevisionTexts texts = textsRef.get();

    if (texts.getLocal() == null) throw new DiffRequestProducerException("Can't show diff for '" + file.getPresentableUrl() + "'");

    if (texts.getBase() == null) {
      String localContent = texts.getLocal().toString();

      final GenericPatchApplier applier = new GenericPatchApplier(localContent, patch.getHunks());
      applier.execute();

      final AppliedTextPatch appliedTextPatch = AppliedTextPatch.create(applier.getAppliedInfo());
      return createBadDiffRequest(project, file, localContent, appliedTextPatch, null, null, null, null);
    }
    else {
      String localContent = texts.getLocal().toString();
      String baseContent = texts.getBase().toString();
      String patchedContent = texts.getPatched();

      return createDiffRequest(project, file, ContainerUtil.list(localContent, baseContent, patchedContent), null,
                               ContainerUtil.list("Current Version", "Base Version", afterTitle));
    }
  }

  @NotNull
  public static DiffRequest createDiffRequest(@Nullable Project project,
                                              @Nullable VirtualFile file,
                                              @NotNull List<String> contents,
                                              @Nullable String windowTitle,
                                              @NotNull List<String> titles) {
    assert contents.size() == 3;
    assert titles.size() == 3;

    if (windowTitle == null) windowTitle = getPatchTitle(file);

    String localTitle = StringUtil.notNullize(titles.get(0), VcsBundle.message("patch.apply.conflict.local.version"));
    String baseTitle = StringUtil.notNullize(titles.get(1), "Base Version");
    String patchedTitle = StringUtil.notNullize(titles.get(2), VcsBundle.message("patch.apply.conflict.patched.version"));

    FileType fileType = file != null ? file.getFileType() : null;

    DiffContentFactory contentFactory = DiffContentFactory.getInstance();
    DocumentContent localContent = file != null ? contentFactory.createDocument(project, file) : null;
    if (localContent == null) localContent = contentFactory.create(contents.get(0), fileType);
    DocumentContent baseContent = contentFactory.create(contents.get(1), fileType);
    DocumentContent patchedContent = contentFactory.create(contents.get(2), fileType);

    return new SimpleDiffRequest(windowTitle, localContent, baseContent, patchedContent,
                                 localTitle, baseTitle, patchedTitle);
  }

  @NotNull
  public static DiffRequest createBadDiffRequest(@Nullable Project project,
                                                 @NotNull VirtualFile file,
                                                 @NotNull String localContent,
                                                 @NotNull AppliedTextPatch textPatch,
                                                 @Nullable String windowTitle,
                                                 @Nullable String localTitle,
                                                 @Nullable String resultTitle,
                                                 @Nullable String patchTitle) {
    if (windowTitle == null) windowTitle = getBadPatchTitle(file);
    if (localTitle == null) localTitle = VcsBundle.message("patch.apply.conflict.local.version");
    if (resultTitle == null) resultTitle = VcsBundle.message("patch.apply.conflict.patched.somehow.version");
    if (patchTitle == null) patchTitle = VcsBundle.message("patch.apply.conflict.patch");

    DocumentContent resultContent = DiffContentFactory.getInstance().createDocument(project, file);
    if (resultContent == null) resultContent = DiffContentFactory.getInstance().create(localContent, file);
    return new ApplyPatchDiffRequest(resultContent, textPatch, localContent, windowTitle, localTitle, resultTitle, patchTitle);
  }

  @NotNull
  public static MergeRequest createMergeRequest(@Nullable Project project,
                                                @NotNull Document document,
                                                @NotNull VirtualFile file,
                                                @NotNull String baseContent,
                                                @NotNull String localContent,
                                                @NotNull String patchedContent,
                                                @Nullable Consumer<MergeResult> callback)
    throws InvalidDiffRequestException {
    List<String> titles = ContainerUtil.list(null, null, null);
    List<String> contents = ContainerUtil.list(localContent, baseContent, patchedContent);

    return createMergeRequest(project, document, file, contents, null, titles, callback);
  }

  @NotNull
  public static MergeRequest createBadMergeRequest(@Nullable Project project,
                                                   @NotNull Document document,
                                                   @NotNull VirtualFile file,
                                                   @NotNull String localContent,
                                                   @NotNull AppliedTextPatch textPatch,
                                                   @Nullable Consumer<MergeResult> callback)
    throws InvalidDiffRequestException {
    return createBadMergeRequest(project, document, file, localContent, textPatch, null, null, null, null, callback);
  }

  @NotNull
  public static MergeRequest createMergeRequest(@Nullable Project project,
                                                @NotNull Document document,
                                                @Nullable VirtualFile file,
                                                @NotNull List<String> contents,
                                                @Nullable String windowTitle,
                                                @NotNull List<String> titles,
                                                @Nullable Consumer<MergeResult> callback)
    throws InvalidDiffRequestException {
    assert contents.size() == 3;
    assert titles.size() == 3;

    if (windowTitle == null) windowTitle = getPatchTitle(file);

    String localTitle = StringUtil.notNullize(titles.get(0), VcsBundle.message("patch.apply.conflict.local.version"));
    String baseTitle = StringUtil.notNullize(titles.get(1), VcsBundle.message("patch.apply.conflict.merged.version"));
    String patchedTitle = StringUtil.notNullize(titles.get(2), VcsBundle.message("patch.apply.conflict.patched.version"));

    List<String> actualTitles = ContainerUtil.list(localTitle, baseTitle, patchedTitle);

    FileType fileType = file != null ? file.getFileType() : null;
    return DiffRequestFactory.getInstance().createMergeRequest(project, fileType, document, contents, windowTitle, actualTitles, callback);
  }

  @NotNull
  public static MergeRequest createBadMergeRequest(@Nullable Project project,
                                                   @NotNull Document document,
                                                   @Nullable VirtualFile file,
                                                   @NotNull String localContent,
                                                   @NotNull AppliedTextPatch textPatch,
                                                   @Nullable String windowTitle,
                                                   @Nullable String localTitle,
                                                   @Nullable String resultTitle,
                                                   @Nullable String patchTitle,
                                                   @Nullable Consumer<MergeResult> callback)
    throws InvalidDiffRequestException {
    if (!DiffUtil.canMakeWritable(document)) {
      throw new InvalidDiffRequestException("Output is read only" + (file != null ? " : '" + file.getPresentableUrl() +"'": ""));
    }

    if (windowTitle == null) windowTitle = getBadPatchTitle(file);
    if (localTitle == null) localTitle = VcsBundle.message("patch.apply.conflict.local.version");
    if (resultTitle == null) resultTitle = VcsBundle.message("patch.apply.conflict.patched.somehow.version");
    if (patchTitle == null) patchTitle = VcsBundle.message("patch.apply.conflict.patch");

    DocumentContent resultContent = DiffContentFactory.getInstance().create(project, document, file);
    return new ApplyPatchMergeRequest(project, resultContent, textPatch, localContent,
                                      windowTitle, localTitle, resultTitle, patchTitle, callback);
  }

  @NotNull
  private static String getPatchTitle(@Nullable VirtualFile file) {
    if (file != null) {
      return VcsBundle.message("patch.apply.conflict.title", getPresentablePath(file));
    }
    else {
      return "Patch Conflict";
    }
  }


  @NotNull
  private static String getBadPatchTitle(@Nullable VirtualFile file) {
    if (file != null) {
      return "Result of Patch Apply to " + getPresentablePath(file);
    }
    else {
      return "Result of Patch Apply";
    }
  }

  @NotNull
  private static String getPresentablePath(@NotNull VirtualFile file) {
    String fullPath = file.getParent() == null ? file.getPath() : file.getParent().getPath();
    return file.getName() + " (" + fullPath + ")";
  }
}

