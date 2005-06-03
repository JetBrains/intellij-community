package com.siyeh.ig.psiutils;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class TestUtils{
    private TestUtils(){
        super();
    }

    public static boolean isTest(@NotNull PsiClass aClass){
        final PsiManager manager = aClass.getManager();
        final PsiFile file = aClass.getContainingFile();
        final VirtualFile virtualFile = file.getVirtualFile();
        final Project project = manager.getProject();
        final ProjectRootManager rootManager = ProjectRootManager.getInstance(project);
        final ProjectFileIndex fileIndex = rootManager.getFileIndex();
        return fileIndex.isInTestSourceContent(virtualFile);
    }

}
