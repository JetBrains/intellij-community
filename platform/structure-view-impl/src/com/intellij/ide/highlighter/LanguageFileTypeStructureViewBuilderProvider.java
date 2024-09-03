/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.ide.highlighter;

import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewBuilderProvider;
import com.intellij.ide.structureView.impl.StructureViewComposite;
import com.intellij.ide.structureView.logical.impl.LogicalStructureViewService;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class LanguageFileTypeStructureViewBuilderProvider implements StructureViewBuilderProvider {
  @Override
  @Nullable
  public StructureViewBuilder getStructureViewBuilder(@NotNull final FileType fileType, @NotNull final VirtualFile file, @NotNull final Project project) {
    if (!(fileType instanceof LanguageFileType)) return null;

    final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (psiFile == null) return null;

    final PsiStructureViewFactory factory = LanguageStructureViewBuilder.getInstance().forLanguage(psiFile.getLanguage());
    if (factory == null) return null;
    StructureViewBuilder physicalBuilder = factory.getStructureViewBuilder(psiFile);
    if (physicalBuilder == null) return null;
    StructureViewBuilder logicalBuilder = LogicalStructureViewService.Companion.getInstance(project).getLogicalStructureBuilder(psiFile);
    if (logicalBuilder == null) return physicalBuilder;
    return new StructureViewBuilder() {
      @Override
      public @NotNull StructureView createStructureView(@Nullable FileEditor fileEditor, @NotNull Project project) {
        return new StructureViewComposite(
          new StructureViewComposite.StructureViewDescriptor("Logical", logicalBuilder.createStructureView(fileEditor, project), null),
          new StructureViewComposite.StructureViewDescriptor("Physical", physicalBuilder.createStructureView(fileEditor, project), null)
        );
      }
    };
  }
}