/**
 * (c) 2004 Carp Technologies BV
 * Hengelosestraat 705, 7521PA Enschede
 * Created: Feb 7, 2006, 2:34:01 AM
 */
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * @author <A href="bas@carp-technologies.nl">Bas Leijdekkers</a>
 */
public class SynchronizationUtil {

    private SynchronizationUtil() {
    }

    public static boolean isInSynchronizedContext(PsiElement element) {
        final PsiElement context =
                PsiTreeUtil.getParentOfType(element, PsiMethod.class,
                        PsiSynchronizedStatement.class);
        if (context instanceof PsiSynchronizedStatement) {
            return true;
        }
        if (context != null) {
            final PsiModifierListOwner modifierListOwner =
                    (PsiModifierListOwner)context;
            if (modifierListOwner.hasModifierProperty(
                    PsiModifier.SYNCHRONIZED)) {
                return true;
            }
        }
        return false;
    }
}