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
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.siyeh.HardcodedMethodConstants;

public class ImportUtils{

    private ImportUtils(){
        super();
    }

    // doesn't work for the following case:
    // class UnnecessaryFullQualifiedNameInspection {
    //     java.util.Vector v;
    //     class Vector {}
    // }
    public static boolean nameCanBeImported(String fqName, PsiJavaFile file){
        //if(hasExactImportMatch(fqName, file)){
        //    return true;
        //}
        if(hasExactImportConflict(fqName, file)){
            return false;
        }
        //if(hasOnDemandImportConflict(fqName, file)){
        //    return false;
        //}
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

    private static boolean hasExactImportMatch(String fqName, PsiJavaFile file){
        final PsiImportList imports = file.getImportList();
        if(imports == null){
            return false;
        }
        final PsiImportStatement[] importStatements =
                imports.getImportStatements();
        for(final PsiImportStatement importStatement : importStatements){
            if(!importStatement.isOnDemand()){
                final String importName = importStatement.getQualifiedName();
                if(fqName.equals(importName)){
                    return true;
                }
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

    public static boolean hasOnDemandImportConflict(String fqName,
                                                    PsiJavaFile file){
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
        return hasDefaultImportConflict(fqName, file);
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
        if(!HardcodedMethodConstants.JAVA_LANG.equals(packageName)){
            final PsiPackage javaLangPackage =
                    manager.findPackage(HardcodedMethodConstants.JAVA_LANG);
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
            if (containsConflictingClass(fqName, aClass)) {
                return true;
            }
        }
        //return false;
        final int lastDotIndex = fqName.lastIndexOf((int) '.');
        final String shortName = fqName.substring(lastDotIndex + 1);
        final ClassReferenceVisitor visitor =
                new ClassReferenceVisitor(shortName, fqName);
        file.accept(visitor);
        return visitor.isReferenceFound();
    }

    private static boolean containsConflictingClass(String fqName,
                                                    PsiClass aClass){
        final String shortName = ClassUtil.extractClassName(fqName);
        if(shortName.equals(aClass.getName())){
                if(!fqName.equals(aClass.getQualifiedName())){
                    return true;
                }
        }
        final PsiClass[] classes = aClass.getInnerClasses();
        for (PsiClass innerClass : classes) {
            if (containsConflictingClass(fqName, innerClass)) {
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

        private ClassReferenceVisitor(String className,
                                      String fullyQualifiedName){
            super();
            m_name = className;
            this.fullyQualifiedName = fullyQualifiedName;
        }

        public void visitReferenceElement(PsiJavaCodeReferenceElement ref){
            final String text = ref.getText();
            if(text.indexOf((int) '.') >= 0){
                return;
            }
            final PsiElement element = ref.resolve();

            if(element instanceof PsiClass
               && !(element instanceof PsiTypeParameter)){
                final PsiClass aClass = (PsiClass) element;
                final String testClassName = aClass.getName();
                final String testClassQualifiedName = aClass.getQualifiedName();
                if(testClassQualifiedName != null && testClassName != null
                   && !testClassQualifiedName.equals(fullyQualifiedName) &&
                   testClassName.equals(m_name)){
                    m_referenceFound = true;
                }
            }
        }

        public boolean isReferenceFound(){
            return m_referenceFound;
        }
    }
}
