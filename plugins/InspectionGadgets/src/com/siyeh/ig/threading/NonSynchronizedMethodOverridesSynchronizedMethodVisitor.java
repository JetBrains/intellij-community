/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 20.09.2006
 * Time: 22:17:07
 */
package com.siyeh.ig.threading;

import com.siyeh.ig.BaseInspectionVisitor;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;

class NonSynchronizedMethodOverridesSynchronizedMethodVisitor extends BaseInspectionVisitor {

    public void visitMethod(@NotNull PsiMethod method) {
        //no call to super, so we don't drill into anonymous classes
        if (method.isConstructor()) {
            return;
        }
        if (method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
            return;
        }
        if (method.getNameIdentifier() == null) {
            return;
        }
        final PsiMethod[] superMethods = method.findSuperMethods();
        for (final PsiMethod superMethod : superMethods) {
            if (superMethod.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
                registerMethodError(method);
                return;
            }
        }
    }
}