package com.siyeh.ig.imports;

import com.intellij.psi.*;

class ImportIsUsedVisitor extends PsiRecursiveElementVisitor {
    private final PsiImportStatement m_import;
    private boolean m_used = false;

    ImportIsUsedVisitor(PsiImportStatement importStatement) {
        super();
        m_import = importStatement;
    }

    public void visitReferenceElement(PsiJavaCodeReferenceElement ref) {
        super.visitReferenceElement(ref);
        followReferenceToImport(ref);
    }

    public void visitReferenceExpression(PsiReferenceExpression ref) {
        super.visitReferenceExpression(ref);
        followReferenceToImport(ref);
    }

    private void followReferenceToImport(PsiJavaCodeReferenceElement ref) {
        final PsiElement element = ref.resolve();
        if (!(element instanceof PsiClass)) {
            return;
        }
        final PsiClass referencedClass = (PsiClass) element;
        if (ref.getQualifier()!=null) {
            return;        //it' already fully qualified, so the import statement wasn't responsible
        }
        final String importName = m_import.getQualifiedName();
        if (importName == null) {
            return;
        }
        final String qualifiedName = referencedClass.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }
        if (m_import.isOnDemand()) {
            final int lastComponentIndex = qualifiedName.lastIndexOf((int) '.');
            if (lastComponentIndex > 0) {
                final String packageName = qualifiedName.substring(0, lastComponentIndex);
                if (importName.equals(packageName)) {
                    m_used = true;
                }
            }
        } else {
            if (importName.equals(qualifiedName)) {
                m_used = true;
            }
        }
    }

    public boolean isUsed() {
        return m_used;
    }
}
