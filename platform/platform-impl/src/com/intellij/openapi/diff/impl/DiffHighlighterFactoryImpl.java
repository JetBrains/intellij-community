// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl;

import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;

public class DiffHighlighterFactoryImpl implements DiffHighlighterFactory {
  private final Project myProject;
  private final FileType myFileType;
  private final VirtualFile myFile;

  public DiffHighlighterFactoryImpl(FileType fileType, VirtualFile file, Project project) {
    myFileType = fileType;
    myProject = project;
    myFile = file;
  }

  @Override
  public EditorHighlighter createHighlighter() {
    if (myFileType == null || myProject == null) return null;
    if ((myFile != null && myFile.getFileType() == myFileType) || myFile instanceof LightVirtualFile) {
      return EditorHighlighterFactory.getInstance().createEditorHighlighter(myProject, myFile);
    }
    return EditorHighlighterFactory.getInstance().createEditorHighlighter(myProject, myFileType);
  }
}
