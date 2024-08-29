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
package com.intellij.ide.highlighter

import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewBuilderProvider
import com.intellij.ide.structureView.impl.StructureViewComposite
import com.intellij.ide.structureView.logical.impl.LogicalStructureViewService
import com.intellij.lang.LanguageStructureViewBuilder
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class LanguageFileTypeStructureViewBuilderProvider : StructureViewBuilderProvider {
  override fun getStructureViewBuilder(fileType: FileType, file: VirtualFile, project: Project): StructureViewBuilder? {
    if (fileType !is LanguageFileType) return null

    val psiFile = PsiManager.getInstance(project).findFile(file) ?: return null

    val physicalBuilder = LanguageStructureViewBuilder.getInstance().forLanguage(psiFile.language)?.getStructureViewBuilder(psiFile) ?: return null
    val logicalViewBuilder = LogicalStructureViewService.getInstance(project).getLogicalStructureBuilder(psiFile) ?: return physicalBuilder

    return StructureViewBuilder { fileEditor, _ ->
      StructureViewComposite(
        StructureViewComposite.StructureViewDescriptor("Logical", logicalViewBuilder.createStructureView(fileEditor, project), null),
        StructureViewComposite.StructureViewDescriptor("Physical", physicalBuilder.createStructureView(fileEditor, project), null)
      )
    }
  }
}