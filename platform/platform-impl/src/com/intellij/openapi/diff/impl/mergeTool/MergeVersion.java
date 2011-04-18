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
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

public interface MergeVersion {
  Document createWorkingDocument(Project project);

  void applyText(String text, Project project);

  VirtualFile getFile();

  byte[] getBytes() throws IOException;

  FileType getContentType();

  void restoreOriginalContent(Project project);

  class MergeDocumentVersion implements MergeVersion {
    private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.mergeTool.MergeVersion.MergeDocumentVersion");
    protected final Document myDocument;
    private final String myOriginalText;
    private String myTextBeforeMerge;

    public MergeDocumentVersion(Document document, String originalText) {
      LOG.assertTrue(originalText != null, "text should not be null");
      LOG.assertTrue(document != null, "document should not be null");
      LOG.assertTrue(document.isWritable(), "document should be writable");
      myDocument = document;
      myOriginalText = originalText;
    }

    public Document createWorkingDocument(final Project project) {
      //TODO[ik]: do we really need to create copy here?
      final Document workingDocument = myDocument; //DocumentUtil.createCopy(myDocument, project);
      //LOG.assertTrue(workingDocument != myDocument);
      workingDocument.setReadOnly(false);
      final DocumentReference ref = DocumentReferenceManager.getInstance().create(workingDocument);
      myTextBeforeMerge = myDocument.getText();
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          setDocumentText(workingDocument, myOriginalText, DiffBundle.message("merge.init.merge.content.command.name"), project);
          UndoManager.getInstance(project).nonundoableActionPerformed(ref, false);
        }
      });
      return workingDocument;
    }

    public void applyText(final String text, final Project project) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          doApplyText(text, project);
        }
      });
    }

    protected void doApplyText(String text, Project project) {
      setDocumentText(myDocument, text, DiffBundle.message("save.merge.result.command.name"), project);

      FileDocumentManager.getInstance().saveDocument(myDocument);
      final VirtualFile file = getFile();
      if (file != null) {
        if (ProjectUtil.isProjectOrWorkspaceFile(file)) {
          ProjectManagerEx.getInstanceEx().saveChangedProjectFile(file, project);
        }
      }
    }

    @Override
    public void restoreOriginalContent(final Project project) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          doRestoreOriginalContent(project);
        }
      });
    }

    protected void doRestoreOriginalContent(Project project) {
      setDocumentText(myDocument, myTextBeforeMerge, "", project);
    }

    public VirtualFile getFile() {
      return FileDocumentManager.getInstance().getFile(myDocument);
    }

    public byte[] getBytes() throws IOException {
      VirtualFile file = getFile();
      if (file != null) return file.contentsToByteArray();
      return myDocument.getText().getBytes();
    }

    public FileType getContentType() {
      VirtualFile file = getFile();
      if (file == null) return FileTypes.PLAIN_TEXT;
      return FileTypeManager.getInstance().getFileTypeByFile(file);
    }

    private static void setDocumentText(final Document document, final String text, String name, Project project) {
      CommandProcessor.getInstance().executeCommand(project, new Runnable() {
        public void run() {
          document.replaceString(0, document.getTextLength(), text);
        }
      }, name, null);
    }
  }
}
