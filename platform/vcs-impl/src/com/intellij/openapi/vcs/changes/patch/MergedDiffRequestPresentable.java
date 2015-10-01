/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.actions.*;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class MergedDiffRequestPresentable implements DiffRequestPresentable {
  private final Project myProject;
  private final VirtualFile myFile;
  private final String myAfterTitle;
  private final Getter<ApplyPatchForBaseRevisionTexts> myTexts;

  public MergedDiffRequestPresentable(final Project project,
                                      final Getter<ApplyPatchForBaseRevisionTexts> texts,
                                      final VirtualFile file,
                                      final String afterTitle) {
    myTexts = texts;
    myProject = project;
    myFile = file;
    myAfterTitle = afterTitle;
  }

  public MyResult step(DiffChainContext context) {
    if (myFile.getFileType().isBinary()) {
      final boolean nowItIsText = ChangeDiffRequestPresentable.checkAssociate(myProject, myFile.getName(), context);
      if (!nowItIsText) {
        final SimpleDiffRequest request = new SimpleDiffRequest(myProject, null);
        return new MyResult(request, DiffPresentationReturnValue.removeFromList);
      }
    }
    final ApplyPatchForBaseRevisionTexts revisionTexts = myTexts.get();
    if (revisionTexts.getBase() == null) {
      final SimpleDiffRequest badDiffRequest = createBadDiffRequest(myProject, myFile, revisionTexts, true);
      return new MyResult(badDiffRequest, DiffPresentationReturnValue.useRequest);
    }
    final MergeRequest request = DiffRequestFactory.getInstance()
      .create3WayDiffRequest(revisionTexts.getLocal().toString(),
                             revisionTexts.getPatched(),
                             revisionTexts.getBase().toString(),
                             myFile.getFileType(), myProject, null, null);
    request.setWindowTitle(VcsBundle.message("patch.apply.conflict.title", FileUtil.toSystemDependentName(myFile.getPresentableUrl())));
    request.setVersionTitles(new String[]{"Current Version", "Base Version", FileUtil.toSystemDependentName(myAfterTitle)});
    return new MyResult(request, DiffPresentationReturnValue.useRequest);
  }

  @Override
  public String getPathPresentation() {
    return myFile.getPath();
  }

  public void haveStuff() {
  }

  public List<? extends AnAction> createActions(DiffExtendUIFactory uiFactory) {
    return Collections.emptyList();
  }

  @NotNull
  public static SimpleDiffRequest createBadDiffRequest(@Nullable final Project project,
                                                       @NotNull final VirtualFile file,
                                                       @NotNull ApplyPatchForBaseRevisionTexts texts,
                                                       boolean readonly) {
    final String fullPath = file.getParent() == null ? file.getPath() : file.getParent().getPath();
    final String title = "Result Of Patch Apply To " + file.getName() + " (" + fullPath + ")";

    final SimpleDiffRequest simpleRequest = new SimpleDiffRequest(project, title);
    final DocumentImpl patched = new DocumentImpl(texts.getPatched());
    patched.setReadOnly(false);

    final DocumentContent mergedContent =
      new DocumentContent(project, patched, file.getFileType());
    mergedContent.getDocument().setReadOnly(readonly);
    final SimpleContent originalContent = new SimpleContent(texts.getLocal().toString(), file.getFileType());

    simpleRequest.setContents(originalContent, mergedContent);
    simpleRequest.setContentTitles(VcsBundle.message("diff.title.local"), "Patched (with problems)");
    simpleRequest.addHint(DiffTool.HINT_SHOW_MODAL_DIALOG);
    simpleRequest.addHint(DiffTool.HINT_DIFF_IS_APPROXIMATE);

    if (!readonly) {
      simpleRequest.setOnOkRunnable(new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              final String resultText = mergedContent.getDocument().getText();
              final Document document = FileDocumentManager.getInstance().getDocument(file);
              if (document == null) {
                try {
                  VfsUtil.saveText(file, resultText);
                }
                catch (IOException e) {
                  // todo bad: we had already returned success by now
                  showIOException(project, file.getName(), e);
                }
              }
              else {
                document.setText(resultText);
                FileDocumentManager.getInstance().saveDocument(document);
              }
            }
          });
        }
      });
    }
    return simpleRequest;
  }

  private static void showIOException(@Nullable Project project, @NotNull String name, @NotNull IOException e) {
    Messages.showErrorDialog(project,
                             VcsBundle.message("patch.apply.error", name, e.getMessage()),
                             VcsBundle.message("patch.apply.dialog.title"));
  }
}
