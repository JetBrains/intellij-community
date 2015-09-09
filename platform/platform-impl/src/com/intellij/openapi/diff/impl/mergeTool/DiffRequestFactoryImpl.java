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
package com.intellij.openapi.diff.impl.mergeTool;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.ActionButtonPresentation;
import com.intellij.openapi.diff.DiffRequestFactory;
import com.intellij.openapi.diff.MergeRequest;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DiffRequestFactoryImpl extends DiffRequestFactory {
  private static final Logger LOG = Logger.getInstance(DiffRequestFactoryImpl.class);

  @Override
  public MergeRequest createMergeRequest(@NotNull String leftText,
                                         @NotNull String rightText,
                                         @NotNull String originalContent,
                                         @NotNull VirtualFile file,
                                         @Nullable Project project,
                                         @Nullable ActionButtonPresentation okButtonPresentation,
                                         @Nullable ActionButtonPresentation cancelButtonPresentation) {
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document != null) {
      return new MergeRequestImpl(leftText, new MergeVersion.MergeDocumentVersion(document, originalContent), rightText, project,
                                  okButtonPresentation,
                                  cancelButtonPresentation);
    }
    else {
      LOG.warn("Document not found for " + file.getPresentableUrl() + "; FileType - " + file.getFileType().getName() + "; valid - " + file.isValid());
      return create3WayDiffRequest(leftText, rightText, originalContent, file.getFileType(), project, okButtonPresentation, cancelButtonPresentation);
    }
  }

  @Override
  public MergeRequest create3WayDiffRequest(@NotNull String leftText,
                                            @NotNull String rightText,
                                            @NotNull String originalContent,
                                            @Nullable FileType type,
                                            @Nullable Project project,
                                            @Nullable ActionButtonPresentation okButtonPresentation,
                                            @Nullable ActionButtonPresentation cancelButtonPresentation) {
    return new MergeRequestImpl(leftText, originalContent, rightText, type, project, okButtonPresentation, cancelButtonPresentation);
  }

  @Override
  public MergeRequest create3WayDiffRequest(@NotNull String leftText,
                                            @NotNull String rightText,
                                            @NotNull String originalContent,
                                            @Nullable Project project,
                                            @Nullable ActionButtonPresentation okButtonPresentation,
                                            @Nullable ActionButtonPresentation cancelButtonPresentation) {
    return create3WayDiffRequest(leftText, rightText, originalContent, null, project, okButtonPresentation, cancelButtonPresentation);
  }
}
