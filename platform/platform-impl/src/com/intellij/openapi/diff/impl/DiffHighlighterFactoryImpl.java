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
package com.intellij.openapi.diff.impl;

import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;

public class DiffHighlighterFactoryImpl implements DiffHighlighterFactory {
  private final Project myProject;
  private final FileType myFileType;

  public DiffHighlighterFactoryImpl(FileType fileType, Project project) {
    myFileType = fileType;
    myProject = project;
  }

  public EditorHighlighter createHighlighter() {
    return (myFileType == null || myProject == null) ?
        null : EditorHighlighterFactory.getInstance().createEditorHighlighter(myProject, myFileType);
  }
}
