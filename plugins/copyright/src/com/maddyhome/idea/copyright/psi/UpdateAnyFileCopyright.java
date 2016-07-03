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

package com.maddyhome.idea.copyright.psi;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.TreeTraversal;
import com.maddyhome.idea.copyright.CopyrightProfile;

import java.util.List;

public class UpdateAnyFileCopyright extends UpdatePsiFileCopyright {
  public UpdateAnyFileCopyright(Project project, Module module, VirtualFile root, CopyrightProfile options) {
    super(project, module, root, options);
  }

  protected void scanFile() {
    List<PsiComment> comments = SyntaxTraverser.psiTraverser(getFile())
      .withTraversal(TreeTraversal.LEAVES_DFS)
      .traverse()
      .takeWhile(Conditions.instanceOf(PsiComment.class, PsiWhiteSpace.class))
      .filter(PsiComment.class)
      .toList();
    checkComments(ContainerUtil.getLastItem(comments), true, comments);
  }

  public static class Provider extends UpdateCopyrightsProvider {
    @Override
    public UpdateCopyright createInstance(Project project, Module module, VirtualFile file, FileType base, CopyrightProfile options) {
      return new UpdateAnyFileCopyright(project, module, file, options);
    }
  }
}
