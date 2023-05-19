// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.copyright;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.TreeTraversal;
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.psi.UpdatePsiFileCopyright;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.KotlinFileType;
import org.jetbrains.kotlin.kdoc.psi.api.KDoc;
import org.jetbrains.kotlin.psi.KtDeclaration;

import java.util.List;

public class UpdateKotlinCopyright extends UpdatePsiFileCopyright {
    UpdateKotlinCopyright(Project project, Module module, VirtualFile root, CopyrightProfile copyrightProfile) {
        super(project, module, root, copyrightProfile);
    }

    @Override
    protected boolean accept() {
        return getFile().getFileType() == KotlinFileType.INSTANCE;
    }

    @Override
    protected void scanFile() {
        List<PsiComment> comments = getExistentComments(getFile());
        checkComments(ContainerUtil.getLastItem(comments), true, comments);
    }

    @NotNull
    public static List<PsiComment> getExistentComments(@NotNull PsiFile psiFile) {
        return SyntaxTraverser.psiTraverser(psiFile)
                .withTraversal(TreeTraversal.LEAVES_DFS)
                .traverse()
                .takeWhile(
                        element ->
                                (element instanceof PsiComment && !(element.getParent() instanceof KtDeclaration)) ||
                                element instanceof PsiWhiteSpace ||
                                element.getText().isEmpty() ||
                                element.getParent() instanceof KDoc
                )
                .map(e -> e.getParent() instanceof KDoc ? e.getParent() : e)
                .filter(PsiComment.class)
                .toList();
    }
}
