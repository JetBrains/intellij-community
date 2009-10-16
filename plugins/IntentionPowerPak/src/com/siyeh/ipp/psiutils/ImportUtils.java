/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.openapi.project.Project;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ImportUtils{

    private ImportUtils(){
    }

    public static boolean nameCanBeImported(@NotNull String fqName,
                                            @NotNull PsiElement context){
        final PsiClass containingClass = PsiTreeUtil.getParentOfType(context, PsiClass.class);
        if (containingClass != null) {
            if (fqName.equals(containingClass.getQualifiedName())) {
                return true;
            }
            final String shortName = ClassUtil.extractClassName(fqName);
            final PsiClass[] innerClasses = containingClass.getAllInnerClasses();
            for (PsiClass innerClass : innerClasses) {
                if (innerClass.hasModifierProperty(PsiModifier.PRIVATE)) {
                    continue;
                }
                if (innerClass.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
                    if (!inSamePackage(innerClass, containingClass)) {
                        continue;
                    }
                }
                final String className = innerClass.getName();
                if (shortName.equals(className)) {
                    return false;
                }
            }
        }
        final PsiJavaFile file =
                PsiTreeUtil.getParentOfType(context, PsiJavaFile.class);
        if (file == null) {
            return false;
        }
        if(hasExactImportConflict(fqName, file)){
            return false;
        }
        if(hasOnDemandImportConflict(fqName, file, true)){
            return false;
        }
        if(containsConflictingClass(fqName, file)){
            return false;
        }
        return !containsConflictingClassName(fqName, file);
    }

    public static boolean inSamePackage(@Nullable PsiElement element1,
                                        @Nullable PsiElement element2) {
        if (element1 == null || element2==null) {
            return false;
        }
        final PsiFile containingFile1 = element1.getContainingFile();
        if (!(containingFile1 instanceof PsiClassOwner)) {
            return false;
        }
        final PsiClassOwner containingJavaFile1 =
                (PsiClassOwner)containingFile1;
        final String packageName1 = containingJavaFile1.getPackageName();
        final PsiFile containingFile2 = element2.getContainingFile();
        if (!(containingFile2 instanceof PsiClassOwner)) {
            return false;
        }
        final PsiClassOwner containingJavaFile2 =
                (PsiClassOwner)containingFile2;
        final String packageName2 = containingJavaFile2.getPackageName();
        return packageName1.equals(packageName2);
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
        return hasOnDemandImportConflict(fqName, file, false);
    }

    /**
     * @param strict  if strict is true this method checks if the conflicting
     * class which is imported is actually used in the file. If it isn't the
     * on demand import can be overridden with an exact import for the fqName
     * without breaking stuff.
     */
    private static boolean hasOnDemandImportConflict(@NotNull String fqName,
                                                     @NotNull PsiJavaFile file,
                                                     boolean strict) {
        final PsiImportList imports = file.getImportList();
        if(imports == null){
            return false;
        }
        final PsiImportStatement[] importStatements =
                imports.getImportStatements();
        final String shortName = ClassUtil.extractClassName(fqName);
        final String packageName = ClassUtil.extractPackageName(fqName);
        for(final PsiImportStatement importStatement : importStatements){
            if (!importStatement.isOnDemand()) {
                continue;
            }
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
            if (element == null || !(element instanceof PsiPackage)) {
                continue;
            }
            final PsiPackage aPackage = (PsiPackage) element;
            final PsiClass[] classes = aPackage.getClasses();
            for(final PsiClass aClass : classes){
                final String className = aClass.getName();
                if (!shortName.equals(className)) {
                    continue;
                }
                if (!strict) {
                    return true;
                }
                final String qualifiedClassName = aClass.getQualifiedName();
                final ClassReferenceVisitor visitor =
                        new ClassReferenceVisitor(qualifiedClassName);
                file.accept(visitor);
                return visitor.isReferenceFound();
            }
        }
        return hasJavaLangImportConflict(fqName, file);
    }

    public static boolean hasDefaultImportConflict(String fqName,
                                                   PsiJavaFile file) {
        final String shortName = ClassUtil.extractClassName(fqName);
        final String packageName = ClassUtil.extractPackageName(fqName);
        final String filePackageName = file.getPackageName();
        if (filePackageName.equals(packageName)) {
            return false;
        }
        final Project project = file.getProject();
        final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        final PsiPackage filePackage =
                psiFacade.findPackage(filePackageName);
        if (filePackage == null) {
            return false;
        }
        final PsiClass[] classes = filePackage.getClasses();
        for (PsiClass aClass : classes) {
            final String className = aClass.getName();
            if(shortName.equals(className)){
                return true;
            }
        }
        return false;
    }

    public static boolean hasJavaLangImportConflict(String fqName,
                                                    PsiJavaFile file) {
        final String shortName = ClassUtil.extractClassName(fqName);
        final String packageName = ClassUtil.extractPackageName(fqName);
        if ("java.lang".equals(packageName)) {
            return false;
        }
        final Project project = file.getProject();
        final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        final PsiPackage javaLangPackage = psiFacade.findPackage("java.lang");
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
        //return false;
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

    public static void addStaticImport(PsiElement context, String qualifierClass, String memberName)
            throws IncorrectOperationException {
        final PsiFile psiFile = context.getContainingFile();
        if (!(psiFile instanceof PsiJavaFile)) {
            return;
        }
        final Project project = context.getProject();
        final GlobalSearchScope scope = context.getResolveScope();
        final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        final PsiClass aClass = psiFacade.findClass(qualifierClass, scope);
        if (aClass == null) {
            return;
        }
        final PsiJavaFile javaFile = (PsiJavaFile)psiFile;
        final PsiImportList importList = javaFile.getImportList();
        if (importList == null) {
            return;
        }
        final String qualifiedName  = aClass.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }
        final List<PsiJavaCodeReferenceElement> imports =
                getImportsFromClass(importList, qualifiedName);
        final CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(project);
        final PsiElementFactory elementFactory = psiFacade.getElementFactory();
        if (imports.size() < codeStyleSettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND) {
            importList.add(elementFactory.createImportStaticStatement(aClass, memberName));
        } else {
            for (PsiJavaCodeReferenceElement ref : imports) {
                final PsiImportStaticStatement importStatement =
                        PsiTreeUtil.getParentOfType(ref, PsiImportStaticStatement.class);
                if (importStatement != null) {
                    importStatement.delete();
                }
            }
            importList.add(elementFactory.createImportStaticStatement(aClass, "*"));
        }
    }

    private static List<PsiJavaCodeReferenceElement> getImportsFromClass(
            @NotNull PsiImportList importList, @NotNull String className){
        final List<PsiJavaCodeReferenceElement> imports =
                new ArrayList<PsiJavaCodeReferenceElement>();
        for (PsiImportStaticStatement staticStatement : importList.getImportStaticStatements()) {
            final PsiClass psiClass = staticStatement.resolveTargetClass();
            if (psiClass == null) {
                continue;
            }
            if (!className.equals(psiClass.getQualifiedName())) {
                continue;
            }
            imports.add(staticStatement.getImportReference());
        }
        return imports;
    }

    private static class ClassReferenceVisitor
            extends JavaRecursiveElementVisitor{

        private final String m_name;
        private final String fullyQualifiedName;
        private boolean m_referenceFound = false;

        private ClassReferenceVisitor(String fullyQualifiedName) {
            super();
            m_name = ClassUtil.extractClassName(fullyQualifiedName);
            this.fullyQualifiedName = fullyQualifiedName;
        }

        @Override public void visitReferenceElement(
                PsiJavaCodeReferenceElement reference) {
            super.visitReferenceElement(reference);
            if (m_referenceFound) {
                return;
            }
            final String text = reference.getText();
            if (text.indexOf((int)'.') >= 0 || !m_name.equals(text)) {
                return;
            }
            final PsiElement element = reference.resolve();
            if (!(element instanceof PsiClass)
                || element instanceof PsiTypeParameter) {
                return;
            }
            final PsiClass aClass = (PsiClass) element;
            final String testClassName = aClass.getName();
            final String testClassQualifiedName = aClass.getQualifiedName();
            if (testClassQualifiedName == null || testClassName == null
                || testClassQualifiedName.equals(fullyQualifiedName) ||
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