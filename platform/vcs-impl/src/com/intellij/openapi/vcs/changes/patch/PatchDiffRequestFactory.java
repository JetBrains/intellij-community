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
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PatchDiffRequestFactory {
  @NotNull
  public static DiffRequest createFromChange(@Nullable Project project,
                                             @NotNull Change change,
                                             @NotNull String name,
                                             @NotNull UserDataHolder context,
                                             @NotNull ProgressIndicator indicator)
    throws DiffRequestProducerException {
    ChangeDiffRequestProducer proxyProducer = ChangeDiffRequestProducer.create(project, change);
    if (proxyProducer == null) throw new DiffRequestProducerException("Can't show diff for '" + name + "'");
    return proxyProducer.process(context, indicator);
  }

  public static DiffRequest createConflict(@Nullable Project project,
                                           @Nullable VirtualFile file,
                                           @NotNull String afterTitle,
                                           @NotNull final Getter<ApplyPatchForBaseRevisionTexts> textsGetter,
                                           @NotNull String name,
                                           @NotNull UserDataHolder context,
                                           @NotNull ProgressIndicator indicator)
    throws DiffRequestProducerException {
    if (file == null) throw new DiffRequestProducerException("Can't show diff for '" + name + "'");
    if (file.getFileType().isBinary()) throw new DiffRequestProducerException("Can't show diff for binary file '" + name + "'");

    final Ref<ApplyPatchForBaseRevisionTexts> textsRef = new Ref<ApplyPatchForBaseRevisionTexts>();
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        textsRef.set(textsGetter.get());
      }
    }, indicator.getModalityState());
    ApplyPatchForBaseRevisionTexts texts = textsRef.get();

    if (texts.getBase() == null) {
      return ApplyPatchAction.createBadDiffRequest(project, file, texts);
    }
    else {
      String path = FileUtil.toSystemDependentName(file.getPresentableUrl());
      FileType type = file.getFileType();

      String windowTitle = VcsBundle.message("patch.apply.conflict.title", path);

      DiffContentFactory contentFactory = DiffContentFactory.getInstance();
      DocumentContent localContent = contentFactory.createDocument(project, file);
      if (localContent == null) localContent = contentFactory.create(texts.getLocal().toString(), type);
      DocumentContent baseContent = contentFactory.create(texts.getBase().toString(), type);
      DocumentContent patchedContent = contentFactory.create(texts.getPatched(), type);

      return new SimpleDiffRequest(windowTitle, localContent, baseContent, patchedContent,
                                   "Current Version", "Base Version", afterTitle);
    }
  }
}

