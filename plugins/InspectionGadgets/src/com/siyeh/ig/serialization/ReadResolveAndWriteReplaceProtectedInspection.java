package com.siyeh.ig.serialization;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.fixes.MakeProtectedFix;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;

public class ReadResolveAndWriteReplaceProtectedInspection extends MethodInspection {
    private final MakeProtectedFix fix = new MakeProtectedFix();

    public String getDisplayName() {
        return "'readResolve()' or 'writeReplace()' not declared 'protected'";
    }

    public String getGroupDisplayName() {
        return GroupNames.SERIALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref() not declared 'protected' #loc";

    }

    public BaseInspectionVisitor buildVisitor() {
        return new ReadResolveWriteReplaceProtectedVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class ReadResolveWriteReplaceProtectedVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            // no call to super, so it doesn't drill down
            final PsiClass aClass = method.getContainingClass();
            if(aClass == null)
            {
                return;
            }
            if (aClass.isInterface() || aClass.isAnnotationType()) {
                return;
            }
            if (method.hasModifierProperty(PsiModifier.PROTECTED)) {
                return;
            }
            if(!SerializationUtils.isReadResolve(method) &&
                       !SerializationUtils.isWriteReplace(method)){
                return;
            }
            if(!SerializationUtils.isSerializable(aClass)){
                return;
            }
            registerMethodError(method);
        }
    }

}
