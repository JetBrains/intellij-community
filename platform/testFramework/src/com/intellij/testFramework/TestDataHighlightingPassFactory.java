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
package com.intellij.testFramework;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class TestDataHighlightingPassFactory extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {
  public static final List<String> SUPPORTED_FILE_TYPES = Arrays.asList(
    StdFileTypes.JAVA.getDefaultExtension()
  );

  public TestDataHighlightingPassFactory(Project project, TextEditorHighlightingPassRegistrar highlightingPassRegistrar) {
    super(project);
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this, null, null, true, -1);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return getClass().getName();
  }

  @Nullable
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull final Editor editor) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null) {      
      if (SUPPORTED_FILE_TYPES.contains(virtualFile.getExtension())) {
        if (ProjectRootManager.getInstance(myProject).getFileIndex().getSourceRootForFile(virtualFile) == null) {
          return new TestDataHighlightingPass(myProject, PsiDocumentManager.getInstance(myProject).getDocument(file));          
        }
      }
    }
    return null;
  }
}

