/**
 * (c) 2006 Carp Technologies BV
 * Brouwerijstraat 1, 7523XC Enschede
 * Created: Jun 7, 2006
 */
package com.siyeh.ipp.imports;

import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * @author <A href="bas@carp-technologies.nl">Bas Leijdekkers</a>
 */
public class ReplaceOnDemandImportIntention extends Intention {

    @NotNull
    protected PsiElementPredicate getElementPredicate() {
        return new OnDemandImportPredicate();
    }

    protected void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException {
        final PsiImportStatementBase importStatementBase = (PsiImportStatementBase)element;
        if (importStatementBase instanceof PsiImportStatement) {
            final PsiImportStatement importStatement = (PsiImportStatement)importStatementBase;
            final PsiJavaFile javaFile = (PsiJavaFile)importStatement.getContainingFile();
            final PsiClass[] classes = javaFile.getClasses();
            final String qualifiedName = importStatement.getQualifiedName();
            final ClassCollector visitor = new ClassCollector(qualifiedName);
            for (PsiClass aClass : classes) {
                aClass.accept(visitor);
            }
            final PsiClass[] importedClasses = visitor.getImportedClasses();
            final PsiManager manager = importStatement.getManager();
            final PsiElementFactory factory = manager.getElementFactory();
            final PsiElement importList = importStatement.getParent();
            importStatement.delete();
            for (PsiClass importedClass : importedClasses) {
                final PsiImportStatement newImportStatement =
                        factory.createImportStatement(importedClass);
                importList.add(newImportStatement);
            }
        } else if (importStatementBase instanceof PsiImportStaticStatement) {
            // do something else
        }
    }

    private static class ClassCollector extends PsiRecursiveElementVisitor {

        private final String importedPackageName;
        private final Set<PsiClass> importedClasses = new HashSet();

        ClassCollector(String importedPackageName) {
            this.importedPackageName = importedPackageName;
        }

        public void visitTypeElement(PsiTypeElement typeElement) {
            super.visitTypeElement(typeElement);
            final PsiType type = typeElement.getType();
            final PsiType deepType = type.getDeepComponentType();
            if (!(deepType instanceof PsiClassType)) {
                return;
            }
            final PsiClassType classType = (PsiClassType)deepType;
            final String canonicalText = classType.getCanonicalText();
            if (!canonicalText.startsWith(importedPackageName)) {
                return;
            }
            final PsiClass aClass = classType.resolve();
            if (aClass == null) {
                return;
            }
            final String qualifiedName = aClass.getQualifiedName();
            final String packageName = ClassUtil.extractPackageName(qualifiedName);
            if (!importedPackageName.equals(packageName)) {
                return;
            }
            importedClasses.add(aClass);
        }


        public void visitReferenceExpression(PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            final PsiElement parent = expression.getParent();
            if (!(parent instanceof PsiReferenceExpression)) {
                return;
            }
            if (expression.isQualified()) {
                return;
            }
            final PsiElement element = expression.resolve();
            if (!(element instanceof PsiClass)) {
                return;
            }
            final PsiClass aClass = (PsiClass)element;
            final String qualifiedName = aClass.getQualifiedName();
            final String packageName = ClassUtil.extractPackageName(qualifiedName);
            if (!importedPackageName.equals(packageName)) {
                return;
            }
            importedClasses.add(aClass);
        }

        public PsiClass[] getImportedClasses() {
            return importedClasses.toArray(new PsiClass[importedClasses.size()]);
        }
    }
}