package com.siyeh.ig.imports;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.jsp.JspFile;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.DeleteImportFix;
import org.jetbrains.annotations.NotNull;

public class UnusedImportInspection extends ClassInspection{
    private final DeleteImportFix fix = new DeleteImportFix();

    public String getDisplayName(){
        return "Unused import";
    }

    public String getGroupDisplayName(){
        return GroupNames.IMPORTS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Unused import '#ref' #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    public BaseInspectionVisitor buildVisitor(){
        return new UnusedImportVisitor();
    }

    private static class UnusedImportVisitor extends BaseInspectionVisitor{
        public void visitClass(@NotNull PsiClass aClass){
            if(!(aClass.getParent() instanceof PsiJavaFile)){
                return;
            }
            if(aClass.getContainingFile() instanceof JspFile){
                return;
            }
            final PsiJavaFile file = (PsiJavaFile) aClass.getParent();
            if(file == null){
                return;
            }
            if(!file.getClasses()[0].equals(aClass)){
                return;
            }
            final PsiImportList importList = file.getImportList();
            if(importList == null){
                return;
            }
            final PsiImportStatement[] importStatements =
                    importList.getImportStatements();
            for(final PsiImportStatement importStatement : importStatements){
                if(!isNecessaryImport(importStatement, file.getClasses())){
                    registerError(importStatement);
                }
            }
        }

        private static boolean isNecessaryImport(
                PsiImportStatement importStatement, PsiClass[] classes){
            final ImportIsUsedVisitor visitor = new ImportIsUsedVisitor(
                    importStatement);
            for(PsiClass aClasses : classes){
                aClasses.accept(visitor);
                final PsiClass[] innerClasses = aClasses.getInnerClasses();
                for(PsiClass innerClass : innerClasses){
                    innerClass.accept(visitor);
                }
            }
            return visitor.isUsed();
        }
    }
}
