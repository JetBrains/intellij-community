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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.ActionButtonPresentation;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.DiffRequestFactory;
import com.intellij.openapi.diff.MergeRequest;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class DiffRequestFactoryImpl extends DiffRequestFactory {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.mergeTool.DiffRequestFactoryImpl");

  public MergeRequest createMergeRequest(String leftText,
                                         String rightText,
                                         String originalContent,
                                         @NotNull VirtualFile file,
                                         Project project,
                                         @Nullable final ActionButtonPresentation actionButtonPresentation) {
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    return new MergeRequestImpl(leftText, createMergeVersion(file, document, originalContent), rightText, project, actionButtonPresentation);
  }

  public MergeRequest create3WayDiffRequest(final String leftText, final String rightText, final String originalContent, final Project project,
                                            @Nullable final ActionButtonPresentation actionButtonPresentation) {
    return new MergeRequestImpl(leftText, originalContent, rightText, project, actionButtonPresentation);
  }

  private MergeVersion createMergeVersion(VirtualFile file,
                                          final Document document,
                                          final String centerText) {
    if (document != null) {
      return new MergeVersion.MergeDocumentVersion(document, centerText);
    }
    else {
      return new MyMergeVersion(centerText, file);
    }
  }

  private class MyMergeVersion implements MergeVersion {
    private final String myCenterText;
    private final VirtualFile myFile;

    public MyMergeVersion(String centerText, VirtualFile file) {
      myCenterText = centerText;
      myFile = file;
    }

    public FileType getContentType() {
      return FileTypeManager.getInstance().getFileTypeByFile(myFile);
    }

    public String getTextBeforeMerge() {
      return myCenterText;
    }

    public byte[] getBytes() throws IOException {
      return myCenterText.getBytes(myFile.getCharset().name());
    }

    public VirtualFile getFile() {
      return myFile;
    }

    public Document createWorkingDocument(Project project) {
      return EditorFactory.getInstance().createDocument(myCenterText);
    }


    public void applyText(final String text, final Project project) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          CommandProcessor.getInstance().executeCommand(project, new Runnable() {
            public void run() {
              storeChangedContent(text);
            }

          }, DiffBundle.message("merge.dialog.writing.to.file.command.name"), null);
        }
      });
    }

    private void storeChangedContent(String text) {
      final Document content = FileDocumentManager.getInstance().getDocument(myFile);
      content.replaceString(0, content.getTextLength(), text);
    }
  }
}
