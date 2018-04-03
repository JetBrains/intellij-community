/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TestDataHighlightingPassFactory implements ProjectComponent,  TextEditorHighlightingPassFactory {
  public static final List<String> SUPPORTED_FILE_TYPES = Collections.singletonList(StdFileTypes.JAVA.getDefaultExtension());
  public static final List<String> SUPPORTED_IN_TEST_DATA_FILE_TYPES = Arrays.asList(
    "js", "php", "css", "html", "xhtml", "jsp", "test", "py", "aj"
  );
  private static final int MAX_HOPES = 3;
  private static final String TEST_DATA = "testdata";
  private final Project myProject;


  public TestDataHighlightingPassFactory(Project project, TextEditorHighlightingPassRegistrar highlightingPassRegistrar) {
    myProject = project;
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this, null, null, true, -1);
  }
  
  @Override
  @Nullable
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull final Editor editor) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null && isSupported(virtualFile)) {
      return new TestDataHighlightingPass(myProject, PsiDocumentManager.getInstance(myProject).getDocument(file));
    }
    return null;
  }

  public boolean isSupported(@NotNull VirtualFile file) {
    if (ScratchUtil.isScratch(file)) {
      return false;
    }
    final String ext = file.getExtension();
    if (SUPPORTED_FILE_TYPES.contains(ext)) {
      return ProjectRootManager.getInstance(myProject).getFileIndex().getSourceRootForFile(file) == null;
    }

    if (SUPPORTED_IN_TEST_DATA_FILE_TYPES.contains(ext)) {
      int i = 0;
      VirtualFile parent = file.getParent();
      while (parent != null && i < MAX_HOPES) {
        if (parent.getName().toLowerCase().contains(TEST_DATA)) {
          return true;
        }
        i++;
        parent = parent.getParent();
      }
    }
    return false;
  }
}
