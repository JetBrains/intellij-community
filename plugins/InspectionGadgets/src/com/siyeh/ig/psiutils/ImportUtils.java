package com.siyeh.ig.psiutils;

import com.intellij.psi.*;

public class ImportUtils {
    private ImportUtils() {
        super();
    }

    public static boolean nameCanBeImported(String fqName, PsiJavaFile file) {
        if (hasExactImportMatch(fqName, file)) {
            return true;
        }
        if (hasExactImportConflict(fqName, file)) {
            return false;
        }
        if (hasOnDemandImportConflict(fqName, file)) {
            return false;
        }
        if (containsConflictingClassReference(fqName, file)) {
            return false;
        }
        return true;
    }

    private static boolean hasExactImportMatch(String fqName, PsiJavaFile file) {
        final PsiImportList imports = file.getImportList();
        final PsiImportStatement[] importStatements = imports.getImportStatements();
        for (int i = 0; i < importStatements.length; i++) {
            final PsiImportStatement importStatement = importStatements[i];
            if (!importStatement.isOnDemand()) {
                final String importName = importStatement.getQualifiedName();
                if (importName.equals(fqName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasExactImportConflict(String fqName, PsiJavaFile file) {
        final PsiImportList imports = file.getImportList();
        final PsiImportStatement[] importStatements = imports.getImportStatements();
        final int lastDotIndex = fqName.lastIndexOf((int) '.');
        final String shortName = fqName.substring(lastDotIndex + 1);
        final String dottedShortName = '.' + shortName;
        for (int i = 0; i < importStatements.length; i++) {
            final PsiImportStatement importStatement = importStatements[i];
            if (!importStatement.isOnDemand()) {
                final String importName = importStatement.getQualifiedName();

                if (!importName.equals(fqName)) {
                    if (importName.endsWith(dottedShortName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean hasOnDemandImportConflict(String fqName, PsiJavaFile file) {
        final PsiImportList imports = file.getImportList();
        final PsiImportStatement[] importStatements = imports.getImportStatements();
        final int lastDotIndex = fqName.lastIndexOf((int) '.');
        final String shortName = fqName.substring(lastDotIndex + 1);
        final String packageName = fqName.substring(0, lastDotIndex);
        for (int i = 0; i < importStatements.length; i++) {
            final PsiImportStatement importStatement = importStatements[i];
            if (importStatement.isOnDemand()) {
                final PsiJavaCodeReferenceElement ref = importStatement.getImportReference();
                if (ref == null) {
                    continue;
                }
                final String packageText = ref.getText();
                if (packageText.equals(packageName)) {
                    continue;
                }
                final PsiElement element = ref.resolve();
                if (element != null && element instanceof PsiPackage) {
                    final PsiPackage aPackage = (PsiPackage) element;
                    final PsiClass[] classes = aPackage.getClasses();
                    for (int j = 0; j < classes.length; j++) {
                        final PsiClass aClass = classes[j];
                        final String className = aClass.getName();
                        if (shortName.equals(className)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean containsConflictingClassReference(String fqName, PsiJavaFile file) {
        final int lastDotIndex = fqName.lastIndexOf((int) '.');
        final String shortName = fqName.substring(lastDotIndex + 1);
        final PsiClass[] classes = file.getClasses();
        for (int i = 0; i < classes.length; i++) {
            if (shortName.equals(classes[i].getName())) {
                if (!fqName.equals(classes[i].getQualifiedName())) {
                    return true;
                }
            }
        }
        final ClassReferenceVisitor visitor = new ClassReferenceVisitor(shortName, fqName);
        file.accept(visitor);
        return visitor.isReferenceFound();
    }

    public static boolean importStatementMatches(PsiImportStatement importStatement, String name) {
        final String qualifiedName = importStatement.getQualifiedName();

        if (importStatement.isOnDemand()) {
            final int lastDotIndex = name.lastIndexOf((int) '.');
            final String packageName = name.substring(0, lastDotIndex);
            return packageName.equals(qualifiedName);
        } else {
            return name.equals(qualifiedName);
        }
    }

    private static class ClassReferenceVisitor extends PsiRecursiveElementVisitor {
        private final String m_name;
        private final String fullyQualifiedName;
        private boolean m_referenceFound = false;

        private ClassReferenceVisitor(String className, String fullyQualifiedName) {
            super();
            m_name = className;
            this.fullyQualifiedName = fullyQualifiedName;
        }


        public void visitReferenceElement(PsiJavaCodeReferenceElement ref) {
            final String text = ref.getText();
            if (text.indexOf((int) '.') >= 0) {
                return;
            }
            final PsiElement element = ref.resolve();

            if (element instanceof PsiClass && !(element instanceof PsiTypeParameter)) {
                final PsiClass aClass = (PsiClass) element;
                final String testClassName = aClass.getName();
                final String testClassQualifiedName = aClass.getQualifiedName();
                if (testClassQualifiedName != null && testClassName != null && !testClassQualifiedName.equals(fullyQualifiedName) &&
                        testClassName.equals(m_name)) {
                    m_referenceFound = true;
                }
            }
        }

        private boolean isReferenceFound() {
            return m_referenceFound;
        }
    }
}
