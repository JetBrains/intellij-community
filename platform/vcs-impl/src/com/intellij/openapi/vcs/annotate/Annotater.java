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
package com.intellij.openapi.vcs.annotate;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.actions.AnnotateToggleAction;
import com.intellij.openapi.vfs.VirtualFile;

public class Annotater {

  private final Project myProject;
  private final VirtualFile myVirtualFile;
  private final FileAnnotation myFileAnnotation;

  public Annotater(FileAnnotation fileAnnotation, Project project, VirtualFile virtualFile) {
    myFileAnnotation = fileAnnotation;
    myProject = project;
    myVirtualFile = virtualFile;
  }

  public void showAnnotation() {
    OpenFileDescriptor openFileDescriptor = new OpenFileDescriptor(myProject, myVirtualFile);
    Editor editor = FileEditorManager.getInstance(myProject).openTextEditor(openFileDescriptor, true);
    if (editor == null) {
      Messages.showMessageDialog(VcsBundle.message("message.text.cannot.open.editor", myVirtualFile.getPresentableUrl()),
                                 VcsBundle.message("message.title.cannot.open.editor"), Messages.getInformationIcon());
      return;
    }

    AnnotateToggleAction.doAnnotate(editor, myProject, myVirtualFile, myFileAnnotation);
  }

}
