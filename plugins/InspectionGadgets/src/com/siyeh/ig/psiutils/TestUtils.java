package com.siyeh.ig.psiutils;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

public class TestUtils {
    private TestUtils() {
        super();
    }

    public static boolean isTest(PsiClass aClass) {
        final PsiManager manager = aClass.getManager();
        final PsiFile file = (PsiFile) PsiTreeUtil.getParentOfType(aClass,
                PsiFile.class);
        final VirtualFile virtualFile = file.getVirtualFile();
        final Project project = manager.getProject();
        return TestUtils.isTest(project, virtualFile);
    }

    public static boolean isTest(PsiDirectory directory) {
        final PsiManager manager = directory.getManager();
        final VirtualFile virtualFile = directory.getVirtualFile();
        final Project project = manager.getProject();
        return TestUtils.isTest(project, virtualFile);
    }

    public static boolean isTest(Project project, VirtualFile virtualFile) {
        if (virtualFile == null) {
            return false;
        }
        final ProjectRootManager rootManager = ProjectRootManager.getInstance(project);
        final ProjectFileIndex fileIndex = rootManager.getFileIndex();
        return fileIndex.isInTestSourceContent(virtualFile);
    }

    public static boolean isTest(PsiJavaFile file) {
        final PsiManager manager = file.getManager();
        final VirtualFile virtualFile = file.getVirtualFile();
        final Project project = manager.getProject();
        return TestUtils.isTest(project, virtualFile);
    }
}
