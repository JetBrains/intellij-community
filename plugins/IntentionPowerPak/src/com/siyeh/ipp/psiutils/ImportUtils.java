/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import org.jetbrains.annotations.NotNull;

public class ImportUtils{

    private ImportUtils(){
        super();
    }

    public static boolean nameCanBeImported(@NotNull String fqName,
                                            @NotNull PsiJavaFile file){
        if(hasExactImportConflict(fqName, file)){
            return false;
        }
        if(containsConflictingClass(fqName, file)){
            return false;
        }
        return !containsConflictingClassName(fqName, file);
    }

    private static boolean containsConflictingClassName(String fqName,
                                                        PsiJavaFile file){
        final int lastDotIndex = fqName.lastIndexOf((int) '.');
        final String shortName = fqName.substring(lastDotIndex + 1);
        final PsiClass[] classes = file.getClasses();
        for(PsiClass aClass : classes){
            if(shortName.equals(aClass.getName())){
                return true;
            }
        }
        return false;
    }

    private static boolean hasExactImportConflict(String fqName,
                                                  PsiJavaFile file){
        final PsiImportList imports = file.getImportList();
        if(imports == null){
            return false;
        }
        final PsiImportStatement[] importStatements = imports
                .getImportStatements();
        final int lastDotIndex = fqName.lastIndexOf((int) '.');
        final String shortName = fqName.substring(lastDotIndex + 1);
        final String dottedShortName = '.' + shortName;
        for(final PsiImportStatement importStatement : importStatements){
            if(!importStatement.isOnDemand()){
                final String importName = importStatement.getQualifiedName();
                if (importName ==  null){
                    return false;
                }
                if(!importName.equals(fqName)){
                    if(importName.endsWith(dottedShortName)){
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean hasOnDemandImportConflict(@NotNull String fqName,
                                                    @NotNull PsiJavaFile file){
        final PsiImportList imports = file.getImportList();
        if(imports == null){
            return false;
        }
        final PsiImportStatement[] importStatements =
                imports.getImportStatements();
        final int lastDotIndex = fqName.lastIndexOf((int) '.');
        final String shortName = fqName.substring(lastDotIndex + 1);
        final String packageName = ClassUtil.extractPackageName(fqName);
        for(final PsiImportStatement importStatement : importStatements){
            if(importStatement.isOnDemand()){
                final PsiJavaCodeReferenceElement importReference =
                        importStatement.getImportReference();
                if(importReference == null){
                    continue;
                }
                final String packageText = importReference.getText();
                if(packageText.equals(packageName)){
                    continue;
                }
                final PsiElement element = importReference.resolve();
                if(element != null && element instanceof PsiPackage){
                    final PsiPackage aPackage = (PsiPackage) element;
                    final PsiClass[] classes = aPackage.getClasses();
                    for(final PsiClass aClass : classes){
                        final String className = aClass.getName();
                        if(shortName.equals(className)){
                            return true;
                        }
                    }
                }
            }
        }
        if (hasDefaultImportConflict(fqName, file)) {
            return true;
        }
        return hasJavaLangImportConflict(fqName, file);
    }

    public static boolean hasDefaultImportConflict(String fqName,
                                                   PsiJavaFile file) {
        final String shortName = ClassUtil.extractClassName(fqName);
        final String packageName = ClassUtil.extractPackageName(fqName);
        final PsiManager manager = file.getManager();
        final String filePackageName = file.getPackageName();
        if(!filePackageName.equals(packageName)){
            final PsiPackage filePackage = manager.findPackage(filePackageName);
            if(filePackage != null){
                final PsiClass[] classes = filePackage.getClasses();
                for (PsiClass aClass : classes) {
                    final String className = aClass.getName();
                    if(shortName.equals(className)){
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean hasJavaLangImportConflict(String fqName,
                                                    PsiJavaFile file) {
        final PsiManager manager = file.getManager();
        final String shortName = ClassUtil.extractClassName(fqName);
        final String packageName = ClassUtil.extractPackageName(fqName);
        if(!"java.lang".equals(packageName)){
            final PsiPackage javaLangPackage =
                    manager.findPackage("java.lang");
            if(javaLangPackage == null){
                return false;
            }
            final PsiClass[] classes = javaLangPackage.getClasses();
            for(final PsiClass aClass : classes){
                final String className = aClass.getName();
                if(shortName.equals(className)){
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsConflictingClass(String fqName,
                                                    PsiJavaFile file){
        final PsiClass[] classes = file.getClasses();
        for(PsiClass aClass : classes){
            if (containsConflictingInnerClass(fqName, aClass)) {
                return true;
            }
        }
        final ClassReferenceVisitor visitor =
                new ClassReferenceVisitor(fqName);
        file.accept(visitor);
        return visitor.isReferenceFound();
    }

    /**
     * ImportUtils currently checks all inner classes, even those that are
     * contained in inner classes themselves, because it doesn't know the
     * location of the original fully qualified reference. It should really only
     * check if the containing class of the fully qualified reference has any
     * conflicting inner classes.
     */
    private static boolean containsConflictingInnerClass(String fqName,
                                                         PsiClass aClass){
        final String shortName = ClassUtil.extractClassName(fqName);
        if(shortName.equals(aClass.getName())){
            if(!fqName.equals(aClass.getQualifiedName())){
                return true;
            }
        }
        final PsiClass[] classes = aClass.getInnerClasses();
        for (PsiClass innerClass : classes) {
            if (containsConflictingInnerClass(fqName, innerClass)) {
                return true;
            }
        }
        return false;
    }

    public static boolean importStatementMatches(
            PsiImportStatement importStatement, String name){
        final String qualifiedName = importStatement.getQualifiedName();

        if(importStatement.isOnDemand()){
            final int lastDotIndex = name.lastIndexOf((int) '.');
            final String packageName = name.substring(0, lastDotIndex);
            return packageName.equals(qualifiedName);
        } else{
            return name.equals(qualifiedName);
        }
    }

    private static class ClassReferenceVisitor
            extends PsiRecursiveElementVisitor{

        private final String m_name;
        private final String fullyQualifiedName;
        private boolean m_referenceFound = false;

        private ClassReferenceVisitor(String fullyQualifiedName) {
            super();
            m_name = ClassUtil.extractClassName(fullyQualifiedName);
            this.fullyQualifiedName = fullyQualifiedName;
        }

        public void visitReferenceExpression(
                PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            if (m_referenceFound) {
                return;
            }
            final String text = expression.getText();
            if (text.indexOf((int)'.') >= 0 || !m_name.equals(text)) {
                return;
            }
            final PsiElement element = expression.resolve();
            if (!(element instanceof PsiClass)
                    || element instanceof PsiTypeParameter) {
                return;
            }
            final PsiClass aClass = (PsiClass) element;
            final String testClassName = aClass.getName();
            final String testClassQualifiedName = aClass.getQualifiedName();
            if (testClassQualifiedName == null || testClassName == null) {
                return;
            }
            if (testClassQualifiedName.equals(fullyQualifiedName) ||
                    !testClassName.equals(m_name)) {
                return;
            }
            m_referenceFound = true;
        }

        public boolean isReferenceFound(){
            return m_referenceFound;
        }
    }
}