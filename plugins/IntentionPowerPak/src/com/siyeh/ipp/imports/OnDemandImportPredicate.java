/**
 * (c) 2006 Carp Technologies BV
 * Brouwerijstraat 1, 7523XC Enschede
 * Created: Jun 7, 2006
 */
package com.siyeh.ipp.imports;

import com.intellij.psi.*;
import com.siyeh.ipp.base.PsiElementPredicate;

/**
 * @author <A href="bas@carp-technologies.nl">Bas Leijdekkers</a>
 */
public class OnDemandImportPredicate implements PsiElementPredicate {

    public boolean satisfiedBy(PsiElement element) {
        if (!(element instanceof PsiImportStatement)) { // doesn't work for import static yet.
            return false;
        }
        final PsiImportStatementBase importStatementBase = (PsiImportStatementBase)element;
        if (!importStatementBase.isOnDemand()) {
            return false;
        }
        final PsiFile file = importStatementBase.getContainingFile();
        return file instanceof PsiJavaFile;
    }
}