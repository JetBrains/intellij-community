// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.maddyhome.idea.copyright.psi;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.maddyhome.idea.copyright.CopyrightProfile;

public class UpdateJavaScriptFileCopyright extends UpdatePsiFileCopyright
{
    public UpdateJavaScriptFileCopyright(Project project, Module module, VirtualFile root, CopyrightProfile options)
    {
        super(project, module, root, options);
    }

    @Override
    protected void scanFile()
    {
        PsiElement first = getFile().getFirstChild();
        if (first != null) {
          final PsiElement child = first.getFirstChild();
          if (child instanceof PsiComment) {
            first = child;
          }
        }
        PsiElement last = first;
        PsiElement next = first;
        while (next != null)
        {
            if (next instanceof PsiComment || next instanceof PsiWhiteSpace)
            {
                next = getNextSibling(next);
            }
            else
            {
                break;
            }
            last = next;
        }

        if (first != null)
        {
            checkComments(first, last, true);
        }
        else
        {
            checkComments(null, null, true);
        }
    }
}
