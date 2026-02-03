// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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

  @Override
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
