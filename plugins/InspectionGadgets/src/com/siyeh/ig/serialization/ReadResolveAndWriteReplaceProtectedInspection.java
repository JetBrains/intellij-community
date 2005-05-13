package com.siyeh.ig.serialization;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.siyeh.ig.*;
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

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ReadResolveWriteReplaceProtectedVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class ReadResolveWriteReplaceProtectedVisitor extends BaseInspectionVisitor {
        private ReadResolveWriteReplaceProtectedVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(@NotNull PsiMethod method) {
            // no call to super, so it doesn't drill down
            final PsiClass aClass = method.getContainingClass();
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
